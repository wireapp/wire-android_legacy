/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.call

import com.sun.jna.Pointer
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.log.InternalLog
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.call.Avs.WCall
import com.waz.service.call.Calling._
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.jna.{Size_t, Uint32_t}
import com.waz.utils.returning
import org.threeten.bp.Instant

import scala.concurrent.{Future, Promise}

trait Avs {
  import Avs._
  def registerAccount(callingService: CallingServiceImpl): Future[WCall]
  def unregisterAccount(wCall: WCall): Future[Unit]
  def onNetworkChanged(wCall: WCall): Future[Unit]
  def startCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, convType: WCallConvType.Value, cbrEnabled: Boolean): Future[Int]
  def answerCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, cbrEnabled: Boolean): Unit
  def onHttpResponse(wCall: WCall, status: Int, reason: String, arg: Pointer): Future[Unit]
  def onConfigRequest(wCall: WCall, error: Int, json: String): Future[Unit]
  def onReceiveMessage(wCall: WCall, msg: String, currTime: LocalInstant, msgTime: RemoteInstant, convId: RConvId, userId: UserId, clientId: ClientId): Unit
  def endCall(wCall: WCall, convId: RConvId): Unit
  def rejectCall(wCall: WCall, convId: RConvId): Unit
  def setVideoSendState(wCall: WCall, convId: RConvId, state: VideoState.Value): Unit
}

/**
  * Facilitates synchronous communication with AVS and also provides a wrapper around the native code which can be easily
  * mocked for testing the CallingService
  */
class AvsImpl() extends Avs {

  private implicit val dispatcher = new SerialDispatchQueue(name = "AvsWrapper")

  import Avs._

  private val available = Calling.avsAvailable.map { _ =>
    returning(Calling.wcall_init()) { res =>
      Calling.wcall_set_log_handler(new LogHandler {
        override def onLog(level: Int, msg: String, arg: WCall): Unit = {
          import InternalLog._
          level match {
            case LogLevelDebug => debug(msg, AvsLogTag)
            case LogLevelInfo  => info(msg, AvsLogTag)
            case LogLevelWarn  => warn(msg, AvsLogTag)
            case LogLevelError => error(msg, AvsLogTag)
            case _             => verbose(msg, AvsLogTag)
          }
        }
      }, null)
      verbose(s"AVS initialized: $res")
    }
  }.map(_ => {})

  available.onFailure {
    case e: Throwable =>
      error("Failed to initialise AVS - calling will not work", e)
  }

  override def registerAccount(cs: CallingServiceImpl) = available.flatMap { _ =>
    verbose(s"Initialising calling for: ${cs.accountId} and current client: ${cs.clientId}")

    val callingReady = Promise[Unit]()

    val wCall = Calling.wcall_create(
      cs.accountId.str,
      cs.clientId.str,
      new ReadyHandler {
        override def onReady(version: Int, arg: Pointer) = {
          callingReady.success({})
        }
      },
      new SendHandler {
        override def onSend(ctx: Pointer, convId: String, userid_self: String, clientid_self: String, userid_dest: String, clientid_dest: String, data: Pointer, len: Size_t, transient: Boolean, arg: Pointer) = {
          cs.onSend(ctx, RConvId(convId), UserId(userid_dest), ClientId(clientid_dest), data.getString(0, "UTF-8"))
          0
        }
      },
      new IncomingCallHandler {
        override def onIncomingCall(convId: String, msg_time: Uint32_t, userId: String, video_call: Boolean, should_ring: Boolean, arg: Pointer) =
          cs.onIncomingCall(RConvId(convId), UserId(userId), video_call, should_ring)
      },
      new MissedCallHandler {
        override def onMissedCall(convId: String, msg_time: Uint32_t, userId: String, video_call: Boolean, arg: Pointer): Unit =
          cs.onMissedCall(RConvId(convId), remoteInstant(msg_time), UserId(userId), video_call)
      },
      new AnsweredCallHandler {
        override def onAnsweredCall(convId: String, arg: Pointer) = cs.onOtherSideAnsweredCall(RConvId(convId))
      },
      new EstablishedCallHandler {
        override def onEstablishedCall(convId: String, userId: String, arg: Pointer) =
          cs.onEstablishedCall(RConvId(convId), UserId(userId))
      },
      new CloseCallHandler {
        override def onClosedCall(reasonCode: Int, convId: String, msg_time: Uint32_t, userId: String, arg: Pointer) =
          cs.onClosedCall(reasonCode, RConvId(convId), remoteInstant(msg_time), UserId(userId))
      },
      new MetricsHandler {
        override def onMetricsReady(convId: String, metricsJson: String, arg: Pointer) =
          cs.onMetricsReady(RConvId(convId), metricsJson)
      },
      new CallConfigRequestHandler {
        override def onConfigRequest(inst: WCall, arg: WCall): Int =
          cs.onConfigRequest(inst)
      },
      new CbrStateChangeHandler {
        override def onBitRateStateChanged(userId: String, enabled: Boolean, arg: WCall): Unit =
          cs.onBitRateStateChanged(enabled)
      },
      new VideoReceiveStateHandler {
        override def onVideoReceiveStateChanged(userId: String, state: Int, arg: WCall): Unit =
          cs.onVideoStateChanged(userId, VideoState(state))
      },
      null
    )

    callingReady.future.map { _ =>
      //TODO it would be nice to convince AVS to move this last method into the method wcall_init.
      Calling.wcall_set_group_changed_handler(wCall, new GroupChangedHandler {
        override def onGroupChanged(convId: String, arg: Pointer) = {
          //TODO change this set to an ordered set to for special audio effects?
          val mStruct = wcall_get_members(wCall, convId)
          val members = if (mStruct.membc.intValue() > 0) mStruct.toArray(mStruct.membc.intValue()).map(u => UserId(u.userid)).toSet else Set.empty[UserId]
          wcall_free_members(mStruct.getPointer)
          cs.onGroupChanged(RConvId(convId), members)
        }
      })
      wCall
    }
  }

  private def withAvsReturning[A](onSuccess: => A, onFailure: => A): Future[A] = available.map(_ => onSuccess).recover {
    case err =>
      error("Tried to perform action on avs after it failed to initialise", err)
      onFailure
  }


  private def withAvs(f: => Unit): Future[Unit] =
    withAvsReturning(f, {})

  override def unregisterAccount(wcall: WCall) =
    withAvs(Calling.wcall_destroy(wcall))

  override def onNetworkChanged(wCall: WCall) =
    withAvs(Calling.wcall_network_changed(wCall))

  override def startCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, convType: WCallConvType.Value, cbrEnabled: Boolean) =
    withAvsReturning(wcall_start(wCall, convId.str, callType.id, convType.id, cbrEnabled), -1)

  override def answerCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, cbrEnabled: Boolean) =
    withAvs(wcall_answer(wCall: WCall, convId.str, callType.id, cbrEnabled))

  override def onHttpResponse(wCall: WCall, status: Int, reason: String, arg: Pointer) =
    withAvs(wcall_resp(wCall, status, reason, arg))

  override def onReceiveMessage(wCall: WCall, msg: String, currTime: LocalInstant, msgTime: RemoteInstant, convId: RConvId, from: UserId, sender: ClientId) = {
    val bytes = msg.getBytes("UTF-8")
    withAvs(wcall_recv_msg(wCall, bytes, bytes.length, uint32_tTime(currTime.instant), uint32_tTime(msgTime.instant), convId.str, from.str, sender.str))
  }

  override def onConfigRequest(wCall: WCall, error: Int, json: String): Future[Unit] =
    withAvs(wcall_config_update(wCall, error, json))

  override def endCall(wCall: WCall, convId: RConvId) =
    withAvs(wcall_end(wCall, convId.str))

  override def rejectCall(wCall: WCall, convId: RConvId) =
    withAvs(wcall_reject(wCall, convId.str))

  override def setVideoSendState(wCall: WCall, convId: RConvId, state: VideoState.Value) =
    withAvs(wcall_set_video_send_state(wCall, convId.str, state.id))

}

object Avs {

  val AvsLogTag: LogTag = "AVS"

  type WCall = Pointer

  def remoteInstant(uint32_t: Uint32_t) = RemoteInstant.ofEpochMilli(uint32_t.value.toLong * 1000)

  def uint32_tTime(instant: Instant) =
    returning(Uint32_t((instant.toEpochMilli / 1000).toInt))(t => verbose(s"uint32_tTime for $instant = ${t.value}"))

  /**
    * NOTE: All values should be kept up to date as defined in:
    * https://github.com/wearezeta/avs/blob/master/include/avs_wcall.h
    *
    * Also, these are the raw values from AVS - do not mix them up with the closed reason in the CallInfo object, which
    * represents slightly different behaviour for UI and tracking
    */
  type AvsClosedReason = Int
  object AvsClosedReason {
    val Normal             = 0
    val Error              = 1
    val Timeout            = 2
    val LostMedia          = 3
    val Canceled           = 4
    val AnsweredElsewhere  = 5
    val IOError            = 6
    val StillOngoing       = 7
    val TimeoutEconn       = 8
    val DataChannel        = 9
    val Rejected           = 10

    def reasonString(r: AvsClosedReason): String = r match {
      case Normal            => "normal"
      case Error             => "internal_error"
      case Timeout           => "timeout"
      case LostMedia         => "lost_media"
      case Canceled          => "cancelled"
      case AnsweredElsewhere => "answered_elsewhere"
      case IOError           => "io_error"
      case StillOngoing      => "still_ongoing"
      case TimeoutEconn      => "timeout_econn"
      case DataChannel       => "data_channel"
      case Rejected          => "rejected"
    }
  }

  /**
    * WCALL_CALL_TYPE_NORMAL          0
    * WCALL_CALL_TYPE_VIDEO           1
    * WCALL_CALL_TYPE_FORCED_AUDIO    2
    */
  object WCallType extends Enumeration {
    val Normal, Video, ForcedAudio = Value
  }

  /**
    * WCALL_CONV_TYPE_ONEONONE        0
    * WCALL_CONV_TYPE_GROUP           1
    * WCALL_CONV_TYPE_CONFERENCE      2
    */
  object WCallConvType extends Enumeration {
    val OneOnOne, Group, Conference = Value
  }

  /**
    *   WCALL_VIDEO_STATE_STOPPED           0
    *   WCALL_VIDEO_STATE_STARTED           1
    *   WCALL_VIDEO_STATE_BAD_CONN          2
    *   WCALL_VIDEO_STATE_PAUSED            3
    *   WCALL_VIDEO_STATE_SCREENSHARE       4
    *   NoCameraPermission - internal state 5
    *   Unknown - internal state            6
    */
  type VideoState = VideoState.Value
  object VideoState extends Enumeration {
    val Stopped, Started, BadConnection, Paused, ScreenShare, NoCameraPermission, Unknown = Value
  }

  /**
    * WCALL_LOG_LEVEL_DEBUG 0
    * WCALL_LOG_LEVEL_INFO  1
    * WCALL_LOG_LEVEL_WARN  2
    * WCALL_LOG_LEVEL_ERROR 3
    */
  val LogLevelDebug = 0
  val LogLevelInfo  = 1
  val LogLevelWarn  = 2
  val LogLevelError = 3
}

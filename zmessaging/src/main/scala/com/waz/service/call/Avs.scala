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
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.model.otr.{ClientId, OtrClientIdMap, QOtrClientIdMap}
import com.waz.service.call.Avs.AvsClientList.encode
import com.waz.service.call.CallInfo.{ActiveSpeaker, Participant}
import com.waz.service.call.Calling.{ActiveSpeakersHandler, Handle, _}
import com.waz.utils.jna.{Size_t, Uint32_t}
import com.waz.utils.{CirceJSONSupport, returning}
import com.waz.zms.BuildConfig
import com.wire.signals.SerialDispatchQueue
import org.threeten.bp.Instant

import scala.concurrent.{Future, Promise}

trait Avs {
  import Avs._
  def libraryVersion: Future[String]
  def registerAccount(callingService: CallingServiceImpl): Future[WCall]
  def unregisterAccount(wCall: WCall): Future[Unit]
  def onNetworkChanged(wCall: WCall): Future[Unit]
  def startCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, convType: WCallConvType.Value, cbrEnabled: Boolean): Future[Int]
  def answerCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, cbrEnabled: Boolean): Unit
  def onHttpResponse(wCall: WCall, status: Int, reason: String, arg: Pointer): Future[Unit]
  def onConfigRequest(wCall: WCall, error: Int, json: String): Future[Unit]
  def onReceiveMessage(wCall: WCall, msg: String, currTime: LocalInstant, msgTime: RemoteInstant, convId: RConvId, userId: UserId, clientId: ClientId): Future[Int]
  def endCall(wCall: WCall, convId: RConvId): Unit
  def rejectCall(wCall: WCall, convId: RConvId): Unit
  def setVideoSendState(wCall: WCall, convId: RConvId, state: VideoState.Value): Unit
  def setCallMuted(wCall: WCall, muted: Boolean): Unit
  def setProxy(host: String, port: Int): Unit
  def onClientsRequest(wCall: WCall, convId: RConvId, userClients: OtrClientIdMap): Unit
  def onQualifiedClientsRequest(wCall: WCall, convId: RConvId, domain: Domain, userClients: QOtrClientIdMap): Unit
  def onSftResponse(wCall: WCall, data: Option[Array[Byte]], ctx: Pointer): Unit
}

/**
  * Facilitates synchronous communication with AVS and also provides a wrapper around the native code which can be easily
  * mocked for testing the CallingService
  */
class AvsImpl() extends Avs with DerivedLogTag {

  private implicit val dispatcher = SerialDispatchQueue(name = "AvsWrapper")

  import Avs._

  private val available = Calling.avsAvailable.map { _ =>
    returning(Calling.wcall_init(Calling.WCALL_ENV_DEFAULT)) { res =>
      Calling.wcall_set_log_handler(new LogHandler {
        override def onLog(level: Int, msg: String, arg: Pointer): Unit = {
          val newMsg = msg.split('\n').map(_.trim.filter(_ >= ' ')).mkString
          val log = l"${showString(newMsg)}"
          level match {
            case LogLevelDebug => debug(log)(AvsLogTag)
            case LogLevelInfo  => info(log)(AvsLogTag)
            case LogLevelWarn  => warn(log)(AvsLogTag)
            case LogLevelError => error(log)(AvsLogTag)
            case _             => verbose(log)(AvsLogTag)
          }
        }
      }, null)
      verbose(l"AVS ${Calling.wcall_library_version()} initialized: $res")
    }
  }.map(_ => {})

  available.onFailure {
    case e: Throwable =>
      error(l"Failed to initialise AVS - calling will not work", e)
  }

  override def libraryVersion: Future[String] = {
    withAvsReturning(wcall_library_version(), "Unknown AVS version")
  }

  override def registerAccount(cs: CallingServiceImpl) = available.flatMap { _ =>
    verbose(l"Initialising calling for: ${cs.accountId} and current client: ${cs.clientId}")

    val userId = DomainUtils.getFederatedId(cs.accountId.str, cs.domain.str)

    val callingReady = Promise[Unit]()

    val wCall = Calling.wcall_create(
      userId,
      cs.clientId.str,
      new ReadyHandler {
        override def onReady(version: Int, arg: Pointer) = {
          callingReady.success({})
        }
      },
      new SendHandler {
        override def onSend(ctx: Pointer,
                            convId: String,
                            userIdSelf: String,
                            clientIdSelf: String,
                            targetRecipientsJson: String, // Change: was userIdDest but has been re-purposed.
                            clientIdDest: String,         // Deprecated: AVS will no longer pass a value here.
                            data: Pointer,
                            len: Size_t,
                            isTransient: Boolean,
                            arg: Pointer): Int = {

          val userId = userIdSelf.split("@").head
          val rConvId = RConvId(convId.split("@").head)

          if (!(userId == cs.accountId.str && clientIdSelf == cs.clientId.str)) {
            warn(l"Received request to send calling message from non self user and/or client")
            return AvsCallbackError.InvalidArgument
          }

          try {
            val message = data.getString(0, "UTF-8")
            val targetRecipients = Option(targetRecipientsJson).map { json =>
              AvsClientList.decode(json).fold({ throw _ }, identity)
            }

            if (BuildConfig.FEDERATION_USER_DISCOVERY)
              cs.onQSend(ctx, message, rConvId, targetRecipients)
            else cs.onSend(ctx, message, rConvId, targetRecipients)

            AvsCallbackError.None
          } catch {
            case e: Throwable =>
              error(l"Could not decode AvsClientList", e)
              AvsCallbackError.CouldNotDecodeArgument
          }
        }
      },
      new SFTRequestHandler {
        override def onSFTRequest(ctx: Pointer, url: String, data: Pointer, length: Size_t, arg: Pointer): Int = {
          cs.onSftRequest(ctx, url, data.getString(0, "UTF-8"))
          0
        }
      },
      new IncomingCallHandler {
        override def onIncomingCall(convId: String, msgTime: Uint32_t, userId: String, clientId: String, isVideoCall: Boolean, shouldRing: Boolean, convType: Int, arg: Pointer) =
          cs.onIncomingCall(RConvId(convId.split("@").head), UserId(userId.split("@").head), isVideoCall, shouldRing, isConferenceCall = convType == WCallConvType.Conference.id)
      },
      new MissedCallHandler {
        override def onMissedCall(convId: String, msgTime: Uint32_t, userId: String, isVideoCall: Boolean, arg: Pointer): Unit =
          cs.onMissedCall(RConvId(convId), remoteInstant(msgTime), UserId(userId), isVideoCall)
      },
      new AnsweredCallHandler {
        override def onAnsweredCall(convId: String, arg: Pointer) = cs.onOtherSideAnsweredCall(RConvId(convId))
      },
      new EstablishedCallHandler {
        override def onEstablishedCall(convId: String, userId: String, clientId: String, arg: Pointer) =
          cs.onEstablishedCall(RConvId(convId), UserId(userId))
      },
      new CloseCallHandler {
        override def onClosedCall(reasonCode: Int, convId: String, msgTime: Uint32_t, userId: String, clientId: String, arg: Pointer) =
          cs.onClosedCall(reasonCode, RConvId(convId), remoteInstant(msgTime), UserId(userId))
      },
      new MetricsHandler {
        override def onMetricsReady(convId: String, metricsJson: String, arg: Pointer) =
          cs.onMetricsReady(RConvId(convId), metricsJson)
      },
      new CallConfigRequestHandler {
        override def onConfigRequest(inst: WCall, arg: Pointer): Int =
          cs.onConfigRequest(inst)
      },
      new CbrStateChangeHandler {
        override def onBitRateStateChanged(userId: String, clientId: String, isEnabled: Boolean, arg: Pointer): Unit =
          cs.onBitRateStateChanged(isEnabled)
      },
      new VideoReceiveStateHandler {
        override def onVideoReceiveStateChanged(convId: String, userId: String, clientId: String, state: Int, arg: Pointer): Unit = {
          val userIdWithoutDomain = userId.split("@").head
          val userDomain = DomainUtils.getDomainFromString(userId)

          cs.onVideoStateChanged(userIdWithoutDomain, clientId, VideoState(state), userDomain)
        }
      },
      null
    )

    callingReady.future.map { _ =>
      val participantChangedHandler = new ParticipantChangedHandler {
        override def onParticipantChanged(convId: String, data: String, arg: Pointer): Unit = {
          ParticipantsChangeDecoder.decode(data).fold(()) { participantsChange =>

            val participants = participantsChange.members.map(m => {
              val userIdString = m.userid.str.split("@").head
              val userDomain = DomainUtils.getDomainFromString(m.userid.str)

              Participant(UserId(userIdString), m.clientid, m.muted == 1, domain = Domain(userDomain))
            }).toSet
            cs.onParticipantsChanged(RConvId(convId), participants)

            import AvsClientList._
            val clients = participants.map { participant =>

              val userIdString = DomainUtils.getFederatedId(participant.userId.str, participant.domain.str)

              AvsClient(userIdString, participant.clientId.str)
            }
            val json = encode(AvsClientList(clients.toSeq))
            withAvs(wcall_request_video_streams(wCall, convId, 0, json))
          }
        }
      }

      Calling.wcall_set_participant_changed_handler(wCall, participantChangedHandler, arg = null)

      val networkQualityHandler = new NetworkQualityChangedHandler {
        override def onNetworkQualityChanged(convId: String,
                                             userId: String,
                                             clientId: String,
                                             quality: Int,
                                             roundTripTimeInMilliseconds: Int,
                                             upstreamPacketLossPercentage: Int,
                                             downstreamPacketLossPercentage: Int,
                                             arg: Pointer): Unit = {
          val userIdString = userId.split("@").head
          val userDomain = DomainUtils.getDomainFromString(userId)
          val participant = Participant(UserId(userIdString), ClientId(clientId), domain = Domain(userDomain))
          cs.onNetworkQualityChanged(ConvId(convId), participant, NetworkQuality(quality))
        }
      }

      Calling.wcall_set_network_quality_handler(wCall, networkQualityHandler, intervalInSeconds = 5, arg = null)

      val clientsRequestHandler = new ClientsRequestHandler {
        override def onClientsRequest(inst: Calling.Handle, convId: String, arg: Pointer): Unit = {
          val conv  = convId.split("@").head
          val domain  = DomainUtils.getDomainFromString(convId)
          if(BuildConfig.FEDERATION_USER_DISCOVERY)
            cs.onQualifiedClientsRequest(RConvQualifiedId(RConvId(conv), domain))
          else cs.onClientsRequest(RConvId(convId))
      }}

      Calling.wcall_set_req_clients_handler(wCall, clientsRequestHandler)

      val activeSpeakersHandler = new ActiveSpeakersHandler {
        override def onActiveSpeakersChanged(inst: Handle, convId: String, data: String, arg: Pointer): Unit =
          ActiveSpeakerChangeDecoder.decode(data).foreach { activeSpeakersChange =>
            val activeSpeakers = activeSpeakersChange.audio_levels.map(m =>ActiveSpeaker(UserId(m.userid.str.split("@").head), m.clientid, m.audio_level, m.audio_level_now)).toSet
            cs.onActiveSpeakersChanged(RConvId(convId), activeSpeakers)
        }
      }

      Calling.wcall_set_active_speaker_handler(wCall, activeSpeakersHandler)

      wCall
    }
  }

  private def withAvsReturning[A](onSuccess: => A, onFailure: => A): Future[A] = available.map(_ => onSuccess).recover {
    case err =>
      error(l"Tried to perform action on avs after it failed to initialise", err)
      onFailure
  }

  private def withAvs(f: => Unit): Future[Unit] =
    withAvsReturning(f, {})

  override def unregisterAccount(wcall: WCall) =
    withAvs(Calling.wcall_destroy(wcall))

  override def onNetworkChanged(wCall: WCall) =
    withAvs(Calling.wcall_network_changed(wCall))

  override def startCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, convType: WCallConvType.Value, cbrEnabled: Boolean) =
    withAvsReturning(wcall_start(wCall, convId.str, callType.id, convType.id, if (cbrEnabled) 1 else 0), -1)

  override def answerCall(wCall: WCall, convId: RConvId, callType: WCallType.Value, cbrEnabled: Boolean) =
    withAvs(wcall_answer(wCall: WCall, convId.str, callType.id, if (cbrEnabled) 1 else 0))

  override def onHttpResponse(wCall: WCall, status: Int, reason: String, arg: Pointer) =
    withAvs(wcall_resp(wCall, status, reason, arg))

  override def onReceiveMessage(wCall: WCall, msg: String, currTime: LocalInstant, msgTime: RemoteInstant, convId: RConvId, from: UserId, sender: ClientId): Future[Int] = {
    val bytes = msg.getBytes("UTF-8")
    withAvsReturning(wcall_recv_msg(wCall, bytes, bytes.length, uint32_tTime(currTime.instant), uint32_tTime(msgTime.instant), convId.str, from.str, sender.str), onFailure = 0)
  }

  override def onConfigRequest(wCall: WCall, error: Int, json: String): Future[Unit] =
    withAvs(wcall_config_update(wCall, error, json))

  override def endCall(wCall: WCall, convId: RConvId) =
    withAvs(wcall_end(wCall, convId.str))

  override def rejectCall(wCall: WCall, convId: RConvId) =
    withAvs(wcall_reject(wCall, convId.str))

  override def setVideoSendState(wCall: WCall, convId: RConvId, state: VideoState.Value) =
    withAvs(wcall_set_video_send_state(wCall, convId.str, state.id))

  override def setCallMuted(wCall: WCall, muted: Boolean): Unit =
    withAvs(wcall_set_mute(wCall, if (muted) 1 else 0))

  override def setProxy(host: String, port: Int): Unit =
    withAvs(wcall_set_proxy(host, port))

  override def onClientsRequest(wCall: WCall, convId: RConvId, userClients: OtrClientIdMap): Unit = {
    import AvsClientList._

    val clients = userClients.entries.flatMap { case (userId, clientIds) =>
      clientIds.map { clientId =>
        AvsClient(userId.str, clientId.str)
      }
    }

    val json = encode(AvsClientList(clients.toSeq))
    withAvs(wcall_set_clients_for_conv(wCall, convId.str, json))
  }

  override def onQualifiedClientsRequest(wCall: WCall, convId: RConvId, convDomain: Domain, userClients: QOtrClientIdMap): Unit = {
    import AvsClientList._

    val conversationIdWithDomain = s"${convId.str}@${convDomain.str}"

    val clients = userClients.entries.flatMap { case (userId, clientIds) =>
      clientIds.map { clientId =>
        val userIdWithDomain = s"${userId.id.str}@${userId.domain}"
        AvsClient(userIdWithDomain, clientId.str)
      }
    }

    val json = encode(AvsClientList(clients.toSeq))
    withAvs(wcall_set_clients_for_conv(wCall, conversationIdWithDomain, json))
  }

  override def onSftResponse(wCall: WCall, data: Option[Array[Byte]], ctx: Pointer): Unit =
    withAvs {
      val errorCode = if (data.isDefined) AvsSftError.None else AvsSftError.NoResponseData
      val responseData = data.getOrElse(Array())
      wcall_sft_resp(wCall, errorCode, responseData, responseData.length, ctx)
    }
}

object Avs extends DerivedLogTag {

  val AvsLogTag: LogTag = LogTag("AVS-N")

  type WCall = Calling.Handle

  def remoteInstant(uint32_t: Uint32_t) = RemoteInstant.ofEpochMilli(uint32_t.value.toLong * 1000)

  def uint32_tTime(instant: Instant) =
    returning(Uint32_t((instant.toEpochMilli / 1000).toInt))(t => verbose(l"uint32_tTime for $instant = ${t.value}"))

  type AvsCallError = Int
  object AvsCallError {
    val None = 0
    val UnknownProtocol = 1000
  }

  type AvsSftError = Int
  object AvsSftError {
    val None = 0
    val NoResponseData = 1
  }

  type AvsCallbackError = Int
  object AvsCallbackError {
    val None = 0
    val InvalidArgument = 1
    val CouldNotDecodeArgument = 2
  }

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
    * WCALL_AUDIO_STATE_CONNECTING       0
    * WCALL_AUDIO_STATE_ESTABLISHED      1
    * WCALL_AUDIO_STATE_NETWORK_PROBLEM  2
    */
  type AudioState = AudioState.Value
  object AudioState extends Enumeration {
    val Connecting, Established, NetworkProblem = Value
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
    * WCALL_QUALITY_NORMAL          1
    * WCALL_QUALITY_MEDIUM          2
    * WCALL_QUALITY_POOR            3
    * WCALL_QUALITY_NETWORK_PROBLEM 4
    */
  type NetworkQuality = NetworkQuality.Value
  object NetworkQuality extends Enumeration(1) {
    val Normal, Medium, Poor, Problem = Value
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

  object ParticipantsChangeDecoder extends CirceJSONSupport {
    import io.circe.{Decoder, parser}

    case class AvsParticipantsChange(convid: ConvId, members: Seq[Member])

    case class Member(userid: UserId, clientid: ClientId, aestab: Int, vrecv: Int, muted: Int)

    private lazy val decoder: Decoder[AvsParticipantsChange] = Decoder.apply

    def decode(json: String): Option[AvsParticipantsChange] =
      parser.decode(json)(decoder).right.toOption
  }

  object ActiveSpeakerChangeDecoder extends CirceJSONSupport {
    import io.circe.{Decoder, parser}

    case class ActiveSpeakerChange(audio_levels: Seq[Speaker])

    case class Speaker(userid: UserId, clientid: ClientId, audio_level: Int, audio_level_now: Int)

    private lazy val decoder: Decoder[ActiveSpeakerChange] = Decoder.apply

    def decode(json: String): Option[ActiveSpeakerChange] =
      parser.decode(json)(decoder).right.toOption
  }

  case class AvsClientList(clients: Seq[AvsClient])
  case class AvsClient(userid: String, clientid: String)

  object AvsClientList extends CirceJSONSupport {

    import io.circe.{Decoder, Encoder, Error, parser}

    private lazy val encoder: Encoder[AvsClientList] = Encoder.apply
    private lazy val decoder: Decoder[AvsClientList] = Decoder.apply

    def encode(clientList: AvsClientList): String = encoder(clientList).toString
    def decode(json: String): Either[Error, AvsClientList] = parser.decode(json)(decoder)

  }

}

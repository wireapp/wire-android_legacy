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

import android.content.Context
import android.view.View
import com.waz.call._
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service._
import com.waz.service.call.FlowManagerService.VideoCaptureDevice
import com.waz.threading.SerialDispatchQueue
import com.waz.utils._
import com.waz.utils.events._

import scala.collection.breakOut
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait FlowManagerService {
  def flowManager: Option[FlowManager]
  def getVideoCaptureDevices: Future[Vector[VideoCaptureDevice]]
  def setVideoCaptureDevice(id: RConvId, deviceId: String): Future[Unit]
  def setVideoPreview(view: View): Future[Unit]
  def setVideoView(id: RConvId, partId: Option[UserId], view: View): Future[Unit]

  val cameraFailedSig: Signal[Boolean]
}

class DefaultFlowManagerService(context:      Context,
                                globalPrefs:  GlobalPreferences,
                                network:      NetworkModeService) extends FlowManagerService with DerivedLogTag {
  import FlowManagerService._

  private implicit val ev = EventContext.Global
  private implicit val dispatcher = new SerialDispatchQueue(name = "FlowManagerService")

  override val cameraFailedSig = Signal[Boolean](false)

  network.networkMode.onChanged {  _ =>
    flowManager.fold { warn(l"unable to access flow manager") } { fm => Try { fm.networkChanged() } }
  }

  lazy val flowManager: Option[FlowManager] =
    Try {
      returning(
        new FlowManager(context, null, if (globalPrefs.getFromPref(AutoAnswerCallPrefKey)) avsAudioTestFlag else 0)
      )(_.addListener(flowListener))
    }.toOption

  private val flowListener = new FlowManagerListener {
    override def cameraFailed(): Unit = {
      debug(l"cameraFailed")
      cameraFailedSig ! true
    }

    override def changeVideoState(state: Int, reason: Int): Unit = ()
    override def volumeChanged(convId: String, participantId: String, volume: Float): Unit = ()
    override def mediaEstablished(convId: String): Unit = ()
    override def handleError(convId: String, errCode: Int): Unit = ()
    override def conferenceParticipants(convId: String, participantIds: Array[String]): Unit = ()
    override def createVideoPreview(): Unit = ()
    override def releaseVideoPreview(): Unit = ()
    override def createVideoView(convId: String, partId: String): Unit = ()
    override def releaseVideoView(convId: String, partId: String): Unit = ()
    override def changeAudioState(state: Int): Unit = ()
    override def changeVideoSize(i: Int, i1: Int): Unit = ()
  }

  def getVideoCaptureDevices: Future[Vector[VideoCaptureDevice]] = scheduleOr[Array[CaptureDevice]]({ fm =>
    debug(l"getVideoCaptureDevices")
    safeguardAgainstOldAvs(fm.getVideoCaptureDevices, fallback = Array.empty)
  }, Array.empty).map(_.map(d => VideoCaptureDevice(d.devId, d.devName))(breakOut))

  def setVideoCaptureDevice(id: RConvId, deviceId: String): Future[Unit] = schedule { fm =>
    debug(l"setVideoCaptureDevice($id, ???)")
    fm.setVideoCaptureDevice(id.str, deviceId)
  }

  // This is the preview of the outgoing video stream.
  // Call this from the callback telling us to.
  def setVideoPreview(view: View): Future[Unit] = schedule { fm =>
    debug(l"setVideoPreview")
    cameraFailedSig ! false //reset this signal since we are trying to start the capture again
    fm.setVideoPreview(null, view)
  }

  // This is the incoming video call stream from the other participant.
  // partId is the participant id (for group calls, can be null for now).
  // Call this from the callback telling us to.
  def setVideoView(id: RConvId, partId: Option[UserId], view: View): Future[Unit] = schedule { fm =>
    debug(l"setVideoView($id, $partId)")
    fm.setVideoView(id.str, partId.map(_.str).orNull, view)
  }

  private def schedule(op: FlowManager => Unit)(implicit dispatcher: ExecutionContext): Future[Unit] =
    scheduleWithoutRecovery(op) .recoverWithLog()

  private def scheduleOr[T](op: FlowManager => T, fallback: => T)(implicit dispatcher: ExecutionContext): Future[T] =
    scheduleWithoutRecovery(op).recover { case _: Throwable => fallback }

  private def scheduleWithoutRecovery[T](op: FlowManager => T)(implicit dispatcher: ExecutionContext): Future[T] =
    flowManager.fold[Future[T]] { Future.failed(new IllegalStateException("unable to access flow manager")) } { fm => Future(op(fm)) (dispatcher) }

  private def safeguardAgainstOldAvs[T](op: => T, fallback: => T): T = try op catch {
    case e: Error =>
      warn(l"too old avs version")
      fallback
  }
}

object FlowManagerService {
  case class VideoCaptureDevice(id: String, name: String)

  private[call] val avsAudioTestFlag: Long = 1 << 1
}

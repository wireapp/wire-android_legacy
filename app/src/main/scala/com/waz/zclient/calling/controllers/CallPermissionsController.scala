/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.calling.controllers

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{info, warn}
import com.waz.api.VoiceChannelState
import com.waz.model.ConvId
import com.waz.model.ConversationData.ConversationType
import com.waz.service.call.{CallingService, VoiceChannelService}
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.common.controllers.{CameraPermission, PermissionsController, RecordAudioPermission}

import scala.concurrent.Future

/**
  * This class is intended to be a relatively small controller that every PermissionsActivity can have access to in order
  * to start and accept calls. This controller requires a PermissionsActivity so that it can request and display the
  * related permissions dialogs, that's why it can't be in the GlobalCallController
  */
class CallPermissionsController(implicit inj: Injector, cxt: WireContext) extends Injectable {

  import Threading.Implicits.Background
  private implicit val eventContext = cxt.eventContext

  val globController = inject[GlobalCallingController]
  import globController._

  val permissionsController = inject[PermissionsController]

  private def useV3(convId: ConvId) = {
    prefs.head.flatMap(p => p.uiPreferenceStringSignal(p.callingV3Key, "1").apply()).flatMap {
      case "0" => Future.successful(false) // v2
      case "1" => v3Service.flatMap(_.requestedCallVersion).head.map { v =>
        info(s"Relying on backend switch: using calling version: $v")
        v == 3
      }
      case "2" => Future.successful(true) // v3
      case _ =>
        warn("Unexpected calling v3 preference, defaulting to v2")
        Future.successful(false)
    }
  }

  private def isGroupCall(convId: ConvId) =
    for {
      z <- zms.head
      Some(conv) <- z.convsContent.convById(convId)
    } yield conv.convType == ConversationType.Group

  (for {
    incomingCall <- callState.map {
      case VoiceChannelState.OTHER_CALLING => true
      case _ => false
    }
    autoAnswer <- prefs.flatMap(p => p.uiPreferenceBooleanSignal(p.autoAnswerCallPrefKey).signal)
  } yield (incomingCall, autoAnswer)) {
    case (true, true) => acceptCall()
    case _ => //
  }

  def startCall(convId: ConvId, withVideo: Boolean, variableBitRate: Boolean): Unit = {
    permissionsController.requiring(if (withVideo) Set(CameraPermission, RecordAudioPermission) else Set(RecordAudioPermission)) {
      setVariableBitRateMode(variableBitRate)
      useV3(convId).flatMap {
        case true => isGroupCall(convId).flatMap {
          case true => v3Service.head.map(_.startCall(convId, withVideo, true))
          case false => v3Service.head.map(_.startCall(convId, withVideo, false))
        }
        case false => v2Service.head.map(_.joinVoiceChannel(convId, withVideo))
      }
    }(R.string.calling__cannot_start__title,
      if (withVideo) R.string.calling__cannot_start__no_video_permission__message else R.string.calling__cannot_start__no_permission__message)
  }

  def setVariableBitRateMode(enabled: Boolean): Unit = {
    val cbrOn = if(enabled) 0 else 1 // in SE it's reversed: we DISABLE cbr instead of enabling vbr and vice versa
    v3Service.head.map(_.setAudioConstantBitRateEnabled(cbrOn))
  }

  def acceptCall(): Unit = {
    for {
      withVideo <- videoCall.head
      v3        <- isV3Call.head
    } {
      def withV3(f: (CallingService, ConvId) => Unit) = v3ServiceAndCurrentConvId.head.map { case (cs, id) => f(cs, id) }
      def withV2(f: (VoiceChannelService, ConvId) => Unit) = v2ServiceAndCurrentConvId.head.map { case (vcs, id) => f(vcs, id) }

      def accept() = v3 match {
        case true => withV3 { (cs, id) => isGroupCall(id).map {
          case true => cs.acceptCall(id, true)
          case false => cs.acceptCall(id, false)
          }
        }
        case _ => withV2 { (vcs, id) => vcs.joinVoiceChannel(id, withVideo) }
      }

      def reject() = v3 match {
        case true => withV3 { (cs, id) => cs.endCall(id) }
        case _ => withV2 { (vcs, id) => vcs.silenceVoiceChannel(id) }
      }

      permissionsController.requiring(if (withVideo) Set(CameraPermission, RecordAudioPermission) else Set(RecordAudioPermission)) {
        accept()
      }(R.string.calling__cannot_start__title, if (withVideo) R.string.calling__cannot_start__no_video_permission__message else R.string.calling__cannot_start__no_permission__message,
        reject())
    }
  }
}

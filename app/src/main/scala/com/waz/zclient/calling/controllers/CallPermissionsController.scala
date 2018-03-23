/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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

import android.Manifest.permission._
import android.content.{DialogInterface, Intent}
import android.net.Uri
import android.provider.Settings
import com.waz.content.GlobalPreferences.AutoAnswerCallPrefKey
import com.waz.model.ConvId
import com.waz.service.call.CallInfo.CallState.OtherCalling
import com.waz.permissions.PermissionsService
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.utils.ViewUtils
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.events.EventContext

/**
  * This class is intended to be a relatively small controller that every PermissionsActivity can have access to in order
  * to start and accept calls. This controller requires a PermissionsActivity so that it can request and display the
  * related permissions dialogs, that's why it can't be in the GlobalCallController
  */
class CallPermissionsController(implicit inj: Injector, cxt: WireContext, ec: EventContext) extends Injectable {

  import Threading.Implicits.Background

  val globController = inject[GlobalCallingController]
  import globController._

  val permissions = inject[PermissionsService]

  for {
    cId <- convId
    incomingCall <- callState.map {
      case OtherCalling => true
      case _ => false
    }
    autoAnswer <- prefs.flatMap(_.preference(AutoAnswerCallPrefKey).signal)
  } if (incomingCall && autoAnswer) startCall(cId)

  /**
    * Determines if a call is available and either joins that call, or starts a new one. If there is
    * already an ongoing call, the withVideo and variableBitRate flags will be ignored by the
    * underlying service
    */
  def startCall(convId: ConvId, withVideo: Boolean = false): Unit = {
    currentCallOpt.head.map { incomingCall =>
      permissions.requestAllPermissions(if (incomingCall.map(_.isVideoCall).getOrElse(withVideo)) Set(CAMERA, RECORD_AUDIO) else Set(RECORD_AUDIO)).map {
        case true => callingService.head.map(_.startCall(convId, withVideo))
        case false =>
          ViewUtils.showAlertDialog(
            cxt,
            R.string.calling__cannot_start__title,
            if (withVideo) R.string.calling__cannot_start__no_video_permission__message else R.string.calling__cannot_start__no_permission__message,
            R.string.permissions_denied_dialog_acknowledge,
            R.string.permissions_denied_dialog_settings,
            new DialogInterface.OnClickListener() {
              override def onClick(dialog: DialogInterface, which: Int): Unit =
                if (incomingCall.isDefined) callingService.head.map(_.endCall(convId))
            },
            new DialogInterface.OnClickListener() {
              override def onClick(dialog: DialogInterface, which: Int): Unit = {
                returning(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", cxt.getPackageName, null))) { i =>
                  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  cxt.startActivity(i)
                }
                if (incomingCall.isDefined) callingService.head.map(_.endCall(convId))
              }
            })
      } (Threading.Ui)
    }
  }
}

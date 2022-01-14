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
import com.waz.api.NetworkMode
import com.waz.content.GlobalPreferences.AutoAnswerCallPrefKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.permissions.PermissionsService
import com.waz.service.{AccountsService, NetworkModeService, ZMessaging}
import com.waz.service.call.CallInfo.CallState
import com.waz.threading.Threading
import com.wire.signals.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils.{getString, showConfirmationDialog, showErrorDialog, showPermissionsErrorDialog}
import com.waz.zclient.utils.PhoneUtils
import com.waz.zclient.utils.PhoneUtils.PhoneState

import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * This class needs to be activity scoped so that it can show dialogs and handle user actions before finally starting a
  * call on the appropriate account and conversation. Once a call is started, the CallingController takes over
  */
class CallStartController(implicit inj: Injector, cxt: WireContext, ec: EventContext) extends Injectable with DerivedLogTag {

  import Threading.Implicits.Ui

  private val callController = inject[CallController]
  import callController._

  for {
    Some(call) <- currentCallOpt
    autoAnswer <- prefs.flatMap(_.preference(AutoAnswerCallPrefKey).signal)
  } if (call.state == CallState.OtherCalling && autoAnswer) startCall(call.selfParticipant.qualifiedId.id, call.convId)

  def startCallInCurrentConv(withVideo: Boolean, forceOption: Boolean = false): Future[Unit] = {
    (for {
      Some(zms)  <- inject[Signal[Option[ZMessaging]]].head
      Some(conv) <- inject[Signal[Option[ConvId]]].head
      _          <- startCall(zms.selfUserId, conv, withVideo, forceOption)
    } yield {})
      .recover {
        case NonFatal(e) => warn(l"Failed to start call", e)
      }
  }

  def startCall(account: UserId, conv: ConvId, withVideo: Boolean = false, forceOption: Boolean = false): Future[Unit] = {
    verbose(l"startCall: account: $account, conv: $conv")
    if (PhoneUtils.getPhoneState(cxt) != PhoneState.IDLE)
      showErrorDialog(R.string.calling__cannot_start__title, R.string.calling__cannot_start__message)
    else {
      for {
        curCallZms        <- callingZmsOpt.head
        curCall           <- currentCallOpt.head
        Some(newCallZms)  <- inject[AccountsService].getZms(account)
        Some(newCallConv) <- newCallZms.convsStorage.get(conv)
        ongoingCalls      <- newCallZms.calling.joinableCalls.head
        acceptingCall     =  curCall.exists(c => c.convId == conv && c.selfParticipant.qualifiedId.id == account) //the call we're trying to start is the same as the current one
        isJoiningCall     =  ongoingCalls.contains(conv) //the call we're trying to start is ongoing in the background (note, this will also contain the incoming call)
        _                 =  verbose(l"accepting? $acceptingCall, isJoiningCall?: $isJoiningCall, curCall: $curCall")
        color             <- inject[AccentColorController].accentColor.head
        (true, canceled)  <- (curCallZms, curCall) match { //End any active call if it is not the one we're trying to join, confirm with the user before ending. Only proceed on confirmed
          case (Some(z), Some(c)) if !acceptingCall =>
            showConfirmationDialog(
              getString(R.string.calling_ongoing_call_title),
              getString(if (isJoiningCall) R.string.calling_ongoing_call_join_message else R.string.calling_ongoing_call_start_message),
              positiveRes = if (isJoiningCall) R.string.calling_ongoing_call_join_anyway else R.string.calling_ongoing_call_start_anyway,
              negativeRes = android.R.string.cancel,
              color       = color
            ).flatMap {
              case true  => z.calling.endCall(c.convId, skipTerminating = true).map(_ => (true, true))
              case false => Future.successful((false, false))
            }
          case _ => Future.successful((true, false))
        }
        curWithVideo      <- if (curCall.isDefined && !canceled && !forceOption) isVideoCall.head //ignore withVideo flag if call is incoming
                             else Future.successful(withVideo)
        _                 = verbose(l"curWithVideo: $curWithVideo")
        color             <- inject[AccentColorController].accentColor.head
        true              <-
          inject[NetworkModeService].networkMode.head.flatMap {        //check network state, proceed if okay
            case NetworkMode.OFFLINE              => showErrorDialog(R.string.alert_dialog__no_network__header, R.string.calling__call_drop__message).map(_ => false)
            case _                                => Future.successful(true)
          }
        members           <- newCallZms.conversations.convMembers(newCallConv.id).head
        true              <-
          if (members.size > 5 && !acceptingCall && !isJoiningCall) //!acceptingCall is superfluous, but here for clarity
            showConfirmationDialog(
              getString(R.string.group_calling_title),
              getString(R.string.group_calling_message, members.size.toString),
              positiveRes = R.string.group_calling_confirm,
              negativeRes = android.R.string.cancel,
              color       = color
            )
          else
            Future.successful(true)
        hasPerms          <- inject[PermissionsService].requestAllPermissions(if (curWithVideo) ListSet(CAMERA, RECORD_AUDIO) else ListSet(RECORD_AUDIO)) //check or request permissions
        _                 <-
          if (hasPerms)
            newCallZms.calling.startCall(newCallConv.id, curWithVideo, forceOption, BuildConfig.FORCE_CONSTANT_BITRATE_CALLS)
          else showPermissionsErrorDialog(
            R.string.calling__cannot_start__title,
            if (curWithVideo) R.string.calling__cannot_start__no_camera_permission__message else R.string.calling__cannot_start__no_permission__message
          ).flatMap(_ => if (curCall.isDefined) newCallZms.calling.endCall(newCallConv.id) else Future.successful({}))
      } yield {}
    }.recover {
      case NonFatal(e) => warn(l"Failed to start call", e)
    }
  }
}

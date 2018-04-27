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
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{verbose, warn}
import com.waz.api.NetworkMode
import com.waz.content.GlobalPreferences.AutoAnswerCallPrefKey
import com.waz.model.{ConvId, UserId}
import com.waz.permissions.PermissionsService
import com.waz.service.call.CallInfo.CallState
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient._
import com.waz.zclient.utils.ContextUtils.{getString, showConfirmationDialog, showErrorDialog, showPermissionsErrorDialog}
import com.waz.zclient.utils.PhoneUtils
import com.waz.zclient.utils.PhoneUtils.PhoneState

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * This class needs to be activity scoped so that it can show dialogs and handle user actions before finally starting a
  * call on the appropriate account and conversation. Once a call is started, the CallingController takes over
  */
class CallStartController(implicit inj: Injector, cxt: WireContext, ec: EventContext) extends Injectable {

  import Threading.Implicits.Ui

  val callController = inject[CallController]
  import callController._

  for {
    Some(call) <- currentCallOpt
    autoAnswer <- prefs.flatMap(_.preference(AutoAnswerCallPrefKey).signal)
  } if (call.state.contains(CallState.OtherCalling) && autoAnswer) startCall(call.account, call.convId)

  def startCallInCurrentConv(withVideo: Boolean) = {
    (for {
      Some(zms)  <- activeZmsOpt.head
      Some(conv) <- zms.convsStats.selectedConversationId.head
      _          <- startCall(zms.selfUserId, conv, withVideo)
    } yield {})
      .recover {
        case NonFatal(e) => warn("Failed to start call", e)
      }
  }

  def acceptCall(): Future[Unit] =
    currentCallOpt.head.flatMap {
      case Some(call) => startCall(call.account, call.convId)
      case None => Future.successful(warn("No active call to accept..."))
    }

  def startCall(account: UserId, conv: ConvId, withVideo: Boolean = false): Future[Unit] = {
    verbose(s"startCall: account: $account, conv: $conv")
    if (PhoneUtils.getPhoneState(cxt) != PhoneState.IDLE) showErrorDialog(R.string.calling__cannot_start__title, R.string.calling__cannot_start__message)
    else {
      for {
        curCallZms        <- callingZmsOpt.head
        curCall           <- currentCallOpt.head
        Some(newCallZms)  <- accounts.getZms(account)
        Some(newCallConv) <- newCallZms.convsStorage.get(conv)

        ongoingCalls <- newCallZms.calling.availableCalls.head

        acceptingCall = curCall.exists(c => c.convId == conv && c.account == account) //the call we're trying to start is the same as the current one
        isJoiningCall = ongoingCalls.contains(conv)  //the call we're trying to start is ongoing in the background (note, this will also contain the incoming call)
        _ = verbose(s"accepting? $acceptingCall, isJoiningCall?: $isJoiningCall, curCall: $curCall")

        //End any active call if it is not the one we're trying to join, confirm with the user before ending. Only proceed on confirmed
        true <- (curCallZms, curCall) match {
          case (Some(z), Some(c)) if !acceptingCall =>
            showConfirmationDialog(
              getString(R.string.calling_ongoing_call_title),
              getString(if (isJoiningCall) R.string.calling_ongoing_call_join_message else R.string.calling_ongoing_call_start_message),
              positiveRes = if (isJoiningCall) R.string.calling_ongoing_call_join_anyway else R.string.calling_ongoing_call_start_anyway
            ).flatMap {
              case true  => z.calling.endCall(c.convId).map(_ => true)
              case false => Future.successful(false)
            }
          case _ => Future.successful(true)
        }

        //ignore withVideo flag if call is incoming
        curWithVideo <- if (curCall.isDefined) isVideoCall.head else Future.successful(withVideo)
        _ = verbose(s"curWithVideo: $curWithVideo")

        //check network state, proceed if okay
        true <- networkMode.head.flatMap {
          case NetworkMode.OFFLINE              => showErrorDialog(R.string.alert_dialog__no_network__header, R.string.calling__call_drop__message).map(_ => false)
          case NetworkMode._2G                  => showErrorDialog(R.string.calling__slow_connection__title, R.string.calling__slow_connection__message).map(_ => false)
          case NetworkMode.EDGE if curWithVideo => showConfirmationDialog(getString(R.string.calling__slow_connection__title), getString(R.string.calling__video_call__slow_connection__message))
          case _                                => Future.successful(true)
        }

        //confirm large groups with user when placing a new call in a group (don't ask for confirmation when accepting or joining call)
        members <- conversationController.loadMembers(newCallConv.id)
        true <-
          if (members.size > 5 && !acceptingCall && !isJoiningCall) //!acceptingCall is superfluous, but here for clarity
            showConfirmationDialog(
              getString(R.string.group_calling_title),
              getString(R.string.group_calling_message, members.size.toString),
              positiveRes = R.string.group_calling_confirm)
          else Future.successful(true)

        //check or request permissions
        hasPerms <- inject[PermissionsService].requestAllPermissions(if (curWithVideo) Set(CAMERA, RECORD_AUDIO) else Set(RECORD_AUDIO))
        _ <-
        if (hasPerms) newCallZms.calling.startCall(newCallConv.id, curWithVideo)
        else showPermissionsErrorDialog(
          R.string.calling__cannot_start__title,
          if (curWithVideo) R.string.calling__cannot_start__no_video_permission__message else R.string.calling__cannot_start__no_permission__message
        ).flatMap(_ => if (curCall.isDefined) newCallZms.calling.endCall(newCallConv.id) else Future.successful({}))

      } yield {}
    }.recover {
      case NonFatal(e) => warn("Failed to start call", e)
    }
  }
}

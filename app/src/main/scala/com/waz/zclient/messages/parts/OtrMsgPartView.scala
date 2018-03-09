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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.controllers.{BrowserController, ScreenController}
import com.waz.zclient.messages.{MessageViewPart, MsgPart, SystemMessageView, UsersController}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.fragments.SingleParticipantFragment
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class OtrMsgPartView(context: Context, attrs: AttributeSet, style: Int) extends SystemMessageView(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import com.waz.api.Message.Type._

  override val tpe = MsgPart.OtrMessage

  lazy val screenController = inject[ScreenController]
  lazy val participantsController = inject[ParticipantsController]
  lazy val browserController = inject[BrowserController]

  val accentColor = inject[AccentColorController]
  val users = inject[UsersController]

  val msgType = message.map(_.msgType)

  val affectedUserName = message.map(_.userId).flatMap(users.displayNameString).map(_.toUpperCase)

  val memberNames = users.memberDisplayNames(message).map(_.toUpperCase)

  val memberIsJustSelf = users.memberIsJustSelf(message)

  val shieldIcon = msgType map {
    case OTR_ERROR | OTR_IDENTITY_CHANGED | HISTORY_LOST      => Some(R.drawable.red_alert)
    case OTR_VERIFIED                                         => Some(R.drawable.shield_full)
    case OTR_UNVERIFIED | OTR_DEVICE_ADDED | OTR_MEMBER_ADDED => Some(R.drawable.shield_half)
    case STARTED_USING_DEVICE                                 => None
    case _                                                    => None
  }

  val msgString = msgType.flatMap {
    case HISTORY_LOST         => Signal const getString(R.string.content__otr__lost_history)
    case STARTED_USING_DEVICE => Signal const getString(R.string.content__otr__start_this_device__message)
    case OTR_VERIFIED         => Signal const getString(R.string.content__otr__all_fingerprints_verified)
    case OTR_ERROR            => affectedUserName map { getString(R.string.content__otr__message_error, _) }
    case OTR_IDENTITY_CHANGED => affectedUserName map { getString(R.string.content__otr__identity_changed_error, _) }
    case OTR_UNVERIFIED       => memberIsJustSelf flatMap {
      case true => Signal const getString(R.string.content__otr__your_unverified_device__message)
      case false => memberNames map { getString(R.string.content__otr__unverified_device__message, _) }
    }
    case OTR_DEVICE_ADDED     => memberNames map { getString(R.string.content__otr__added_new_device__message, _) }
    case OTR_MEMBER_ADDED     => Signal const getString(R.string.content__otr__new_member__message)
    case _                    => Signal const ""
  }

  shieldIcon.on(Threading.Ui) {
    case None       => setIcon(null)
    case Some(icon) => setIcon(icon)
  }

  Signal(message, msgString, accentColor.accentColor, memberIsJustSelf).on(Threading.Ui) {
    case (msg, text, color, isMe) => setTextWithLink(text, color.getColor()) {
      (msg.msgType, isMe) match {
        case (OTR_UNVERIFIED | OTR_DEVICE_ADDED | OTR_MEMBER_ADDED, true)  => screenController.openOtrDevicePreferences()
        case (OTR_UNVERIFIED | OTR_DEVICE_ADDED | OTR_MEMBER_ADDED, false) => participantsController.onShowParticipants ! Some(SingleParticipantFragment.TagDevices)
        case (STARTED_USING_DEVICE, _)                  => screenController.openOtrDevicePreferences()
        case (OTR_ERROR, _)                             => browserController.openUrl(AndroidURIUtil parse getString(R.string.url_otr_decryption_error_1))
        case (OTR_IDENTITY_CHANGED, _)                  => browserController.openUrl(AndroidURIUtil parse getString(R.string.url_otr_decryption_error_2))
        case _ =>
          info(s"unhandled help link click for $msg")
      }
    }
  }

}

/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.tracking

import com.waz.api.EphemeralExpiration
import com.waz.api.Invitations.PersonalToken
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConversationData, Mime}
import com.waz.utils.returning
import com.waz.zclient.controllers.SignInController.{Email, Login, Register, SignInMethod}
import com.waz.zclient.tracking.ContributionEvent.Action
import org.json.JSONObject

sealed trait TrackingEvent {
  val name: String
  val props: Option[JSONObject]
}

case class OptEvent(enabled: Boolean) extends TrackingEvent {
  override val name = s"settings.opted_${if (enabled) "in" else "out"}_tracking"
  override val props = None
}

//TODO - handle generic invitation tokens
case class SignInEvent(method: SignInMethod, invitation: Option[PersonalToken]) extends TrackingEvent {
  override val name = method.signType match {
    case Register => "registration.succeeded"
    case Login    => "account.logged_in"
  }
  override val props = Some(returning (new JSONObject()) { o =>
    val input = if (method.inputType == Email) "email" else "phone"
    val context = method.signType match {
      case Login                          => input
      case Register if invitation.isEmpty => input
      case _                              => s"personal_invite_$input"
      //TODO - handle generic invitation tokens
//      case _                              => s"generic_invite_$input"
    }
    o.put("context", context)
  })
}

case class SignInErrorEvent(method: SignInMethod, errorCode: Int, errorLabel: String) extends TrackingEvent {
  override val name = method.signType match {
    case Register => "registration.failed"
    case Login    => "login.failed"
  }
  override val props = Some(returning (new JSONObject()) { o =>
    val context = if (method.inputType == Email) "email" else "phone"
    o.put("context", context)
    o.put("error_code", errorCode)
    o.put("error_label", errorLabel)
  })
}

case class ContributionEvent(action: Action, conversationType: ConversationType, ephExp: EphemeralExpiration, withBot: Boolean) extends TrackingEvent {
  override val name = "contributed"

  override val props = Some(returning(new JSONObject()) { o =>
    o.put("action",               action.name)
    o.put("conversation_type",    if (conversationType == ConversationType.Group) "group" else "1:1")
    o.put("with_bot",             withBot)
    o.put("is_ephemeral",         ephExp != EphemeralExpiration.NONE) //TODO is this flag necessary?
    o.put("ephemeral_expiration", ephExp.duration().toSeconds.toString)
  })
}

object ContributionEvent {
  case class Action(name: String)
  object Action {
    lazy val Text      = Action("text")
    lazy val Ping      = Action("ping")
    lazy val AudioCall = Action("audio_call")
    lazy val VideoCall = Action("video_call")
    lazy val Photo     = Action("photo")
    lazy val Audio     = Action("audio")
    lazy val Video     = Action("video")
    lazy val File      = Action("file")
    lazy val Location  = Action("location")
  }

  def apply(action: Action, conv: ConversationData, withOtto: Boolean): ContributionEvent =
    ContributionEvent(action, conv.convType, conv.ephemeral, withOtto)

  def fromMime(mime: Mime) = {
    import Action._
    mime match {
      case Mime.Image() => Photo
      case Mime.Audio() => Audio
      case Mime.Video() => Video
      case _            => File
    }
  }
}

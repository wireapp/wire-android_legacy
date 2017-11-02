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

import java.lang.Math.max

import com.waz.api.EphemeralExpiration
import com.waz.api.Invitations.PersonalToken
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConversationData, Mime}
import com.waz.service.push.{MissedPushes, ReceivedPushData}
import com.waz.utils.returning
import com.waz.zclient.controllers.SignInController._
import com.waz.zclient.tracking.ContributionEvent.Action
import org.json.JSONObject
import org.threeten.bp.Duration
import com.waz.utils._
import com.waz.zclient.tracking.AddPhotoOnRegistrationEvent.Source

sealed trait TrackingEvent {
  val name: String
  val props: Option[JSONObject]
}

case class OptEvent(enabled: Boolean) extends TrackingEvent {
  override val name = s"settings.opted_${if (enabled) "in" else "out"}_tracking"
  override val props = None
}

//TODO - handle generic invitation tokens
case class EnteredCredentialsEvent(method: SignInMethod, error: Option[(Int, String)], invitation: Option[PersonalToken]) extends TrackingEvent {
  override val name = method match {
    case SignInMethod(Register, Phone) => "registration.entered_phone"
    case SignInMethod(Register, Email) => "registration.entered_email_and_password"
    case SignInMethod(Login, _) => "account.entered_login_credentials"
  }
  override val props = Some(returning(new JSONObject()) { o =>
    val input = if (method.inputType == Email) "email" else "phone"
    val outcome = error.fold2("success", _ => "fail")

    val context = method.signType match {
      case Login => input
      case Register if invitation.isEmpty => input
      case _ => s"personal_invite_$input"
      //TODO - handle generic invitation tokens
      // case _ => s"generic_invite_$input"
    }
    o.put("context", context)
    o.put("outcome", outcome)
    error.foreach { case (code, label) =>
        o.put("error", code)
        o.put("error_message", label)
    }
  })
}

case class EnteredCodeEvent(method: SignInMethod, error: Option[(Int, String)]) extends TrackingEvent {
  override val name = method.signType match {
    case Register => "registration.verified_phone"
    case Login => "account.entered_login_code"
  }
  override val props = Some(returning(new JSONObject()) { o =>
    val outcome = error.fold2("success", _ => "fail")

    o.put("outcome", outcome)
    error.foreach { case (code, label) =>
      o.put("error", code)
      o.put("error_message", label)
    }
  })
}

case class EnteredNameOnRegistrationEvent(inputType: InputType, error: Option[(Int, String)]) extends TrackingEvent {
  override val name = "registration.entered_name"

  override val props = Some(returning(new JSONObject()) { o =>
    val outcome = error.fold2("success", _ => "fail")
    val context = inputType match {
      case Phone => "phone"
      case Email => "email"
    }

    o.put("context", context)
    o.put("outcome", outcome)
    error.foreach { case (code, label) =>
      o.put("error", code)
      o.put("error_message", label)
    }
  })
}

case class AddPhotoOnRegistrationEvent(inputType: InputType, error: Option[(Int, String)], source: Source) extends TrackingEvent {
  override val name = "registration.added_photo"

  override val props = Some(returning(new JSONObject()) { o =>
    val outcome = error.fold2("success", _ => "fail")
    val context = inputType match {
      case Phone => "phone"
      case Email => "email"
    }

    o.put("context", context)
    o.put("source", source.value)
    o.put("outcome", outcome)
    error.foreach { case (code, label) =>
      o.put("error", code)
      o.put("error_message", label)
    }
  })
}

object AddPhotoOnRegistrationEvent {
  case class Source(value: String)

  val Unsplash = Source("unsplash")
  val Gallery = Source("gallery")
}

case class ResendVerificationEvent(method: SignInMethod, isCall: Boolean, error: Option[(Int, String)]) extends TrackingEvent {
  override val name = method match {
    case SignInMethod(Login, Phone) if isCall => "account.requested_login_verification_call"
    case SignInMethod(Login, Phone) => "account.resent_login_verification"
    case SignInMethod(Register, Phone) if isCall => "registration.requested_phone_verification_call"
    case SignInMethod(Register, Phone) => "registration.resent_phone_verification"
    case SignInMethod(Register, Email) => "registration.resent_email_verification"
    case _ => ""
  }
  override val props = Some(returning(new JSONObject()) { o =>
    val outcome = error.fold2("success", _ => "fail")
    o.put("outcome", outcome)
    error.foreach { case (code, label) =>
      o.put("error", code)
      o.put("error_message", label)
    }
  })
}

case class SignUpScreenEvent(method: SignInMethod) extends TrackingEvent {
  override val name = method match {
    case SignInMethod(Register, Phone) => "registration.opened_phone_signup"
    case SignInMethod(Register, Email) => "registration.opened_email_signup"
    case SignInMethod(Login, Phone) => "registration.opened_phone_signin"
    case SignInMethod(Login, Email) => "registration.opened_email_signin"
  }

  override val props = Some(returning(new JSONObject()) { _ => })
}

case class ContributionEvent(action: Action, conversationType: ConversationType, ephExp: EphemeralExpiration, withBot: Boolean) extends TrackingEvent {
  override val name = "contributed"

  override val props = Some(returning(new JSONObject()) { o =>
    o.put("action", action.name)
    o.put("conversation_type", if (conversationType == ConversationType.Group) "group" else "one_to_one")
    o.put("with_bot", withBot)
    o.put("is_ephemeral", ephExp != EphemeralExpiration.NONE) //TODO is this flag necessary?
    o.put("ephemeral_expiration", ephExp.duration().toSeconds.toString)
  })
}

object ContributionEvent {

  case class Action(name: String)

  object Action {
    lazy val Text = Action("text")
    lazy val Ping = Action("ping")
    lazy val AudioCall = Action("audio_call")
    lazy val VideoCall = Action("video_call")
    lazy val Photo = Action("photo")
    lazy val Audio = Action("audio")
    lazy val Video = Action("video")
    lazy val File = Action("file")
    lazy val Location = Action("location")
  }

  def apply(action: Action, conv: ConversationData, withOtto: Boolean): ContributionEvent =
    ContributionEvent(action, conv.convType, conv.ephemeral, withOtto)

  def fromMime(mime: Mime) = {
    import Action._
    mime match {
      case Mime.Image() => Photo
      case Mime.Audio() => Audio
      case Mime.Video() => Video
      case _ => File
    }
  }
}

//only for exceptions that actually crash the app
case class CrashEvent(crashType: String, crashDetails: String) extends TrackingEvent {
  override val name = "crash"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("crashType", crashType)
    o.put("crashDetails", crashDetails)
  })
}

case class MissedPushEvent(p: MissedPushes) extends TrackingEvent {
  override val name = "debug.push_missed"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("time", p.time.toString)
    o.put("missed_count", p.countMissed)
    o.put("in_background", p.inBackground)
    o.put("network_mode", p.networkMode)
  })
}

case class ReceivedPushEvent(p: ReceivedPushData) extends TrackingEvent {
  override val name = "debug.push_received"

  def secondsAndMillis(d: Duration) = max(d.toMillis.toDouble / 1000, 0)

  override val props = Some(returning(new JSONObject()) { o =>
    o.put("since_sent_seconds", secondsAndMillis(p.sinceSent))
    o.put("received_at", p.receivedAt.toString)
    o.put("network_mode", p.networkMode)
    o.put("network_operator", p.networkOperator)
    o.put("is_device_idle", p.isDeviceIdle)
    p.toFetch.foreach(d => o.put("to_fetch_seconds", secondsAndMillis(d)))
  })
}

case class LoggedOutEvent(reason: String) extends TrackingEvent {
  override val name = "account.logged_out"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("reason", reason)
  })
}

object LoggedOutEvent {
  val RemovedClient = "removed_client"
  val InvalidCredentials = "invalid_credentials"
  val SelfDeleted = "self_deleted"
  val ResetPassword = "reset_password"
  val Manual = "manual"
}

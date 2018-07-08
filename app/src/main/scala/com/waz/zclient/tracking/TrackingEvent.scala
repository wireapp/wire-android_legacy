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
package com.waz.zclient.tracking

import com.waz.model.Availability
import com.waz.service.tracking.TrackingEvent
import com.waz.utils.{returning, _}
import com.waz.zclient.appentry.fragments.SignInFragment._
import com.waz.zclient.tracking.AvailabilityChanged.Method
import com.waz.zclient.tracking.TeamAcceptedTerms.Occurrence
import org.json.JSONObject

case class EnteredCredentialsEvent(method: SignInMethod, error: Option[(Int, String)]) extends TrackingEvent {
  override val name = method match {
    case SignInMethod(Register, Phone) => "registration.entered_phone"
    case SignInMethod(Register, Email) => "registration.entered_email_and_password"
    case SignInMethod(Login, _) => "account.entered_login_credentials"
  }
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("context", if (method.inputType == Email) "email" else "phone")
    o.put("outcome", error.fold2("success", _ => "fail"))
    error.foreach { case (code, label) =>
        o.put("error", code)
        o.put("error_message", label)
    }
  })
}

case class EnteredCodeEvent(method: SignInMethod, error: Option[(Int, String)]) extends TrackingEvent {
  override val name = method match {
    case SignInMethod(Register, Phone)=> "registration.verified_phone"
    case SignInMethod(Register, Email)=> "registration.verified_email"
    case SignInMethod(Login, _)=> "account.entered_login_code"
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

//TODO - this needs to be re-implemented for emails. For now, this only affects tablets (not super critical)
case class RegistrationSuccessfulEvent(inputType: InputType) extends TrackingEvent {
  override val name = "registration.succeeded"
  override val props = Some(returning (new JSONObject()) { o =>
    o.put("context", inputType match {
      case Phone => "phone"
      case Email => "email"
    })
  })
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
    case SignInMethod(Register, _) => "start.opened_personal_registration"
    case SignInMethod(Login, _) => "start.opened_login"
  }

  override val props = None
}

case class OpenedStartScreen() extends TrackingEvent {
  override val name: String = "start.opened_start_screen"
  override val props = None
}

case class OpenedTeamRegistrationFromProfile() extends TrackingEvent {
  override val name: String = "settings.opened_team_registration"
  override val props = None
}

case class OpenedTeamRegistration() extends TrackingEvent {
  override val name: String = "start.opened_team_registration"
  override val props = None
}

case class OpenedLogin() extends TrackingEvent {
  override val name: String = "start.opened_login"
  override val props = None
}

case class TeamsEnteredVerification(method: TeamsEnteredVerification.Method, error: Option[(Int, String)]) extends TrackingEvent {
  override val name: String = "team.entered_verification"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("method", method.str)
    o.put("outcome", error.fold2("success", _ => "fail"))
    error.foreach { case (code, label) =>
      o.put("error", code)
      o.put("error_message", label)
    }
  })
}

object TeamsEnteredVerification {
  case class Method(str: String)
  object CopyPaste extends Method("copy_paste")
  object Manual extends Method("manual")
}

case class TeamVerified() extends TrackingEvent {
  override val name: String = "team.verified"
  override val props = None
}

case class TeamAcceptedTerms(occurrence: Occurrence) extends TrackingEvent {
  override val name: String = "team.accepted_terms"
  override val props =  Some(returning(new JSONObject()) { o =>
    o.put("method", occurrence.str)
  })
}

object TeamAcceptedTerms {
  case class Occurrence(str: String)
  object AfterName extends Occurrence("after_name")
  object AfterPassword extends Occurrence("after_password")
}

case class TeamCreated() extends TrackingEvent {
  override val name: String = "team.created"
  override val props = None
}

case class TeamFinishedInvite(invited: Boolean, nInvites: Int) extends TrackingEvent {
  override val name: String = "team.finished_invite_step"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("invited", invited)
    o.put("invites", nInvites)
  })
}

case class OpenedManageTeam() extends TrackingEvent {
  override val name: String = "settings.opened_manage_team"
  override val props = None
}

case class AvailabilityChanged(status: Availability, method: Method) extends TrackingEvent {
  override val name: String = "settings.changed_status"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("status", status.toString.toLowerCase)
    o.put("method", method.str)
  })
}

object AvailabilityChanged {
  case class Method(str: String)
  object Settings extends Method("settings")
  object ListHeader extends Method("list_header")
}

case class TeamInviteSent() extends TrackingEvent {
  override val name: String = "team.sent_invite"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("method", "team_creation")
  })
}

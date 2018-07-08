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
package com.waz.zclient.appentry

import com.waz.api.impl.ErrorResponse
import com.waz.zclient.R

trait DialogErrorMessage {
  def headerResource: Int
  def bodyResource: Int
}

object DialogErrorMessage {

  case class EmailError(err: ErrorResponse) extends DialogErrorMessage {
    override val (headerResource, bodyResource) = (err.code, err.label) match {
      case (400, "invalid-email"      ) => (R.string.email_invalid_header,             R.string.email_invalid_message)
      case (403, "invalid-credentials") => (R.string.invalid_credentials_header,       R.string.invalid_credentials_message)
        //TODO (403 password-exists) ??
      case (404, "invalid-code"       ) => (R.string.invalid_code_header,              R.string.wrong_code_message)
      case (409, "key-exists"         ) => (R.string.email_exists_header,              R.string.email_exists_message)
      case (429, "client-error"       ) => (R.string.too_many_attempts_header,         R.string.login_later_message)
      case _ => genericError(err.code)
    }
  }

  case class PhoneError(err: ErrorResponse) extends DialogErrorMessage {
    override val (headerResource, bodyResource) = (err.code, err.label) match {
      case (400, "invalid-phone"         ) => (R.string.phone_invalid_format_header,   R.string.phone_invalid_format_message)
      case (403, "invalid-credentials"   ) => (R.string.incorrect_code_header,         R.string.wrong_code_message)
      case (403, "pending-login"         ) => (R.string.phone_pending_login_header,    R.string.phone_pending_login_message)
      case (403, "password-exists"       ) => (R.string.phone_password_exists_header,  R.string.phone_password_exists_message)
      case (403, "phone-budget-exhausted") => (R.string.phone_budget_exhausted_header, R.string.phone_budget_exhausted_message)
      //TODO handle (403, pending-activation) and (403, suspended) ??
      case (404, "invalid-code"          ) => (R.string.incorrect_code_header,         R.string.wrong_code_message)
      case (409, "key-exists"            ) => (R.string.phone_exists_header,           R.string.phone_exists_message)
      case (429, "client-error"          ) => (R.string.too_many_attempts_header,      R.string.login_later_message)
      case _ => genericError(err.code)
    }
  }

  def genericError(code: Int) = code match {
    case 598 => (R.string.internet_connectivity_error_header, R.string.internet_connectivity_error_message)
    case 600 => (R.string.generic_error_header,               R.string.internet_connectivity_error_message)
    case _   => (R.string.generic_error_header,               R.string.generic_error_message)
  }
}


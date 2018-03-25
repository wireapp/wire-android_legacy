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

import com.waz.zclient.R
import com.waz.zclient.appentry.fragments.SignInFragment._

case class EntryError(code: Int, label: String, signInMethod: SignInMethod) {
  def headerResource: Int = error._1
  def bodyResource: Int = error._2

  private def error = {
    (code, label, signInMethod.signType, signInMethod.inputType) match {
      case (400, _, _, Email) =>                        (R.string.new_reg_email_invalid_header, R.string.new_reg_email_invalid_message)
      case (403, _, _, Email) =>                        (R.string.new_reg_email_invalid_login_credentials_header, R.string.new_reg_email_invalid_login_credentials_message)
      case (409, _, Register, Email) =>                 (R.string.new_reg_email_exists_header, R.string.new_reg_email_exists_message)
      case (404, "invalid-code" , Register, Email) =>   (R.string.new_reg_phone_invalid_registration_code_header, R.string.new_reg_phone_invalid_registration_code_message)
      case (_, _, Register, Email) =>                   (R.string.new_reg_email_generic_error_header, R.string.new_reg_email_generic_error_message)

      case (400, _, _, Phone) =>                        (R.string.new_reg_phone_invalid_format_header, R.string.new_reg_phone_invalid_format_message)
      case (403, "pending-login", _, Phone) =>          (R.string.new_reg_phone_pending_login_header, R.string.new_reg_phone_pending_login_message)
      case (403, "password-exists", Login, Phone) =>    (R.string.new_reg_phone_password_exists_header, R.string.new_reg_phone_password_exists_message)
      case (403, "phone-budget-exhausted", _, Phone) => (R.string.new_reg_phone_budget_exhausted_title, R.string.new_reg_phone_budget_exhausted_message)
      case (403, _, Login, Phone) =>                    (R.string.new_reg_phone_invalid_login_code_header, R.string.new_reg_phone_invalid_login_code_message)
      case (404, _ , Register, Phone) =>                (R.string.new_reg_phone_invalid_registration_code_header, R.string.new_reg_phone_invalid_registration_code_message)
      case (409, _, Register, Phone) =>                 (R.string.new_reg_phone_exists_header, R.string.new_reg_phone_exists_message)
      case (_, _, Register, Phone) =>                   (R.string.new_reg_phone_generic_error_header, R.string.new_reg_phone_generic_error_message)

      case (429, _, _, _) =>                            (R.string.new_reg_phone_too_man_attempts_header, R.string.new_reg_phone_too_man_attempts_message)
      case (598, _, _, _) =>                            (R.string.new_reg_internet_connectivity_error_header, R.string.new_reg_internet_connectivity_error_message)
      case (600, _, _, _) =>                            (R.string.new_reg_server_connectivity_error_header, R.string.new_reg_server_connectivity_error_message)
      case _ =>                                         (R.string.profile_generic_error_header, R.string.profile_generic_error_message)
    }
  }
}

object GenericRegisterPhoneError extends EntryError(0, "", SignInMethod(Register, Phone))
object PhoneExistsError extends EntryError(409, "key-exists", SignInMethod(Register, Phone))

package com.waz.zclient.appentry

import com.waz.service.SSOService
import com.waz.zclient.InputDialog.{InputValidator, ValidatorResult}
import com.waz.zclient.common.views.InputBox.EmailValidator

case class EnterpriseLoginInputValidator(private val ssoService: SSOService,
                                         private val errorText: String)
    extends InputValidator {

  /**
    * @return Non empty option with error message if input is invalid.
    */
  override def isInputInvalid(input: String): ValidatorResult =
    if (isSsoInput(input) || isEmailInput(input)) ValidatorResult.Valid
    else ValidatorResult.Invalid(if (input.isEmpty) None else Some(errorText))

  def isSsoInput(input: String): Boolean = ssoService.isTokenValid(input.trim)

  def isEmailInput(input: String): Boolean = EmailValidator.isValid(input)
}

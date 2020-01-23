package com.waz.zclient.appentry

import com.waz.service.SSOService
import com.waz.zclient.InputDialog.{InputValidator, ValidatorResult}
import com.waz.zclient.common.views.InputBox.EmailValidator

case class EnterpriseLoginInputValidator(private val ssoService: SSOService) extends InputValidator {
  private var _inputType: Option[EnterpriseLoginInputType] = None

  def inputType = _inputType

  /**
    * @return Non empty option with error message if input is invalid.
    */
  override def isInputInvalid(input: String): ValidatorResult = {
    _inputType = if (ssoService.isTokenValid(input.trim)) {
      Some(EnterpriseLoginInputType.SsoCode)
    } else if (EmailValidator.isValid(input)) {
      Some(EnterpriseLoginInputType.Email)
    } else None

    _inputType match {
      case Some(_) => ValidatorResult.Valid
      case None => ValidatorResult.Invalid(error = Some("Error!!!"))
    }
  }
}

trait EnterpriseLoginInputType

object EnterpriseLoginInputType {
  case object SsoCode extends EnterpriseLoginInputType
  case object Email extends EnterpriseLoginInputType
}

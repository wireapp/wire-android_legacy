package com.waz.zclient.appentry

import com.waz.service.SSOService
import com.waz.zclient.InputDialog.ValidatorResult
import org.junit.{Before, Test}
import org.junit.runner.RunWith
import org.mockito.{Mock, Mockito, MockitoAnnotations}
import org.mockito.junit.MockitoJUnitRunner

//noinspection AccessorLikeMethodIsUnit
@RunWith(classOf[MockitoJUnitRunner])
class EnterpriseLoginInputValidatorTest {

  @Mock
  var ssoService: SSOService = _

  private var enterpriseLoginInputValidator: EnterpriseLoginInputValidator = _

  @Before
  def setUp(): Unit = {
    MockitoAnnotations.initMocks(this)
    enterpriseLoginInputValidator = EnterpriseLoginInputValidator(ssoService, "ErrorText")
  }

  @Test
  def isInputInvalid_validEmail_returnsValidResult(): Unit = {
    val email = "somebody@wire.com"
    Mockito.when(ssoService.isTokenValid(email)).thenReturn(false)

    val result = enterpriseLoginInputValidator.isInputInvalid(email)
    assert(result == ValidatorResult.Valid)
  }

  @Test
  def isInputInvalid_validSsoCode_returnsValidResult(): Unit = {
    val ssoCode = "someSsoCode"
    Mockito.when(ssoService.isTokenValid(ssoCode)).thenReturn(true)

    val result = enterpriseLoginInputValidator.isInputInvalid(ssoCode)
    assert(result == ValidatorResult.Valid)
  }

  @Test
  def isInputInvalid_neitherEmailnorSso_returnsInvalidResultWithError(): Unit = {
    val randomText = "sdlfk2348092///..sdfo"
    Mockito.when(ssoService.isTokenValid(randomText)).thenReturn(false)

    val result = enterpriseLoginInputValidator.isInputInvalid(randomText)

    assert(result.isInstanceOf[ValidatorResult.Invalid])
    assert(result.asInstanceOf[ValidatorResult.Invalid].error.isDefined)
  }

  @Test
  def isSsoInput_calledWithValidSsoCode_returnsTrue(): Unit = {
    val ssoCode = "someSsoCode"
    Mockito.when(ssoService.isTokenValid(ssoCode)).thenReturn(true)

    val result = enterpriseLoginInputValidator.isSsoInput(ssoCode)

    assert(result)
  }

  @Test
  def isSsoInput_calledWithInvalidSsoCode_returnsFalse(): Unit = {
    val ssoCode = "someInvalidSsoCode"
    Mockito.when(ssoService.isTokenValid(ssoCode)).thenReturn(false)

    val result = enterpriseLoginInputValidator.isSsoInput(ssoCode)

    assert(!result)
  }

  @Test
  def isEmailInput_calledWithValidEmail_returnsTrue(): Unit = {
    val email = "somebody@team-22-wire.com"

    val result = enterpriseLoginInputValidator.isEmailInput(email)

    assert(result)
  }

  @Test
  def isEmailInput_calledWithInvalidEmail_returnsFalse(): Unit = {
    val input = "this is not an email"

    val result = enterpriseLoginInputValidator.isEmailInput(input)

    assert(!result)
  }
}

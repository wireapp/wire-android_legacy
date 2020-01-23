package com.waz.zclient.appentry

import com.waz.service.SSOService
import com.waz.zclient.InputDialog.ValidatorResult
import org.junit.{Before, Test}
import org.junit.runner.RunWith
import org.mockito.{Mock, Mockito, MockitoAnnotations}
import org.mockito.junit.MockitoJUnitRunner

@RunWith(classOf[MockitoJUnitRunner])
class EnterpriseLoginInputValidatorTest {

  @Mock
  var ssoService: SSOService = _

  private var enterpriseLoginInputValidator: EnterpriseLoginInputValidator = _

  @Before
  def setUp(): Unit = {
    MockitoAnnotations.initMocks(this)
    enterpriseLoginInputValidator = EnterpriseLoginInputValidator(ssoService)
  }

  //noinspection AccessorLikeMethodIsUnit
  @Test
  def isInputInvalid_validEmail_returnsValidResult(): Unit = {
    val email = "somebody@wire.com"
    Mockito.when(ssoService.isTokenValid(email)).thenReturn(false)

    val result = enterpriseLoginInputValidator.isInputInvalid(email)
    assert(result == ValidatorResult.Valid)
  }

  //noinspection AccessorLikeMethodIsUnit
  @Test
  def isInputInvalid_validSsoCode_returnsValidResult(): Unit = {
    val ssoCode = "someSsoCode"
    Mockito.when(ssoService.isTokenValid(ssoCode)).thenReturn(true)

    val result = enterpriseLoginInputValidator.isInputInvalid(ssoCode)
    assert(result == ValidatorResult.Valid)
  }

  //noinspection AccessorLikeMethodIsUnit
  @Test
  def isInputInvalid_neitherEmailnorSso_returnsInvalidResultWithError(): Unit = {
    val randomText = "sdlfk2348092///..sdfo"
    Mockito.when(ssoService.isTokenValid(randomText)).thenReturn(false)

    val result = enterpriseLoginInputValidator.isInputInvalid(randomText)

    assert(result.isInstanceOf[ValidatorResult.Invalid])
    assert(result.asInstanceOf[ValidatorResult.Invalid].error.isDefined)
  }

  @Test
  def inputType_isInputInvalidCalledWithSsoCode_returnsSsoCodeType(): Unit = {
    val ssoCode = "someSsoCode"
    Mockito.when(ssoService.isTokenValid(ssoCode)).thenReturn(true)

    enterpriseLoginInputValidator.isInputInvalid(ssoCode)

    val inputType = enterpriseLoginInputValidator.inputType

    assert(inputType.contains(EnterpriseLoginInputType.SsoCode))
  }

  @Test
  def inputType_isInputInvalidCalledWithEmail_returnsEmailType(): Unit = {
    val email = "somebody@wire.com"
    Mockito.when(ssoService.isTokenValid(email)).thenReturn(false)

    enterpriseLoginInputValidator.isInputInvalid(email)

    val inputType = enterpriseLoginInputValidator.inputType

    assert(inputType.contains(EnterpriseLoginInputType.Email))
  }

  @Test
  def inputType_isInputInvalidCalledWithNeitherEmailNorSsoCode_returnsNone(): Unit = {
    val randomText = "sdlfk2348092///..sdfo"
    Mockito.when(ssoService.isTokenValid(randomText)).thenReturn(false)

    enterpriseLoginInputValidator.isInputInvalid(randomText)

    val inputType = enterpriseLoginInputValidator.inputType

    assert(inputType.isEmpty)
  }
}

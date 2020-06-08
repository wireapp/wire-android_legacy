package com.waz.zclient.feature.auth.registration.personal.phone.name

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidPhoneActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.PhoneInUse
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithPhoneUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedPhone
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.user.name.NameTooShort
import com.waz.zclient.shared.user.name.ValidateNameUseCase
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPhoneNameViewModelTest : UnitTest() {

    private lateinit var nameViewModel: CreatePersonalAccountPhoneNameViewModel

    @Mock
    private lateinit var validateNameUseCase: ValidateNameUseCase

    @Mock
    private lateinit var registerPersonalAccountWithPhoneUseCase: RegisterPersonalAccountWithPhoneUseCase

    @Before
    fun setup() {
        nameViewModel = CreatePersonalAccountPhoneNameViewModel(
            validateNameUseCase,
            registerPersonalAccountWithPhoneUseCase
        )
    }

    @Test
    fun `given validateName is called, when the validation fails with NameTooShort then isValidName should be false`() =
        runBlocking {
            `when`(validateNameUseCase.run(any())).thenReturn(Either.Left(NameTooShort))

            nameViewModel.validateName(TEST_NAME)

            assertFalse(nameViewModel.isValidNameLiveData.awaitValue())
        }

    @Test
    fun `given validateName is called, when the validation succeeds then isValidName should be true`() =
        runBlocking {
            `when`(validateNameUseCase.run(any())).thenReturn(Either.Right(Unit))

            nameViewModel.validateName(TEST_NAME)

            assertTrue(nameViewModel.isValidNameLiveData.awaitValue())
        }

    @Test
    fun `given register is called, when the phone is unauthorized then an error message is propagated`() =
        runBlocking {
            `when`(registerPersonalAccountWithPhoneUseCase.run(any())).thenReturn(Either.Left(UnauthorizedPhone))

            nameViewModel.register(TEST_NAME, TEST_PHONE, TEST_CODE)

            val error = nameViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_unauthorized_phone_error, error.message)
        }

    @Test
    fun `given register is called, when the activation code is invalid then an error message is propagated`() =
        runBlocking {
            `when`(registerPersonalAccountWithPhoneUseCase.run(any())).thenReturn(Either.Left(InvalidPhoneActivationCode))

            nameViewModel.register(TEST_NAME, TEST_PHONE, TEST_CODE)

            val error = nameViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_invalid_activation_code_error, error.message)
        }

    @Test
    fun `given register is called, when the phone is in use then an error message is propagated`() =
        runBlocking {
            `when`(registerPersonalAccountWithPhoneUseCase.run(any())).thenReturn(Either.Left(PhoneInUse))

            nameViewModel.register(TEST_NAME, TEST_PHONE, TEST_CODE)

            val error = nameViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_phone_in_use_error, error.message)
        }

    @Test
    fun `given register is called, when there is a network connection error then a network error message is propagated`() =
        runBlocking {

            `when`(registerPersonalAccountWithPhoneUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            nameViewModel.register(TEST_NAME, TEST_PHONE, TEST_CODE)

            assertEquals(Unit, nameViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given register is called, when there is no error then the registration is done`() =
        runBlocking {
            `when`(registerPersonalAccountWithPhoneUseCase.run(any())).thenReturn(Either.Right(Unit))

            nameViewModel.register(TEST_NAME, TEST_PHONE, TEST_CODE)

            assertEquals(Unit, nameViewModel.registerSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_PHONE = "+499999999"
        private const val TEST_CODE = "000000"
    }
}

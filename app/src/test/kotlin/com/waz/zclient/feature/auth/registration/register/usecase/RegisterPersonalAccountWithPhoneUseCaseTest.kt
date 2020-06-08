package com.waz.zclient.feature.auth.registration.register.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class RegisterPersonalAccountWithPhoneUseCaseTest : UnitTest() {

    private lateinit var registerPersonalAccountWithPhoneUseCase: RegisterPersonalAccountWithPhoneUseCase

    @Mock
    private lateinit var registerRepository: RegisterRepository

    @Mock
    private lateinit var phoneRegistrationParams: PhoneRegistrationParams

    @Before
    fun setup() {
        registerPersonalAccountWithPhoneUseCase = RegisterPersonalAccountWithPhoneUseCase(registerRepository)

        `when`(phoneRegistrationParams.name).thenReturn(TEST_NAME)
        `when`(phoneRegistrationParams.phone).thenReturn(TEST_PHONE)
        `when`(phoneRegistrationParams.activationCode).thenReturn(TEST_ACTIVATION_CODE)
    }

    @Test
    fun `Given register personal account with phone use case is executed, when there is a Forbidden error then return UnauthorizedPhone`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(Forbidden))

            val response = registerPersonalAccountWithPhoneUseCase.run(phoneRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(UnauthorizedPhone, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `Given register personal account with phone use case is executed, when there is a NotFound error then return InvalidPhoneActivationCode`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(NotFound))

            val response = registerPersonalAccountWithPhoneUseCase.run(phoneRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(InvalidPhoneActivationCode, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `Given register personal account with phone use case is executed, when there is a Conflict error then return PhoneInUse`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(Conflict))

            val response = registerPersonalAccountWithPhoneUseCase.run(phoneRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(PhoneInUse, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `given register personal account with phone  use case is executed, there is any other type of error then return this error`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(InternalServerError))

            val response = registerPersonalAccountWithPhoneUseCase.run(phoneRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(InternalServerError, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `given activate phone use case is executed, when there is no error then returns success`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Right(Unit))

            val response = registerPersonalAccountWithPhoneUseCase.run(phoneRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithPhone(
                TEST_NAME,
                TEST_PHONE,
                TEST_ACTIVATION_CODE
            )

            response.onSuccess { assertEquals(Unit, it) }
            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_PHONE = "+499999999"
        private const val TEST_ACTIVATION_CODE = "000000"
    }

}
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
class RegisterPersonalAccountWithEmailUseCaseTest : UnitTest() {

    private lateinit var registerPersonalAccountWithEmailUseCase: RegisterPersonalAccountWithEmailUseCase

    @Mock
    private lateinit var registerRepository: RegisterRepository

    @Mock
    private lateinit var emailRegistrationParams: EmailRegistrationParams

    @Before
    fun setup() {
        registerPersonalAccountWithEmailUseCase = RegisterPersonalAccountWithEmailUseCase(registerRepository)

        `when`(emailRegistrationParams.name).thenReturn(TEST_NAME)
        `when`(emailRegistrationParams.email).thenReturn(TEST_EMAIL)
        `when`(emailRegistrationParams.password).thenReturn(TEST_PASSWORD)
        `when`(emailRegistrationParams.activationCode).thenReturn(TEST_ACTIVATION_CODE)
    }

    @Test
    fun `Given register personal account with email use case is executed, when there is a Forbidden error then return UnauthorizedEmail`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(Forbidden))

            val response = registerPersonalAccountWithEmailUseCase.run(emailRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(UnauthorizedEmail, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `Given register personal account with email use case is executed, when there is a NotFound error then return InvalidEmailActivationCode`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(NotFound))

            val response = registerPersonalAccountWithEmailUseCase.run(emailRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(InvalidEmailActivationCode, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `Given register personal account with email use case is executed, when there is a Conflict error then return EmailInUse`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(Conflict))

            val response = registerPersonalAccountWithEmailUseCase.run(emailRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(EmailInUse, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `given register personal account with email  use case is executed, there is any other type of error then return this error`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Left(InternalServerError))

            val response = registerPersonalAccountWithEmailUseCase.run(emailRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )

            response.onFailure { assertEquals(InternalServerError, it) }
            assertTrue(response.isLeft)
        }

    @Test
    fun `given activate email use case is executed, when there is no error then returns success`() =
        runBlocking {

            `when`(registerRepository.registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )).thenReturn(Either.Right(Unit))

            val response = registerPersonalAccountWithEmailUseCase.run(emailRegistrationParams)

            verify(registerRepository).registerPersonalAccountWithEmail(
                TEST_NAME,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_ACTIVATION_CODE
            )

            response.onSuccess { assertEquals(Unit, it) }
            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_ACTIVATION_CODE = "000000"
    }

}
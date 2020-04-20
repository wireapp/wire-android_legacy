package com.waz.zclient.feature.auth.registration.register.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class RegisterPersonalAccountWithEmailUseCaseTest : UnitTest() {

    private lateinit var registerPersonalAccountWithEmailUseCase: RegisterPersonalAccountWithEmailUseCase

    @Mock
    private lateinit var activationRepository: RegisterRepository

    @Mock
    private lateinit var registrationParams: RegistrationParams

    @Before
    fun setup() {
        registerPersonalAccountWithEmailUseCase = RegisterPersonalAccountWithEmailUseCase(activationRepository)

        `when`(registrationParams.name).thenReturn(TEST_NAME)
        `when`(registrationParams.email).thenReturn(TEST_EMAIL)
        `when`(registrationParams.password).thenReturn(TEST_PASSWORD)
        `when`(registrationParams.activationCode).thenReturn(TEST_ACTIVATION_CODE)
    }

    @Test
    fun `Given register personal account with email use case is executed, when there is a Forbidden error then return UnauthorizedEmail`() = runBlockingTest {

        `when`(activationRepository.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )).thenReturn(Either.Left(Forbidden))

        val response = registerPersonalAccountWithEmailUseCase.run(registrationParams)

        verify(activationRepository).registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        response.isLeft shouldBe true

        response.fold({
            it shouldBe UnauthorizedEmail
        }) {}
    }

    @Test
    fun `Given register personal account with email use case is executed, when there is a NotFound error then return InvalidActivationCode`() = runBlockingTest {

        `when`(activationRepository.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )).thenReturn(Either.Left(NotFound))

        val response = registerPersonalAccountWithEmailUseCase.run(registrationParams)

        verify(activationRepository).registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        response.isLeft shouldBe true

        response.fold({
            it shouldBe InvalidActivationCode
        }) { }
    }

    @Test
    fun `Given register personal account with email use case is executed, when there is a Conflict error then return EmailInUse`() = runBlockingTest {

        `when`(activationRepository.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )).thenReturn(Either.Left(Conflict))

        val response = registerPersonalAccountWithEmailUseCase.run(registrationParams)

        verify(activationRepository).registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        response.isLeft shouldBe true

        response.fold({
            it shouldBe EmailInUse
        }) { }
    }

    @Test
    fun `given register personal account with email  use case is executed, there is any other type of error then return this error`() = runBlockingTest {

        `when`(activationRepository.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )).thenReturn(Either.Left(InternalServerError))

        val response = registerPersonalAccountWithEmailUseCase.run(registrationParams)

        verify(activationRepository).registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        response.isLeft shouldBe true
        response.fold({
            it shouldBe InternalServerError
        }) {}
    }

    @Test
    fun `given activate email use case is executed, when there is no error then returns success`() = runBlockingTest {

        `when`(activationRepository.registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )).thenReturn(Either.Right(Unit))

        val response = registerPersonalAccountWithEmailUseCase.run(registrationParams)

        verify(activationRepository).registerPersonalAccountWithEmail(
            TEST_NAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            TEST_ACTIVATION_CODE
        )

        response.isRight shouldBe true
        response.map {
            it shouldBe Unit
        }
    }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_ACTIVATION_CODE = "000000"
    }

}
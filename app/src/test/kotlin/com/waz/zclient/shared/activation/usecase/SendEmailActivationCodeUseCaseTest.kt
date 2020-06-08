package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.shared.activation.ActivationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class SendEmailActivationCodeUseCaseTest : UnitTest() {

    private lateinit var sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase

    @Mock
    private lateinit var activationRepository: ActivationRepository

    @Mock
    private lateinit var sendEmailActivationCodeParams: SendEmailActivationCodeParams

    @Before
    fun setup() {
        sendEmailActivationCodeUseCase = SendEmailActivationCodeUseCase(activationRepository)
    }

    @Test
    fun `Given send email activation code use case is executed, when there is a Forbidden error then return EmailBlackListed`() =
        coroutinesTestRule.runBlockingTest {

            `when`(activationRepository.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Left(Forbidden))

            val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

            verify(activationRepository).sendEmailActivationCode(TEST_EMAIL)

            response.onFailure { assertEquals(EmailBlacklisted, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given send email activation code use case is executed, there is a Conflict error then return EmailInUse`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activationRepository.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Left(Conflict))

            val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

            verify(activationRepository).sendEmailActivationCode(TEST_EMAIL)

            response.onFailure { assertEquals(EmailInUse, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given send email activation code use case is executed, there is any other type of error then return this error`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activationRepository.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Left(InternalServerError))

            val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

            verify(activationRepository).sendEmailActivationCode(TEST_EMAIL)

            response.onFailure { assertEquals(InternalServerError, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given send email activation code use case is executed, when there is no error then return success`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activationRepository.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Right(Unit))

            val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

            verify(activationRepository).sendEmailActivationCode(TEST_EMAIL)

            response.onSuccess { assertEquals(Unit, it) }

            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_EMAIL = "test@wire"
    }

}

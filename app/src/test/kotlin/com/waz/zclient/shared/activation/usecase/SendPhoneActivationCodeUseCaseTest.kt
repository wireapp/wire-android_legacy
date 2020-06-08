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
class SendPhoneActivationCodeUseCaseTest : UnitTest() {

    private lateinit var sendPhoneActivationCodeUseCase: SendPhoneActivationCodeUseCase

    @Mock
    private lateinit var activationRepository: ActivationRepository

    @Mock
    private lateinit var sendPhoneActivationCodeParams: SendPhoneActivationCodeParams

    @Before
    fun setup() {
        sendPhoneActivationCodeUseCase = SendPhoneActivationCodeUseCase(activationRepository)
    }

    @Test
    fun `Given send phone activation code use case is executed, when there is a Forbidden error then return PhoneBlackListed`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activationRepository.sendPhoneActivationCode(TEST_PHONE)).thenReturn(Either.Left(Forbidden))

            val response = sendPhoneActivationCodeUseCase.run(sendPhoneActivationCodeParams)

            verify(activationRepository).sendPhoneActivationCode(TEST_PHONE)

            response.onFailure { assertEquals(PhoneBlacklisted, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given send phone activation code use case is executed, there is a Conflict error then return PhoneInUse`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activationRepository.sendPhoneActivationCode(TEST_PHONE)).thenReturn(Either.Left(Conflict))

            val response = sendPhoneActivationCodeUseCase.run(sendPhoneActivationCodeParams)

            verify(activationRepository).sendPhoneActivationCode(TEST_PHONE)

            response.onFailure { assertEquals(PhoneInUse, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given send phone activation code use case is executed, there is any other type of error then return this error`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activationRepository.sendPhoneActivationCode(TEST_PHONE)).thenReturn(Either.Left(InternalServerError))

            val response = sendPhoneActivationCodeUseCase.run(sendPhoneActivationCodeParams)

            verify(activationRepository).sendPhoneActivationCode(TEST_PHONE)

            response.onFailure { assertEquals(InternalServerError, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given send phone activation code use case is executed, when there is no error then return success`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activationRepository.sendPhoneActivationCode(TEST_PHONE)).thenReturn(Either.Right(Unit))

            val response = sendPhoneActivationCodeUseCase.run(sendPhoneActivationCodeParams)

            verify(activationRepository).sendPhoneActivationCode(TEST_PHONE)

            response.onSuccess { assertEquals(Unit, it) }

            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_PHONE = "+499999999"
    }

}

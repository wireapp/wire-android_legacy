package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.eq
import com.waz.zclient.shared.activation.ActivationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
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
        runBlockingTest {
            `when`(sendEmailActivationCodeParams.email).thenReturn(TEST_EMAIL)
            `when`(activationRepository.sendEmailActivationCode(eq(TEST_EMAIL))).thenReturn(Either.Left(Forbidden))

            val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

            verify(activationRepository).sendEmailActivationCode(eq(TEST_EMAIL))

            response.isLeft shouldBe true

            response.fold({
                it shouldBe EmailBlacklisted
            }) { assert(false) }
        }

    @Test
    fun `given send email activation code use case is executed, there is a Conflict error then return EmailInUse`() =
        runBlockingTest {
            `when`(sendEmailActivationCodeParams.email).thenReturn(TEST_EMAIL)
            `when`(activationRepository.sendEmailActivationCode(eq(TEST_EMAIL))).thenReturn(Either.Left(Conflict))

            val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

            verify(activationRepository).sendEmailActivationCode(eq(TEST_EMAIL))

            response.isLeft shouldBe true
            response.fold({
                it shouldBe EmailInUse
            }) { assert(false) }
        }

    @Test
    fun `given send email activation code use case is executed, there is any other type of error then return this error`() =
        runBlockingTest {
            `when`(sendEmailActivationCodeParams.email).thenReturn(TEST_EMAIL)
            `when`(activationRepository.sendEmailActivationCode(eq(TEST_EMAIL))).thenReturn(Either.Left(InternalServerError))

            val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

            verify(activationRepository).sendEmailActivationCode(eq(TEST_EMAIL))

            response.isLeft shouldBe true
            response.fold({
                it shouldBe InternalServerError
            }) { assert(false) }
        }

    @Test
    fun `given send email activation code use case is executed, when there is no error then return ActivationCodeSent`() = runBlockingTest {
        `when`(sendEmailActivationCodeParams.email).thenReturn(TEST_EMAIL)
        `when`(activationRepository.sendEmailActivationCode(eq(TEST_EMAIL))).thenReturn(Either.Right(Unit))

        val response = sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

        verify(activationRepository).sendEmailActivationCode(eq(TEST_EMAIL))

        response.isRight shouldBe true
        response.map {
            it shouldBe Unit
        }
    }


    companion object {
        private const val TEST_EMAIL = "test@wire"
    }

}

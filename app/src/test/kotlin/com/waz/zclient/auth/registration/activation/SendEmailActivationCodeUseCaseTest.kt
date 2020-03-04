package com.waz.zclient.auth.registration.activation

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
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
    fun `Given send email activation code use case is executed, then the repository should send email activation code`() = runBlockingTest {
        `when`(sendEmailActivationCodeParams.email).thenReturn(TEST_EMAIL)

        sendEmailActivationCodeUseCase.run(sendEmailActivationCodeParams)

        verify(activationRepository).sendEmailActivationCode(eq(TEST_EMAIL))
    }

    companion object {
        private const val TEST_EMAIL = "test@wire"
    }

}

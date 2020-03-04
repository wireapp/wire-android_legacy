package com.waz.zclient.auth.registration.activation

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ActivationRepositoryTest : UnitTest() {

    private lateinit var activationRepository: ActivationRepository

    @Mock
    private lateinit var activationRemoteDataSource: ActivationRemoteDataSource

    @Before
    fun setup() {
        activationRepository = ActivationDataSource(activationRemoteDataSource)
    }

    @Test
    fun `Given sendEmailActivationCode() is called and remote request fails then return failure`() = runBlockingTest {

        `when`(activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Left(ServerError))

        activationRepository.sendEmailActivationCode(TEST_EMAIL)

        verify(activationRemoteDataSource).sendEmailActivationCode(eq(TEST_EMAIL))

        activationRepository.sendEmailActivationCode(TEST_EMAIL).isLeft shouldBe true
    }

    @Test
    fun `Given sendEmailActivationCode() is called and remote request is success, then return failure`() = runBlockingTest {
        `when`(activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Right(Unit))

        activationRepository.sendEmailActivationCode(TEST_EMAIL)

        verify(activationRemoteDataSource).sendEmailActivationCode(eq(TEST_EMAIL))

        activationRepository.sendEmailActivationCode(TEST_EMAIL).isRight shouldBe true
    }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
    }

}

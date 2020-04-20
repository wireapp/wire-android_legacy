package com.waz.zclient.shared.activation.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import com.waz.zclient.shared.activation.ActivationRepository
import com.waz.zclient.shared.activation.datasources.remote.ActivationRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ActivationDataSourceTest : UnitTest() {

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

        val response = activationRepository.sendEmailActivationCode(TEST_EMAIL)

        verify(activationRemoteDataSource).sendEmailActivationCode(eq(TEST_EMAIL))

        response.isLeft shouldBe true
    }

    @Test
    fun `Given sendEmailActivationCode() is called and remote request is success, then return success`() = runBlockingTest {
        `when`(activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Right(Unit))

        val response = activationRepository.sendEmailActivationCode(TEST_EMAIL)

        verify(activationRemoteDataSource).sendEmailActivationCode(eq(TEST_EMAIL))

        response.isRight shouldBe true
    }

    @Test
    fun `Given activateEmail() is called and remote request fails then return failure`() = runBlockingTest {

        `when`(activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Left(ServerError))

        val response = activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)

        verify(activationRemoteDataSource).activateEmail(TEST_EMAIL, TEST_CODE)

        response.isLeft shouldBe true
    }

    @Test
    fun `Given activateEmail() is called and remote request is success, then return success`() = runBlockingTest {
        `when`(activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Right(Unit))

        val response = activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)

        verify(activationRemoteDataSource).activateEmail(TEST_EMAIL, TEST_CODE)

        response.isRight shouldBe true
    }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
    }

}

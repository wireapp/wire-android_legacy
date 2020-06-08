package com.waz.zclient.shared.activation.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.activation.ActivationRepository
import com.waz.zclient.shared.activation.datasources.remote.ActivationRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
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
    fun `Given sendEmailActivationCode() is called and remote request fails then return failure`() =
        runBlocking {

            `when`(activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Left(ServerError))

            val response = activationRepository.sendEmailActivationCode(TEST_EMAIL)

            verify(activationRemoteDataSource).sendEmailActivationCode(TEST_EMAIL)

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given sendEmailActivationCode() is called and remote request is success, then return success`() =
        runBlocking {
            `when`(activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)).thenReturn(Either.Right(Unit))

            val response = activationRepository.sendEmailActivationCode(TEST_EMAIL)

            verify(activationRemoteDataSource).sendEmailActivationCode(TEST_EMAIL)

            assertTrue(response.isRight)
        }

    @Test
    fun `Given sendPhoneActivationCode() is called and remote request fails then return failure`() =
        runBlocking {

            `when`(activationRemoteDataSource.sendPhoneActivationCode(TEST_PHONE)).thenReturn(Either.Left(ServerError))

            val response = activationRepository.sendPhoneActivationCode(TEST_PHONE)

            verify(activationRemoteDataSource).sendPhoneActivationCode(TEST_PHONE)

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given sendPhoneActivationCode() is called and remote request is success, then return success`() =
        runBlocking {
            `when`(activationRemoteDataSource.sendPhoneActivationCode(TEST_PHONE)).thenReturn(Either.Right(Unit))

            val response = activationRepository.sendPhoneActivationCode(TEST_PHONE)

            verify(activationRemoteDataSource).sendPhoneActivationCode(TEST_PHONE)

            assertTrue(response.isRight)
        }

    @Test
    fun `Given activateEmail() is called and remote request fails then return failure`() =
        runBlocking {

            `when`(activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Left(ServerError))

            val response = activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)

            verify(activationRemoteDataSource).activateEmail(TEST_EMAIL, TEST_CODE)

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given activateEmail() is called and remote request is success, then return success`() =
        runBlocking {
            `when`(activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Right(Unit))

            val response = activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)

            verify(activationRemoteDataSource).activateEmail(TEST_EMAIL, TEST_CODE)

            assertTrue(response.isRight)
        }

    @Test
    fun `Given activatePhone() is called and remote request fails then return failure`() =
        runBlocking {

            `when`(activationRemoteDataSource.activatePhone(TEST_PHONE, TEST_CODE)).thenReturn(Either.Left(ServerError))

            val response = activationRepository.activatePhone(TEST_PHONE, TEST_CODE)

            verify(activationRemoteDataSource).activatePhone(TEST_PHONE, TEST_CODE)

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given activatePhone() is called and remote request is success, then return success`() =
        runBlocking {
            `when`(activationRemoteDataSource.activatePhone(TEST_PHONE, TEST_CODE)).thenReturn(Either.Right(Unit))

            val response = activationRepository.activatePhone(TEST_PHONE, TEST_CODE)

            verify(activationRemoteDataSource).activatePhone(TEST_PHONE, TEST_CODE)

            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
        private const val TEST_PHONE = "+499999999"
    }

}

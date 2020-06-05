package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.shared.activation.ActivationRepository
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
class ActivatePhoneUseCaseTest : UnitTest() {

    private lateinit var activatePhoneUseCase: ActivatePhoneUseCase

    @Mock
    private lateinit var activationRepository: ActivationRepository

    @Mock
    private lateinit var activatePhoneParams: ActivatePhoneParams

    @Before
    fun setup() {
        activatePhoneUseCase = ActivatePhoneUseCase(activationRepository)
    }

    @Test
    fun `Given activate phone use case is executed, when there is a Not found error then return InvalidSmsCode`() =
        runBlocking {
            `when`(activatePhoneParams.phone).thenReturn(TEST_PHONE)
            `when`(activatePhoneParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activatePhone(TEST_PHONE, TEST_CODE)).thenReturn(Either.Left(NotFound))

            val response = activatePhoneUseCase.run(activatePhoneParams)

            verify(activationRepository).activatePhone(TEST_PHONE, TEST_CODE)

            response.onFailure { assertEquals(InvalidPhoneCode, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given activate phone use case is executed, there is any other type of error then return this error`() =
        runBlocking {
            `when`(activatePhoneParams.phone).thenReturn(TEST_PHONE)
            `when`(activatePhoneParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activatePhone(TEST_PHONE, TEST_CODE)).thenReturn(Either.Left(InternalServerError))

            val response = activatePhoneUseCase.run(activatePhoneParams)

            verify(activationRepository).activatePhone(TEST_PHONE, TEST_CODE)

            response.onFailure { assertEquals(InternalServerError, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given activate phone use case is executed, when there is no error then returns success`() =
        runBlocking {
            `when`(activatePhoneParams.phone).thenReturn(TEST_PHONE)
            `when`(activatePhoneParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activatePhone(TEST_PHONE, TEST_CODE)).thenReturn(Either.Right(Unit))

            val response = activatePhoneUseCase.run(activatePhoneParams)

            verify(activationRepository).activatePhone(TEST_PHONE, TEST_CODE)

            response.onSuccess { assertEquals(Unit, it) }

            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_PHONE = "+499999999"
        private const val TEST_CODE = "000000"
    }

}
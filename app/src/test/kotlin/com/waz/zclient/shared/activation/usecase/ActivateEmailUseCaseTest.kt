package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.shared.activation.ActivationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ActivateEmailUseCaseTest : UnitTest() {

    @get:Rule
    val testRule = CoroutinesTestRule()

    private lateinit var activateEmailUseCase: ActivateEmailUseCase

    @Mock
    private lateinit var activationRepository: ActivationRepository

    @Mock
    private lateinit var activateEmailParams: ActivateEmailParams

    @Before
    fun setup() {
        activateEmailUseCase = ActivateEmailUseCase(activationRepository)
    }

    @Test
    fun `Given activate email use case is executed, when there is a Not found error then return InvalidEmailCode`() =
        runBlocking {
            `when`(activateEmailParams.email).thenReturn(TEST_EMAIL)
            `when`(activateEmailParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Left(NotFound))

            val response = activateEmailUseCase.run(activateEmailParams)

            verify(activationRepository).activateEmail(TEST_EMAIL, TEST_CODE)

            response.onFailure { assertEquals(InvalidEmailCode, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given  activate email use case is executed, there is any other type of error then return this error`() =
        runBlocking {
            `when`(activateEmailParams.email).thenReturn(TEST_EMAIL)
            `when`(activateEmailParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Left(InternalServerError))

            val response = activateEmailUseCase.run(activateEmailParams)

            verify(activationRepository).activateEmail(TEST_EMAIL, TEST_CODE)

            response.onFailure { assertEquals(InternalServerError, it) }

            assertTrue(response.isLeft)
        }

    @Test
    fun `given activate email use case is executed, when there is no error then returns success`() =
        runBlocking {
            `when`(activateEmailParams.email).thenReturn(TEST_EMAIL)
            `when`(activateEmailParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Right(Unit))

            val response = activateEmailUseCase.run(activateEmailParams)

            verify(activationRepository).activateEmail(TEST_EMAIL, TEST_CODE)

            response.onSuccess { assertEquals(Unit, it) }

            assertTrue(response.isRight)
        }

    companion object {
        private const val TEST_EMAIL = "test@wire"
        private const val TEST_CODE = "000000"
    }

}
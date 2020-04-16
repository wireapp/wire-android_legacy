package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
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
class ActivateEmailUseCaseTest : UnitTest() {

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
    fun `Given activate email use case is executed, when there is a Not found error then return InvalidCode`() =
        runBlockingTest {
            `when`(activateEmailParams.email).thenReturn(TEST_EMAIL)
            `when`(activateEmailParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Left(NotFound))

            val response = activateEmailUseCase.run(activateEmailParams)

            verify(activationRepository).activateEmail(TEST_EMAIL, TEST_CODE)

            response.isLeft shouldBe true

            response.fold({
                it shouldBe InvalidCode
            }) { assert(false) }
        }

    @Test
    fun `given  activate email use case is executed, there is any other type of error then return this error`() =
        runBlockingTest {
            `when`(activateEmailParams.email).thenReturn(TEST_EMAIL)
            `when`(activateEmailParams.code).thenReturn(TEST_CODE)
            `when`(activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Left(InternalServerError))

            val response = activateEmailUseCase.run(activateEmailParams)

            verify(activationRepository).activateEmail(TEST_EMAIL, TEST_CODE)

            response.isLeft shouldBe true
            response.fold({
                it shouldBe InternalServerError
            }) { assert(false) }
        }

    @Test
    fun `given activate email use case is executed, when there is no error then returns success`() = runBlockingTest {
        `when`(activateEmailParams.email).thenReturn(TEST_EMAIL)
        `when`(activateEmailParams.code).thenReturn(TEST_CODE)
        `when`(activationRepository.activateEmail(TEST_EMAIL, TEST_CODE)).thenReturn(Either.Right(Unit))

        val response = activateEmailUseCase.run(activateEmailParams)

        verify(activationRepository).activateEmail(TEST_EMAIL, TEST_CODE)

        response.isRight shouldBe true
        response.map {
            it shouldBe Unit
        }
    }


    companion object {
        private const val TEST_EMAIL = "test@wire"
        private const val TEST_CODE = "000000"
    }

}
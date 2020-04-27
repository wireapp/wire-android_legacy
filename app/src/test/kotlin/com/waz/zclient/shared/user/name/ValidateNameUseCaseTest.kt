package com.waz.zclient.shared.user.name

import com.waz.zclient.UnitTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class ValidateNameUseCaseTest : UnitTest() {

    private lateinit var validationNameUseCase: ValidateNameUseCase

    @Mock
    private lateinit var validateNameParams: ValidateNameParams

    @Before
    fun setup() {
        validationNameUseCase = ValidateNameUseCase()
    }

    @Test
    fun `Given run is executed, when name length is smaller or equal to 1, then return failure`() {
        runBlockingTest {
            val name = "t"
            `when`(validateNameParams.name).thenReturn(name)
            val response = validationNameUseCase.run(validateNameParams)
            response.isLeft shouldBe true
            response.fold({ it shouldBe NameTooShort }) {}
        }
    }

    @Test
    fun `Given run is executed, when name fits requirements then return success`() {
        runBlockingTest {
            val name = "testName"
            `when`(validateNameParams.name).thenReturn(name)
            validationNameUseCase.run(validateNameParams).isRight shouldBe true
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given run is executed when request is canceled, then return false`() =

        runBlockingTest {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
            validationNameUseCase.run(validateNameParams).isLeft shouldBe true
        }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "The request has been cancelled"
        private const val CANCELLATION_DELAY = 200L
    }
}

package com.waz.zclient.shared.user.handle.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.map
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
class ValidateHandleUseCaseTest : UnitTest() {

    private lateinit var validateHandleUseCase: ValidateHandleUseCase

    @Mock
    private lateinit var validateHandleParams: ValidateHandleParams

    @Before
    fun setup() {
        validateHandleUseCase = ValidateHandleUseCase()
    }

    @Test
    fun `Given run is executed, when handle doesn't match regex, then return failure`() {
        val handle = "----7_.handle"
        verifyValidateUseCase(handle)
    }

    @Test
    fun `Given run is executed, when handle matches regex and length is over max, then return failure`() {
        val handle = "thisisalonghandlethatshouldnotbethislong"
        verifyValidateUseCase(handle)
    }

    @Test
    fun `Given run is executed, when handle matches regex and length is 1, then return failure`() {
        val handle = "h"
        verifyValidateUseCase(handle)
    }

    @Test
    fun `Given run is executed, when handle is empty then return failure`() {
        val handle = String.empty()
        verifyValidateUseCase(handle)
    }

    @Test
    fun `Given run is executed, when handle matches regex and handle fits requirements then return success`() {
        val handle = "wire"
        verifyValidateUseCase(handle, isError = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given run is executed when handle fits requirements but request is canceled, then return false`() {
        val handle = "wire"
        verifyValidateUseCase(handle, isCancelable = true)
    }

    private fun verifyValidateUseCase(handle: String, isError: Boolean = true, isCancelable: Boolean = false) = runBlockingTest {
        `when`(validateHandleParams.newHandle).thenReturn(handle)

        validateHandleUseCase.run(validateHandleParams)

        if (isCancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        if (!isError) {
            validateHandleUseCase.run(validateHandleParams).map {
                it shouldBe handle
            }
        }

        validateHandleUseCase.run(validateHandleParams).isLeft shouldBe isError
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "The request has been cancelled"
        private const val CANCELLATION_DELAY = 200L
    }
}

package com.waz.zclient.settings.account.edithandle

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.GenericUseCaseError
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.user.domain.usecase.handle.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verifyNoInteractions
import java.util.concurrent.CancellationException

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleViewModelTest : UnitTest() {

    private lateinit var editHandleViewModel: EditHandleViewModel

    @Mock
    private lateinit var checkHandleExistsUseCase: CheckHandleExistsUseCase

    @Mock
    private lateinit var changeHandleUseCase: ChangeHandleUseCase

    @Mock
    private lateinit var getHandleUseCase: GetHandleUseCase

    @Mock
    private lateinit var validateHandleUseCase: ValidateHandleUseCase

    @Before
    fun setup() {
        editHandleViewModel = EditHandleViewModel(checkHandleExistsUseCase, changeHandleUseCase, getHandleUseCase, validateHandleUseCase)
    }

    @Test
    fun `given beforeHandTextChange is called and validation fails with HandleInvalidError, then handle input should not be updated`() =
        runBlockingTest {
            editHandleViewModel.beforeHandleTextChanged(HANDLE_INVALID)

            editHandleViewModel.handle.observeOnce {
                it shouldBe String.empty()
            }
        }

    @Test
    fun `given beforeHandTextChange is called and validation do not fail with HandleInvalidError, then handle input should be updated`() =
        runBlockingTest {
            editHandleViewModel.beforeHandleTextChanged(HANDLE_TOO_LONG)

            editHandleViewModel.handle.observeOnce {
                it shouldBe HANDLE_TOO_LONG
            }

        }

    @Test(expected = CancellationException::class)
    fun `given afterHandleTextChanged is called and getHandleUseCase fails with GenericUseCaseError, then okButton should be disabled`() =
        runBlockingTest {
            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            getHandleUseCase.invoke(any(), any()) {
                it.onFailure { failure ->
                    failure shouldBeInstanceOf GenericUseCaseError::class.java
                }
            }

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput valid, checkExist succeeds, validation succeeds, handle should update `() =
        runBlockingTest {

            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe true
            }
            editHandleViewModel.handle.observeOnce {
                it shouldBe NON_DUPLICATED_HANDLE
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is invalid, check exists use case should not be called`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { GENERIC_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            verifyNoInteractions(checkHandleExistsUseCase)
        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid, check exists fails with HandleUnknwonError, then ok button is disabled and error should be updated`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Left(HandleUnknownError))

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBe HandleUnknownError
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid, check exists fails with HandleAlreadyTakenError, then ok button is disabled and error should be updated`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Left(HandleExistsAlreadyError))

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
            editHandleViewModel.error.observeOnce {
                it shouldBe HandleExistsAlreadyError
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid, check exists succeeds, validation fails with HandleTooLongError, ok button should be disabled`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(HANDLE_TOO_LONG)
            val handleFlow: Flow<String> = flow { HANDLE_TOO_LONG }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            validateHandleUseCase.invoke(TestCoroutineScope(TestCoroutineDispatcher()), ValidateHandleParams(HANDLE_TOO_LONG)) {
                it.onFailure { failure ->
                    failure shouldBeInstanceOf HandleTooLongError::class.java
                }
            }

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid,  check exists succeeds, validation fails with HandleTooShortError, ok button should be disabled`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(HANDLE_TOO_SHORT)
            val handleFlow: Flow<String> = flow { HANDLE_TOO_SHORT }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            validateHandleUseCase.invoke(TestCoroutineScope(TestCoroutineDispatcher()), ValidateHandleParams(HANDLE_TOO_SHORT)) {
                it.onFailure { failure ->
                    failure shouldBeInstanceOf HandleTooShortError::class.java
                }
            }

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid, check exists succeeds, validation fails with HandleUnknownError, ok button should be disabled`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(HANDLE_TOO_SHORT)
            val handleFlow: Flow<String> = flow { HANDLE_TOO_SHORT }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid, check exists succeeds, validation fails with HandleInvalidError, ok button should be disabled and error updated`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(HANDLE_INVALID)
            val handleFlow: Flow<String> = flow { HANDLE_INVALID }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(GENERIC_HANDLE)

            validateHandleUseCase.invoke(TestCoroutineScope(TestCoroutineDispatcher()), ValidateHandleParams(HANDLE_INVALID)) {
                it.onFailure { failure ->
                    failure shouldBeInstanceOf HandleInvalidError::class.java
                }
            }

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle succeeds, updateHandle succeeds, dialog is dismissed`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(GENERIC_HANDLE)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleTooLongError, ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(HANDLE_TOO_LONG)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleTooShortError, ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(HANDLE_TOO_SHORT)

            validateHandleUseCase.invoke(TestCoroutineScope(TestCoroutineDispatcher()), ValidateHandleParams(HANDLE_TOO_SHORT)) {
                it.onFailure { failure ->
                    failure shouldBeInstanceOf HandleTooShortError::class.java
                }
            }

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleInvalidError, ok button should be disabled and error updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(HANDLE_INVALID)

            validateHandleUseCase.invoke(TestCoroutineScope(TestCoroutineDispatcher()), ValidateHandleParams(HANDLE_INVALID)) {
                it.onFailure { failure ->
                    failure shouldBeInstanceOf HandleInvalidError::class.java
                }
            }

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleUnknownError, error message should be updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(HANDLE_INVALID)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBeInstanceOf HandleUnknownError::class.java
            }
        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle succeeds, update handle fails with HandleUnknownError, ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(DatabaseError))

            editHandleViewModel.onOkButtonClicked(GENERIC_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBeInstanceOf HandleUnknownError::class.java
            }
        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is false, suggestHandle is valid, update handle succeeds, handle should be update, dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(ChangeHandleParams(GENERIC_HANDLE))).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(GENERIC_HANDLE, false)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is false, suggestHandle is valid, update handle fails with HandleUnknownError, ok button should be disabled, dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(ChangeHandleParams(GENERIC_HANDLE))).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(GENERIC_HANDLE, false)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is true, suggestHandle is valid, dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(ChangeHandleParams(GENERIC_HANDLE))).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(GENERIC_HANDLE, true)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is false, suggestHandle is null, dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(ChangeHandleParams(GENERIC_HANDLE))).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(null, false)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is false, suggestHandle is empty, dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(ChangeHandleParams(GENERIC_HANDLE))).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(String.empty(), true)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    companion object {
        private const val HANDLE_INVALID = "---___hh9@@wire"
        private const val GENERIC_HANDLE = "wire"
        private const val NON_DUPLICATED_HANDLE = "wire1"
        private const val HANDLE_TOO_SHORT = "w"
        private const val HANDLE_TOO_LONG = "thishandleiswaytoolongforwhatwewantinthisapplication"
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again"
        private const val CANCELLATION_DELAY = 200L

    }
}

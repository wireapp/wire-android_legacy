package com.waz.zclient.settings.account.edithandle

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.user.domain.usecase.handle.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verifyNoInteractions

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
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, checkExist succeeds, validation succeeds, then handle should update`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe true
            }

            editHandleViewModel.success.observeOnce {
                it shouldBe HandleIsAvailable
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, currentInput == currentHandle, then CheckExistsUseCase should not be called`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            verifyNoInteractions(checkHandleExistsUseCase)
        }

    @Test
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, check exists fails with HandleUnknownError, then ok button disabled and error updated`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Left(HandleUnknownError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBe HandleUnknownError
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists fails with HandleAlreadyTakenError, then ok button is disabled and error should be updated`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Left(HandleExistsAlreadyError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
            editHandleViewModel.error.observeOnce {
                it shouldBe HandleExistsAlreadyError
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleTooLongError then ok button should be disabled`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooLongError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleTooShortError then ok button should be disabled`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run((any()))).thenReturn(Either.Left(HandleTooShortError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleUnknownError then ok button should be disabled`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleUnknownError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.error.observeOnce {
                it shouldBe HandleUnknownError
            }
            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleInvalidError, then ok button should be disabled and error updated`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleInvalidError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBe HandleInvalidError
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle succeeds and updateHandle succeeds then dialog is dismissed`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleTooLongError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooLongError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleTooShortError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooShortError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleInvalidError then ok button should be disabled and error updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleInvalidError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBe HandleInvalidError
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleUnknownError then error message should be updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleUnknownError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBe HandleUnknownError
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle succeeds and update handle fails with HandleUnknownError, then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(DatabaseError))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Right(TEST_HANDLE))
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(HandleUnknownError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.error.observeOnce {
                it shouldBe HandleUnknownError
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is valid and update handle succeeds, then handle should be updated and dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe true
            }

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is valid and handle fails with HandleUnknownError then ok button should be disabled and dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabled.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is null, then dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(null)

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is empty, then dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(String.empty())

            editHandleViewModel.dismiss.observeOnce {
                it shouldBe Unit
            }
        }

    companion object {
        private const val NON_DUPLICATED_TEST_HANDLE = "testHandle1"
        private const val TEST_HANDLE = "testHandle"
    }
}

package com.waz.zclient.feature.settings.account.edithandle

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.user.handle.HandleAlreadyExists
import com.waz.zclient.shared.user.handle.HandleInvalid
import com.waz.zclient.shared.user.handle.HandleIsAvailable
import com.waz.zclient.shared.user.handle.HandleSameAsCurrent
import com.waz.zclient.shared.user.handle.HandleTooLong
import com.waz.zclient.shared.user.handle.HandleTooShort
import com.waz.zclient.shared.user.handle.UnknownError
import com.waz.zclient.shared.user.handle.usecase.ChangeHandleUseCase
import com.waz.zclient.shared.user.handle.usecase.CheckHandleExistsParams
import com.waz.zclient.shared.user.handle.usecase.CheckHandleExistsUseCase
import com.waz.zclient.shared.user.handle.usecase.GetHandleUseCase
import com.waz.zclient.shared.user.handle.usecase.ValidateHandleUseCase
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
class SettingsAccountEditHandleViewModelTest : UnitTest() {

    private lateinit var editHandleViewModel: SettingsAccountEditHandleViewModel

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
        editHandleViewModel = SettingsAccountEditHandleViewModel(checkHandleExistsUseCase, changeHandleUseCase, getHandleUseCase, validateHandleUseCase)
    }

    @Test
    fun `given afterHandleTextChanged is called, when input contains capital letters then update handle with lowercase values`() {
        editHandleViewModel.afterHandleTextChanged(TEST_HANDLE.toUpperCase())

        editHandleViewModel.handleLiveData.observeOnce {
            assert(it == TEST_HANDLE.toLowerCase())
        }
    }

    @Test
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, checkExist succeeds, validation succeeds, then handle should update`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe true
            }

            editHandleViewModel.successLiveData.observeOnce {
                it shouldBe HandleIsAvailable
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, currentInput == currentHandle, then CheckExistsUseCase should not be called`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe HandleSameAsCurrent
            }

            verifyNoInteractions(checkHandleExistsUseCase)
        }

    @Test
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, check exists fails with HandleUnknownError, then ok button disabled and error updated`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Left(UnknownError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe UnknownError
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists fails with HandleAlreadyTakenError, then ok button is disabled and error should be updated`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Left(HandleAlreadyExists))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }
            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe HandleAlreadyExists
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleTooLongError then ok button should be disabled`() =
        runBlockingTest {
            val checkExistsParams = CheckHandleExistsParams(NON_DUPLICATED_TEST_HANDLE)
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(checkExistsParams)).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooLong))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleTooShortError then ok button should be disabled`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run((any()))).thenReturn(Either.Left(HandleTooShort))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called, when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleUnknownError then ok button should be disabled`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(UnknownError))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe UnknownError
            }
            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given afterHandleTextChanged is called when getHandleUseCase succeeds, check exists succeeds and validation fails with HandleInvalidError, then ok button should be disabled and error updated`() =
        runBlockingTest {
            val handleFlow: Flow<String> = flow { NON_DUPLICATED_TEST_HANDLE }
            lenient().`when`(getHandleUseCase.run(Unit)).thenReturn(handleFlow)
            lenient().`when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Right(HandleIsAvailable))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleInvalid))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe HandleInvalid
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle succeeds and updateHandle succeeds then dialog is dismissed`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.dismissLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleTooLongError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooLong))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleTooShortError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooShort))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleInvalidError then ok button should be disabled and error updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleInvalid))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe HandleInvalid
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleUnknownError then error message should be updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(UnknownError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe UnknownError
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle succeeds and update handle fails with HandleUnknownError, then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(DatabaseError))
            lenient().`when`(validateHandleUseCase.run(any())).thenReturn(Either.Right(TEST_HANDLE))
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(UnknownError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.errorLiveData.observeOnce {
                it shouldBe UnknownError
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is valid and update handle succeeds, then handle should be updated and dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe true
            }

            editHandleViewModel.dismissLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is valid and handle fails with HandleUnknownError then ok button should be disabled and dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(TEST_HANDLE)

            editHandleViewModel.okEnabledLiveData.observeOnce {
                it shouldBe false
            }

            editHandleViewModel.dismissLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is null, then dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(null)

            editHandleViewModel.dismissLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestHandle is empty, then dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(String.empty())

            editHandleViewModel.dismissLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    companion object {
        private const val NON_DUPLICATED_TEST_HANDLE = "testhandle1"
        private const val TEST_HANDLE = "testhandle"
    }
}

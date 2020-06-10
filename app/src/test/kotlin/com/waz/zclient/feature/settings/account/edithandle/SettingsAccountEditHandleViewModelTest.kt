package com.waz.zclient.feature.settings.account.edithandle

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.user.handle.HandleAlreadyExists
import com.waz.zclient.shared.user.handle.HandleInvalid
import com.waz.zclient.shared.user.handle.HandleIsAvailable
import com.waz.zclient.shared.user.handle.HandleSameAsCurrent
import com.waz.zclient.shared.user.handle.HandleTooLong
import com.waz.zclient.shared.user.handle.HandleTooShort
import com.waz.zclient.shared.user.handle.UnknownError
import com.waz.zclient.shared.user.handle.usecase.ChangeHandleUseCase
import com.waz.zclient.shared.user.handle.usecase.CheckHandleExistsUseCase
import com.waz.zclient.shared.user.handle.usecase.GetHandleUseCase
import com.waz.zclient.shared.user.handle.usecase.ValidateHandleUseCase
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class SettingsAccountEditHandleViewModelTest : UnitTest() {

    @get:Rule
    val crTestRule = CoroutinesTestRule()

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
    fun `given afterHandleTextChanged is called, when input contains capital letters then popragate handle with lowercase value`() =
        runBlockingTest {
            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE.toUpperCase())
            assertEquals(TEST_HANDLE.toLowerCase(), editHandleViewModel.handleLiveData.awaitValue())
        }

    @Test
    fun `given afterHandleTextChanged is called, when input validation fails then propagate error and disable ok button`() =
        runBlockingTest {
            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooShort))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(HandleTooShort, editHandleViewModel.errorLiveData.awaitValue())
        }

    @Test
    fun `given afterHandleTextChanged is called, when input is the same your current handle then propagate HandleSameAsCurrent error and disable ok button `() =
        runBlockingTest {
            val handleFlow = flowOf(TEST_HANDLE)

            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Right(TEST_HANDLE))
            `when`(getHandleUseCase.run(any())).thenReturn(handleFlow)

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(HandleSameAsCurrent, editHandleViewModel.errorLiveData.awaitValue())
        }

    @Test
    fun `given afterHandleTextChanged is called, when input is not duplicate your current handle and handle does not exist, then propagate HandleAlreadyExists error and disable ok button`() =
        runBlockingTest {
            val handleFlow = flowOf(NON_DUPLICATED_TEST_HANDLE)

            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Right(TEST_HANDLE))
            `when`(getHandleUseCase.run(any())).thenReturn(handleFlow)
            `when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Left(HandleAlreadyExists))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(HandleAlreadyExists, editHandleViewModel.errorLiveData.awaitValue())
        }

    @Test
    fun `given afterHandleTextChanged is called, when input is not duplicate of your current handle and new handle exists, then propagate HandleIsAvailable and enable ok button`() =
        runBlockingTest {
            val handleFlow = flowOf(NON_DUPLICATED_TEST_HANDLE)

            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Right(TEST_HANDLE))
            `when`(getHandleUseCase.run(any())).thenReturn(handleFlow)
            `when`(checkHandleExistsUseCase.run(any())).thenReturn(Either.Right(HandleIsAvailable))

            editHandleViewModel.afterHandleTextChanged(TEST_HANDLE)

            assertTrue(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(HandleIsAvailable, editHandleViewModel.successLiveData.awaitValue())
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle succeeds and updateHandle succeeds then dialog is dismissed`() =
        runBlockingTest {
            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Right(TEST_HANDLE))
            `when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            assertEquals(Unit, editHandleViewModel.dismissLiveData.awaitValue())
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleTooLongError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooLong))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleTooShortError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleTooShort))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleInvalidError then ok button should be disabled and error updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(HandleInvalid))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(HandleInvalid, editHandleViewModel.errorLiveData.awaitValue())
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle fails with HandleUnknownError then error message should be updated`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))
            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Left(UnknownError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(UnknownError, editHandleViewModel.errorLiveData.awaitValue())
        }

    @Test
    fun `given onOkButtonClicked is called, when validateHandle succeeds and update handle fails with HandleUnknownError, then ok button should be disabled`() =
        runBlockingTest {
            `when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(DatabaseError))
            `when`(validateHandleUseCase.run(any())).thenReturn(Either.Right(TEST_HANDLE))
            `when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(UnknownError))

            editHandleViewModel.onOkButtonClicked(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(UnknownError, editHandleViewModel.errorLiveData.awaitValue())
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestedHandle is valid and update handle succeeds, then dialog should be dismissed`() =
        runBlockingTest {
            `when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(TEST_HANDLE)

            assertEquals(Unit, editHandleViewModel.dismissLiveData.awaitValue())
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestedHandle is valid and handle fails with UnknownError then ok button should be disabled and dialog should dismiss`() =
        runBlockingTest {
            `when`(changeHandleUseCase.run(any())).thenReturn(Either.Left(UnknownError))

            editHandleViewModel.onBackButtonClicked(TEST_HANDLE)

            assertFalse(editHandleViewModel.okEnabledLiveData.awaitValue())
            assertEquals(UnknownError, editHandleViewModel.errorLiveData.awaitValue())
            assertEquals(Unit, editHandleViewModel.dismissLiveData.awaitValue())
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestedHandle is null, then dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(null)

            assertEquals(Unit, editHandleViewModel.dismissLiveData.awaitValue())
        }

    @Test
    fun `given onBackButtonClicked is called, when suggestedHandle is empty, then dialog should dismiss`() =
        runBlockingTest {
            lenient().`when`(changeHandleUseCase.run(any())).thenReturn(Either.Right(Unit))

            editHandleViewModel.onBackButtonClicked(String.empty())

            assertEquals(Unit, editHandleViewModel.dismissLiveData.awaitValue())
        }

    companion object {
        private const val NON_DUPLICATED_TEST_HANDLE = "testhandle1"
        private const val TEST_HANDLE = "testhandle"
    }
}

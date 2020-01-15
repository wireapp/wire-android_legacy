package com.waz.zclient.settings.account.edithandle

import com.waz.zclient.UnitTest
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.CheckHandleExistsUseCase
import com.waz.zclient.user.domain.usecase.handle.GetHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

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

        }

    @Test
    fun `given beforeHandTextChange is called and validation fails with HandleTooLongError, then handle input should be updated`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called and getHandleUseCase fails with DatabaseError, then okButton should be disabled`() =
        runBlockingTest {

        }


    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput valid, checkExist succeeds, validation succeeds, handle should update `() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase fails with Database Error, currentInput valid,  then okButton should be disabled`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is invalid,  check exists use case should not be called`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid,  check exists fails with HandleUnknwonError, then ok button is disabled and error should be updated`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid,  check exists fails with HandleAlreadyTakenError, then ok button is disabled and error should be updated`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid,  check exists succeeds, validation fails with HandleTooLongError, ok button should be disabled`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid,  check exists succeeds, validation fails with HandleTooShortError, ok button should be disabled`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid,  check exists succeeds, validation fails with HandleUnknownError, ok button should be disabled`() =
        runBlockingTest {

        }

    @Test
    fun `given afterHandleTextChanged is called, getHandleUseCase succeeds, currentInput is valid,  check exists succeeds, validation fails with HandleInvalidError, ok button should be disabled and error updated`() =
        runBlockingTest {

        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle succeeds, updateHandle succeeds, dialog is dismissed`() =
        runBlockingTest {

        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleTooLongError, ok button should be disabled`() =
        runBlockingTest {

        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleTooShortError, ok button should be disabled`() =
        runBlockingTest {

        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleInvalidError, ok button should be disabled and error updated`() =
        runBlockingTest {

        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle fails with HandleUnknownError, error message should be updated`() =
        runBlockingTest {

        }

    @Test
    fun `given onOkButtonClicked is called, validateHandle succeeds, update handle fails with HandleUnknownError, ok button should be disabled`() =
        runBlockingTest {

        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is false, suggestHandle is valid, update handle succeeds, handle should be update, dialog should dismiss`() =
        runBlockingTest {

        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is false, suggestHandle is valid, update handle fails with HandleUnknownError, ok button should be disabled, dialog should dismiss`() =
        runBlockingTest {

        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is true, suggestHandle is valid, dialog should dismiss`() =
        runBlockingTest {

        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is true, suggestHandle is null, dialog should dismiss`() =
        runBlockingTest {

        }

    @Test
    fun `given onBackButtonClicked is called, isCancelable is true, suggestHandle is empty, dialog should dismiss`() =
        runBlockingTest {

        }
}

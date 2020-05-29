package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.name.CreatePersonalAccountNameViewModel
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.user.email.EmailTooShort
import com.waz.zclient.shared.user.name.NameTooShort
import com.waz.zclient.shared.user.name.ValidateNameUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountNameViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    private lateinit var createPersonalAccountNameViewModel: CreatePersonalAccountNameViewModel

    @Mock
    private lateinit var validateNameUseCase: ValidateNameUseCase

    @Before
    fun setup() {
        createPersonalAccountNameViewModel = CreatePersonalAccountNameViewModel(
            validateNameUseCase
        )
    }

    @Test
    fun `given validateName is called, when the validation fails with NameTooShort then ok button should be disabled`() =
        runBlocking {
            Mockito.`when`(validateNameUseCase.run(any())).thenReturn(Either.Left(NameTooShort))

            createPersonalAccountNameViewModel.validateName(TEST_NAME)

            assertFalse(createPersonalAccountNameViewModel.isValidNameLiveData.awaitValue())
        }

    @Test
    fun `given validateName is called, when the validation succeeds then ok button should be enabled`() =
        runBlocking {
            Mockito.`when`(validateNameUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountNameViewModel.validateName(TEST_NAME)

            assertTrue(createPersonalAccountNameViewModel.isValidNameLiveData.awaitValue())
        }

    companion object {
        private const val TEST_NAME = "testName"
    }
}

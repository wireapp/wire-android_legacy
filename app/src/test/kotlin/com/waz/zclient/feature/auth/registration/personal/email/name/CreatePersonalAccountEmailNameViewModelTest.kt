package com.waz.zclient.feature.auth.registration.personal.email.name

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
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
class CreatePersonalAccountEmailNameViewModelTest : UnitTest() {

    @get:Rule
    val testRule = CoroutinesTestRule()

    private lateinit var nameViewModel: CreatePersonalAccountEmailNameViewModel

    @Mock
    private lateinit var validateNameUseCase: ValidateNameUseCase

    @Before
    fun setup() {
        nameViewModel = CreatePersonalAccountEmailNameViewModel(
            validateNameUseCase
        )
    }

    @Test
    fun `given validateName is called, when the validation fails with NameTooShort then isValidName should be false`() =
        runBlocking {
            Mockito.`when`(validateNameUseCase.run(any())).thenReturn(Either.Left(NameTooShort))

            nameViewModel.validateName(TEST_NAME)

            assertFalse(nameViewModel.isValidNameLiveData.awaitValue())
        }

    @Test
    fun `given validateName is called, when the validation succeeds then isValidName should be true`() =
        runBlocking {
            Mockito.`when`(validateNameUseCase.run(any())).thenReturn(Either.Right(Unit))

            nameViewModel.validateName(TEST_NAME)

            assertTrue(nameViewModel.isValidNameLiveData.awaitValue())
        }

    companion object {
        private const val TEST_NAME = "testName"
    }
}

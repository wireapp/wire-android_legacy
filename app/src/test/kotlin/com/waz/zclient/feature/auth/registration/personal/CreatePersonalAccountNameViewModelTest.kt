package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.feature.auth.registration.personal.name.CreatePersonalAccountNameViewModel
import com.waz.zclient.shared.user.name.ValidateNameUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.Before
import org.mockito.Mock

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountNameViewModelTest : UnitTest() {

    private lateinit var createPersonalAccountNameViewModel: CreatePersonalAccountNameViewModel

    @Mock
    private lateinit var validateNameUseCase: ValidateNameUseCase

    @Before
    fun setup() {
        createPersonalAccountNameViewModel = CreatePersonalAccountNameViewModel(
            validateNameUseCase
        )
    }

    //TODO add missing tests for validateName() once the solution for false positives is merged

    companion object {
        private const val TEST_NAME = "testName"
    }
}

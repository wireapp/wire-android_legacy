package com.waz.zclient.settings.account

import com.waz.zclient.UnitTest
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeNameUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class SettingsAccountViewModelTest : UnitTest() {

    private lateinit var viewModel: SettingsAccountViewModel

    @Mock
    private lateinit var getUserProfileUseCase: GetUserProfileUseCase

    @Mock
    private lateinit var changeNameUseCase: ChangeNameUseCase

    @Mock
    private lateinit var user: User

    private lateinit var userFlow: Flow<User>

    @Before
    fun setup() {
        viewModel = SettingsAccountViewModel(getUserProfileUseCase, changeNameUseCase)
        userFlow = flowOf(user)
    }

    @Test
    fun `given profile is loaded successfully, then account name observer is notified`() {

    }

    @Test
    fun `given profile is loaded successfully, then account handle observer is notified`() {

    }

    @Test
    fun `given profile is loaded successfully and account email is not null, then account email observer is notified and user email state is success`() {

    }

    @Test
    fun `given profile is loaded successfully and account email is null, then account email observer is notified and then user email state isNull`() {

    }

    @Test
    fun `given profile is loaded successfully and account phone is not null, then account phone observer is notified and then user phone state is success`() {

    }

    @Test
    fun `given profile is loaded successfully and account phone is null, then account phone observer is notified and then user phone state isNull`() {

    }

    @Test
    fun `given profile is loaded and fails with HTTPError, then error observer is notified`() {

    }

    @Test
    fun `given account name is updated successfully, then account name observer is notified`() {

    }

    @Test
    fun `given account name is updated and fails with HttpError, then error observer is notified`() {

    }

    companion object {
        const val TEST_NAME = "testName"
    }
}

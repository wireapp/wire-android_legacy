package com.waz.zclient.feature.settings.account.logout

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class LogoutViewModelTest : UnitTest() {

    @Mock
    private lateinit var logoutUseCase: LogoutUseCase

    private lateinit var logoutViewModel: LogoutViewModel

    @Before
    fun setUp() {
        logoutViewModel = LogoutViewModel(logoutUseCase)
    }

    @Test
    fun `given a logoutUseCase, when onVerifyButtonClicked is called, calls logoutUseCase`() =
        runBlockingTest {
            logoutViewModel.onVerifyButtonClicked()

            verify(logoutUseCase).invoke(any(), eq(Unit), any(), any())
        }

    @Test
    fun `given logoutUseCase returns success, when onVerifyButtonClicked is called, notifies logoutLiveData`() {
        //TODO add test case when useCase assertion problem is resolved
    }

    @Test
    fun `given logoutUseCase returns failure, when onVerifyButtonClicked is called, propagates error to errorLiveData`() {
        //TODO add test case when useCase assertion problem is resolved
    }
}

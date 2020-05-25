package com.waz.zclient.feature.settings.account.logout

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class LogoutViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    @Mock
    private lateinit var logoutUseCase: LogoutUseCase

    private lateinit var logoutViewModel: LogoutViewModel

    @Before
    fun setUp() {
        logoutViewModel = LogoutViewModel(logoutUseCase)
    }

    @Test
    fun `given a logoutUseCase, when onVerifyButtonClicked is called, calls logoutUseCase`() {
        runBlocking {
            `when`(logoutUseCase.run(Unit)).thenReturn(Either.Right(NoAccountsLeft))

            logoutViewModel.onVerifyButtonClicked()

            verify(logoutUseCase).run(eq(Unit))
        }
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

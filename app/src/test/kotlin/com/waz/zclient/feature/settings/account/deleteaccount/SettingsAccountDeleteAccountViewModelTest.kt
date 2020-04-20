package com.waz.zclient.feature.settings.account.deleteaccount

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.observeOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient

@ExperimentalCoroutinesApi
class SettingsAccountDeleteAccountViewModelTest : UnitTest() {

    private lateinit var deleteAccountViewModel: SettingsAccountDeleteAccountViewModel

    @Mock
    private lateinit var deleteAccountUseCase: DeleteAccountUseCase

    @Before
    fun setup() {
        deleteAccountViewModel = SettingsAccountDeleteAccountViewModel(deleteAccountUseCase)
    }

    @Test
    fun `given delete account confirmed, when delete account use case is a success, then confirm deletion`() = runBlockingTest {
        lenient().`when`(deleteAccountUseCase.run(Unit)).thenReturn(Either.Right(Unit))

        deleteAccountViewModel.onDeleteAccountConfirmed()

        deleteAccountViewModel.deletionConfirmedLiveData.observeOnce {
            it shouldBe Unit
        }
    }
}

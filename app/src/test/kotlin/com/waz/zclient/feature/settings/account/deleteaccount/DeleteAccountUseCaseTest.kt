package com.waz.zclient.feature.settings.account.deleteaccount

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.accounts.AccountsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class DeleteAccountUseCaseTest : UnitTest() {

    private lateinit var deleteAccountUseCase: DeleteAccountUseCase

    @Mock
    private lateinit var accountsRepository: AccountsRepository

    @Before
    fun setup() {
        deleteAccountUseCase = DeleteAccountUseCase(accountsRepository)
    }

    @Test
    fun `given use case is executed, then fire repository request`() = runBlockingTest {
        deleteAccountUseCase.run(Unit)

        verify(accountsRepository).deleteAccountPermanently()
    }
}

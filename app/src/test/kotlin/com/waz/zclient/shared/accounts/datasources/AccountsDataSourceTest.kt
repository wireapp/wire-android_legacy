package com.waz.zclient.shared.accounts.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.shared.accounts.AccountMapper
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.shared.accounts.datasources.local.AccountsLocalDataSource
import com.waz.zclient.shared.accounts.datasources.remote.AccountsRemoteDataSource
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions

@ExperimentalCoroutinesApi
class AccountsDataSourceTest : UnitTest() {

    private lateinit var accountsDataSource: AccountsDataSource

    @Mock
    private lateinit var accountMapper: AccountMapper

    @Mock
    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var remoteDataSource: AccountsRemoteDataSource

    @Mock
    private lateinit var localDataSource: AccountsLocalDataSource

    @Before
    fun setup() {
        accountsDataSource = AccountsDataSource(
            accountMapper, remoteDataSource, usersRemoteDataSource, localDataSource
        )
    }

    @Test
    fun `given active accounts is called, when results are mapped, then should return a list of domain active accounts`() =
        runBlockingTest {
            `when`(localDataSource.activeAccounts()).thenReturn(Either.Right(mockListOfEntities()))

            accountsDataSource.activeAccounts()

            verify(localDataSource).activeAccounts()

            localDataSource.activeAccounts().map { activeAccounts ->
                activeAccounts.map {
                    verify(accountMapper, times(activeAccounts.size)).from(it)
                }
            }
        }

    @Test
    fun `given deleteAcountFromDevice is called, then local data source should remove account from database`() = runBlockingTest {
        val account = mockActiveAccount()

        accountsDataSource.deleteAccountFromDevice(account)

        verify(localDataSource).removeAccount(accountMapper.toEntity(account))
        verifyNoMoreInteractions(localDataSource)
        verifyNoInteractions(usersRemoteDataSource)
    }

    @Test
    fun `given deleteAccountPermanently is called, then remote data source should request removal`() = runBlockingTest {
        accountsDataSource.deleteAccountPermanently()

        verify(usersRemoteDataSource).deleteAccountPermanently()
        verifyNoMoreInteractions(usersRemoteDataSource)
        verifyNoInteractions(localDataSource)
    }

    @Test
    fun `given refresh and access tokens, when logout is called, calls remoteDataSource's logout method`() =
        runBlockingTest {
            val refreshToken = "refreshToken"
            val accessToken = "accessToken"

            accountsDataSource.logout(refreshToken, accessToken)

            verify(remoteDataSource).logout(refreshToken, accessToken)
        }

    private fun mockActiveAccount() = mock(ActiveAccount::class.java)

    private fun mockListOfEntities(): List<ActiveAccountsEntity> {
        val activeAccountMock = mock(ActiveAccountsEntity::class.java)
        return listOf(activeAccountMock, activeAccountMock, activeAccountMock)
    }
}

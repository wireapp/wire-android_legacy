package com.waz.zclient.shared.accounts.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.shared.accounts.AccountMapper
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.shared.accounts.datasources.local.AccountsLocalDataSource
import com.waz.zclient.shared.accounts.datasources.remote.AccountsRemoteDataSource
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Assert.assertEquals
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
    fun `given activeAccounts is called, when localDataSource returns entities, then maps the entities and returns them`() =
        runBlockingTest {
            val entity1 = mockActiveAccountEntity()
            val entity2 = mockActiveAccountEntity()
            val activeAccount1 = mockActiveAccount()
            val activeAccount2 = mockActiveAccount()

            `when`(accountMapper.from(entity1)).thenReturn(activeAccount1)
            `when`(accountMapper.from(entity2)).thenReturn(activeAccount2)
            `when`(localDataSource.activeAccounts()).thenReturn(Either.Right(listOf(entity1, entity2)))

            val result = accountsDataSource.activeAccounts()

            result.isRight shouldBe true
            result.map {
                assertEquals(it, listOf(activeAccount1, activeAccount2))
            }
            verify(localDataSource).activeAccounts()
            verify(accountMapper, times(2)).from(any())
        }

    @Test
    fun `given activeAccounts is called, when localDataSource returns failure, then directly returns that failure`() =
        runBlockingTest {
            val failure = mock(DatabaseFailure::class.java)
            `when`(localDataSource.activeAccounts()).thenReturn(Either.Left(failure))

            val result = accountsDataSource.activeAccounts()

            result.isLeft shouldBe true
            result.onFailure {
                it shouldBe failure
            }
            verify(localDataSource).activeAccounts()
            verifyNoInteractions(accountMapper)
        }

    @Test
    fun `given activeAccountsById is called, when localDataSource returns an entity, then maps the entity and returns it`() =
        runBlockingTest {
            val activeAccountsEntity = mock(ActiveAccountsEntity::class.java)
            val activeAccount = mockActiveAccount()
            `when`(accountMapper.from(activeAccountsEntity)).thenReturn(activeAccount)

            `when`(localDataSource.activeAccountById(TEST_ID)).thenReturn(Either.Right(activeAccountsEntity))

            val result = accountsDataSource.activeAccountById(TEST_ID)

            verify(localDataSource).activeAccountById(TEST_ID)
            verify(accountMapper).from(activeAccountsEntity)
            result.isRight shouldBe true
            result.map {
                it shouldBe activeAccount
            }
        }

    @Test
    fun `given activeAccountsById is called, when localDataSource returns null, then directly returns null`() =
        runBlockingTest {
            `when`(localDataSource.activeAccountById(TEST_ID)).thenReturn(Either.Right(null))

            val result = accountsDataSource.activeAccountById(TEST_ID)

            verify(localDataSource).activeAccountById(TEST_ID)
            verifyNoInteractions(accountMapper)
            result.isRight shouldBe true
            result.map {
                it shouldBe null
            }
        }

    @Test
    fun `given activeAccountsById is called, when localDataSource returns failure, then directly returns that failure`() =
        runBlockingTest {
            val failure = mock(DatabaseFailure::class.java)
            `when`(localDataSource.activeAccountById(TEST_ID)).thenReturn(Either.Left(failure))

            val result = accountsDataSource.activeAccountById(TEST_ID)

            verify(localDataSource).activeAccountById(TEST_ID)
            verifyNoInteractions(accountMapper)
            result.isLeft shouldBe true
            result.onFailure {
                it shouldBe failure
            }
        }

    @Test
    fun `given deleteAccountFromDevice is called with an id, then calls local data source with given id`() = runBlockingTest {
        accountsDataSource.deleteAccountFromDevice(TEST_ID)

        verify(localDataSource).removeAccount(TEST_ID)
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

    companion object {
        private const val TEST_ID = "testId"

        private fun mockActiveAccount() = mock(ActiveAccount::class.java)

        private fun mockActiveAccountEntity()= mock(ActiveAccountsEntity::class.java)
    }
}

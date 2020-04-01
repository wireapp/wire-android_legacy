package com.waz.zclient.shared.accounts.datasources.local

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.mock
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class AccountsLocalDataSourceTest : UnitTest() {

    private lateinit var accountsLocalDataSource: AccountsLocalDataSource

    @Mock
    private lateinit var activeAccountsDao: ActiveAccountsDao

    @Before
    fun setup() {
        accountsLocalDataSource = AccountsLocalDataSource(activeAccountsDao)
    }

    @Test
    fun `Given activeAccounts is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            val result = accountsLocalDataSource.activeAccounts()

            verify(activeAccountsDao).activeAccounts()

            result.isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given activeAccounts is called, when dao result is cancelled, then returns error`() {
        runBlockingTest {
            val result = accountsLocalDataSource.activeAccounts()

            verify(activeAccountsDao).activeAccounts()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            result.isRight shouldBe false
        }
    }

    @Test
    fun `Given removeAccount is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            val mockAccount = mock(ActiveAccountsEntity::class)

            val result = accountsLocalDataSource.removeAccount(mockAccount)

            verify(activeAccountsDao).removeAccount(eq(mockAccount))

            result.isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given removeAccount is called, when dao result is cancelled, then returns error`() {
        runBlockingTest {
            val mockAccount = mock(ActiveAccountsEntity::class)
            accountsLocalDataSource.removeAccount(mockAccount)

            val result = accountsLocalDataSource.removeAccount(mockAccount)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            result.isRight shouldBe false
        }
    }

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "This is a test exception message."
    }
}

package com.waz.zclient.shared.accounts.datasources.local

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.map
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
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class AccountsLocalDataSourceTest : UnitTest() {

    private lateinit var accountsLocalDataSource: AccountsLocalDataSource

    @Mock
    private lateinit var activeAccountsDao: ActiveAccountsDao

    @Mock
    private lateinit var mockEntity: ActiveAccountsEntity

    @Before
    fun setup() {
        accountsLocalDataSource = AccountsLocalDataSource(activeAccountsDao)
    }

    @Test
    fun `Given activeAccounts is called, when dao result is successful, then return the data`() {
        runBlockingTest {
            `when`(activeAccountsDao.activeAccounts()).thenReturn(listOf(mockEntity))

            val result = accountsLocalDataSource.activeAccounts()

            verify(activeAccountsDao).activeAccounts()

            result.isRight shouldBe true
            result.map {
                it[0] shouldBe mockEntity
            }
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
    fun `Given activeAccountById is called, when dao returns an entity, then return the data`() {
        runBlockingTest {
            val entity = mock(ActiveAccountsEntity::class)
            `when`(activeAccountsDao.activeAccountById(TEST_ID)).thenReturn(entity)

            val result = accountsLocalDataSource.activeAccountById(TEST_ID)

            result.isRight shouldBe true
            result.map { it shouldBe entity }
            verify(activeAccountsDao).activeAccountById(TEST_ID)
        }
    }

    @Test
    fun `Given activeAccountById is called, when dao returns null, then return null with success`() {
        runBlockingTest {
            `when`(activeAccountsDao.activeAccountById(TEST_ID)).thenReturn(null)

            val result = accountsLocalDataSource.activeAccountById(TEST_ID)

            result.isRight shouldBe true
            result.map { it shouldBe null }
            verify(activeAccountsDao).activeAccountById(TEST_ID)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given activeAccountById is called, when dao result is cancelled, then returns error`() {
        runBlockingTest {
            val result = accountsLocalDataSource.activeAccountById(TEST_ID)

            verify(activeAccountsDao).activeAccountById(TEST_ID)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            result.isRight shouldBe false
        }
    }

    @Test
    fun `Given removeAccount is called, when dao result is successful, then return success`() {
        runBlockingTest {
            val result = accountsLocalDataSource.removeAccount(TEST_ID)

            verify(activeAccountsDao).removeAccount(TEST_ID)

            result.isRight shouldBe true
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given removeAccount is called, when dao result is cancelled, then returns error`() {
        runBlockingTest {
            accountsLocalDataSource.removeAccount(TEST_ID)

            val result = accountsLocalDataSource.removeAccount(TEST_ID)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            result.isRight shouldBe false
        }
    }

    companion object {
        private const val TEST_ID = "testId"
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "This is a test exception message."
    }
}

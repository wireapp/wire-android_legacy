package com.waz.zclient.storage.db.accountdata

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.accountdata.sqlite.GlobalDbSQLiteOpenHelper
import com.waz.zclient.storage.db.accountdata.sqlite.GlobalSQLiteDbTestHelper
import com.waz.zclient.storage.di.StorageModule.getGlobalDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4ClassRunner::class)
class GlobalDatabase24to25MigrationTest {

    private lateinit var testOpenHelper: GlobalDbSQLiteOpenHelper

    @Rule
    @JvmField
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GlobalDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    @Throws(Exception::class)
    fun setUp() {
        testOpenHelper = GlobalDbSQLiteOpenHelper(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            TEST_DB_NAME)
        GlobalSQLiteDbTestHelper.createTable(testOpenHelper)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        GlobalSQLiteDbTestHelper.clearDatabase(testOpenHelper)
    }

    @Test
    @Throws(IOException::class)
    fun migrateActiveAccountsFrom24to25_validateDataIsStillIntact() {
        GlobalSQLiteDbTestHelper.insertActiveAccount(
            id = TEST_ACTIVE_ACCOUNT_ID,
            teamId = null,
            cookie = TEST_ACTIVE_ACCOUNT_COOKIE,
            accessToken = TEST_ACTIVE_ACCOUNT_ACCESS_TOKEN,
            registeredPush = TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH,
            openHelper = testOpenHelper
        )

        val db = testHelper.runMigrationsAndValidate(
            TEST_DB_NAME,
            25,
            true,
            GLOBAL_DATABASE_MIGRATION_24_25)

        runBlocking {
            val activeAccounts = getActiveAccounts()
            val account = activeAccounts[0]
            assert(account.id == TEST_ACTIVE_ACCOUNT_ID)
            assert(account.teamId == null)
            assert(account.accessToken?.token == "111111111")
            assert(account.accessToken?.tokenType == "Bearer")
            assert(account.accessToken?.expiresInMillis == 1582896705028)
            assert(account.refreshToken == TEST_ACTIVE_ACCOUNT_COOKIE)
        }

        testHelper.closeWhenFinished(db)
    }

    private suspend fun getActiveAccounts(): List<ActiveAccountsEntity> =
        getGlobalDatabase(
            InstrumentationRegistry.getInstrumentation().targetContext,
            GlobalDatabase.migrations
        ).activeAccountsDao().activeAccounts()

    companion object {
        private const val TEST_DB_NAME = "ZGlobal.db"
        private const val TEST_ACTIVE_ACCOUNT_ID = "1"
        private const val TEST_ACTIVE_ACCOUNT_COOKIE = "111122333"
        private const val TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH = "11111122222"
        private const val ACCESS_TOKEN_JSON = """{"token":"111111111","type":"Bearer","expires":1582896705028}"""
        private val TEST_ACTIVE_ACCOUNT_ACCESS_TOKEN = JSONObject(ACCESS_TOKEN_JSON)
    }
}

package com.waz.zclient.storage.accountdata

import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.accountdata.GLOBAL_DATABASE_MIGRATION_24_25
import com.waz.zclient.storage.di.StorageModule.getGlobalDatabase
import com.waz.zclient.storage.globaldb.GlobalDbSQLiteOpenHelper
import com.waz.zclient.storage.globaldb.GlobalSQLiteDbTestHelper
import junit.framework.Assert.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GlobalDatabase24to25MigrationTest : IntegrationTest() {

    private lateinit var testOpenHelper: GlobalDbSQLiteOpenHelper

    private lateinit var testHelper: MigrationTestHelper

    @Before
    fun setUp() {
        testHelper = MigrationTestHelper(GlobalDatabase::class.java.canonicalName)
        testOpenHelper = GlobalDbSQLiteOpenHelper(getApplicationContext(),
            TEST_DB_NAME)
        GlobalSQLiteDbTestHelper.createTable(testOpenHelper)
    }

    @After
    fun tearDown() {
        GlobalSQLiteDbTestHelper.clearDatabase(testOpenHelper)
    }

    @Test
    fun migrateActiveAccountsFrom24to25_validateDataIsStillIntact() {
        GlobalSQLiteDbTestHelper.insertActiveAccount(
            id = TEST_ACTIVE_ACCOUNT_ID,
            teamId = null,
            cookie = TEST_ACTIVE_ACCOUNT_COOKIE,
            accessToken = TEST_ACTIVE_ACCOUNT_ACCESS_TOKEN,
            registeredPush = TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val activeAccounts = getActiveAccounts()
            val account = activeAccounts[0]
            assert(account.id == TEST_ACTIVE_ACCOUNT_ID)
            assert(account.teamId == null)
            assert(account.accessToken?.token == TEST_ACTIVE_ACCOUNT_COOKIE)
            assert(account.accessToken?.tokenType == TEST_ACCESS_TOKEN_TYPE)
            assert(account.accessToken?.expiresInMillis == 1582896705028)
            assert(account.refreshToken == TEST_ACTIVE_ACCOUNT_COOKIE)
        }
    }

    @Test
    fun migrateTeamsFrom24to25_validateDataIsStillIntact() {
        GlobalSQLiteDbTestHelper.insertTeam(
            id = TEST_TEAM_ID,
            name = TEST_TEAM_NAME,
            creator = TEST_TEAM_CREATOR,
            icon = TEST_TEAM_ICON,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val teams = getTeams()
            val team = teams[0]
            assert(team.teamId == TEST_TEAM_ID)
            assert(team.creatorId == TEST_TEAM_CREATOR)
            assert(team.iconId == TEST_TEAM_ICON)
            assert(team.teamName == TEST_TEAM_NAME)
        }
    }


    @Test
    fun migrateCacheEntryFrom24to25_validateDataIsStillIntact() {
        GlobalSQLiteDbTestHelper.insertCacheEntry(
            id = TEST_CACHE_ENTRY_ID,
            fileId = TEST_CACHE_ENTRY_FILE_ID,
            data = null,
            lastUsed = TEST_CACHE_ENTRY_LAST_USED,
            timeout = TEST_CACHE_ENTRY_TIME_OUT,
            filePath = TEST_CACHE_ENTRY_FILE_PATH,
            fileName = TEST_CACHE_ENTRY_FILE_NAME,
            mime = TEST_CACHE_ENTRY_MIME,
            encKey = TEST_CACHE_ENTRY_ENC_KEY,
            length = TEST_CACHE_ENTRY_LENGTH,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val cachedEntries = getCacheEntries()
            val cachedEntry = cachedEntries[0]
            assert(cachedEntry.key == TEST_CACHE_ENTRY_ID)
            assert(cachedEntry.fileId == TEST_CACHE_ENTRY_FILE_ID)
            assertNull(cachedEntry.data)
            assert(cachedEntry.lastUsed == TEST_CACHE_ENTRY_LAST_USED)
            assert(cachedEntry.timeout == TEST_CACHE_ENTRY_TIME_OUT)
            assert(cachedEntry.filePath == TEST_CACHE_ENTRY_FILE_PATH)
            assert(cachedEntry.mime == TEST_CACHE_ENTRY_MIME)
            assert(cachedEntry.encKey == TEST_CACHE_ENTRY_ENC_KEY)
            assert(cachedEntry.length == TEST_CACHE_ENTRY_LENGTH)
        }
    }

    private fun validateMigration() =
        testHelper.validateMigration(
            TEST_DB_NAME,
            25,
            true,
            GLOBAL_DATABASE_MIGRATION_24_25
        )

    private fun getGlobalDb() =
        getGlobalDatabase(
            getApplicationContext(),
            GlobalDatabase.migrations
        )

    private suspend fun getCacheEntries() =
        getGlobalDb().cacheEntryDao().cacheEntries()

    private suspend fun getActiveAccounts() =
        getGlobalDb().activeAccountsDao().activeAccounts()

    private suspend fun getTeams() =
        getGlobalDb().teamsDao().allTeams()

    companion object {

        private const val TEST_ACTIVE_ACCOUNT_ID = "1"

        //ActiveAccount
        private const val TEST_DB_NAME = "ZGlobal.db"
        private const val TEST_ACTIVE_ACCOUNT_COOKIE = "111122333"
        private const val TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH = "11111122222"
        private const val TEST_ACCESS_TOKEN_TYPE = "Bearer"
        private const val TEST_ACCESS_TOKEN_EXPIRATION_TIME = 1582896705028
        private const val ACCESS_TOKEN_JSON = """{"token":"$TEST_ACTIVE_ACCOUNT_COOKIE","type":$TEST_ACCESS_TOKEN_TYPE,"expires":$TEST_ACCESS_TOKEN_EXPIRATION_TIME}"""

        //Teams
        private const val TEST_TEAM_ID = "1"
        private const val TEST_TEAM_NAME = "testTeam"
        private const val TEST_TEAM_CREATOR = "123"
        private const val TEST_TEAM_ICON = "teamIcon.png"

        //Cache Entry
        private const val TEST_CACHE_ENTRY_ID = "1"
        private const val TEST_CACHE_ENTRY_FILE_ID = "fileId"
        private const val TEST_CACHE_ENTRY_LAST_USED = 38847746L
        private const val TEST_CACHE_ENTRY_TIME_OUT = 1582896705028L
        private const val TEST_CACHE_ENTRY_FILE_PATH = "/data/downloads/"
        private const val TEST_CACHE_ENTRY_FILE_NAME = "cachentry2"
        private const val TEST_CACHE_ENTRY_MIME = ".txt"
        private const val TEST_CACHE_ENTRY_ENC_KEY = "AES256"
        private const val TEST_CACHE_ENTRY_LENGTH = 200L


        private val TEST_ACTIVE_ACCOUNT_ACCESS_TOKEN = JSONObject(ACCESS_TOKEN_JSON)
    }
}

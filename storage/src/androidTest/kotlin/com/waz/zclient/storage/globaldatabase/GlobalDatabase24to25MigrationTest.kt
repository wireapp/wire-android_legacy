package com.waz.zclient.storage.globaldatabase

import com.waz.zclient.storage.DbSQLiteOpenHelper
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.accountdata.GLOBAL_DATABASE_MIGRATION_24_25
import com.waz.zclient.storage.di.StorageModule.getGlobalDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GlobalDatabase24to25MigrationTest : IntegrationTest() {

    private lateinit var testOpenHelper: DbSQLiteOpenHelper

    private lateinit var testHelper: MigrationTestHelper

    @Before
    fun setUp() {
        testHelper = MigrationTestHelper(GlobalDatabase::class.java.canonicalName)
        testOpenHelper = DbSQLiteOpenHelper(getApplicationContext(),
            TEST_DB_NAME, 24)
        GlobalSQLiteDbTestHelper.createTable(testOpenHelper)
    }

    @After
    fun tearDown() {
        GlobalSQLiteDbTestHelper.clearDatabase(testOpenHelper)
        GlobalSQLiteDbTestHelper.closeDatabase(testOpenHelper)
    }

    @Test
    fun givenActiveAccountInsertedIntoActiveAccountVersion24_whenMigratedToVersion25_thenAssertDataIsStillIntact() {
        val testActiveAccountId = "1"
        val testActiveAccountCookie = "111122333"
        val testActiveAccountRegisteredPush = "11111122222"
        val testAccessTokenType = "Bearer"
        val testAccessTokenExpiration = 1582896705028
        val testAccessTokenJson = """{"token":"$testActiveAccountCookie","type":$testAccessTokenType,"expires":$testAccessTokenExpiration}"""
        val testAccessTokenJsonObject = JSONObject(testAccessTokenJson)

        GlobalSQLiteDbTestHelper.insertActiveAccount(
            id = testActiveAccountId,
            teamId = null,
            cookie = testActiveAccountCookie,
            accessToken = testAccessTokenJsonObject,
            registeredPush = testActiveAccountRegisteredPush,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val activeAccounts = getActiveAccounts()
            with(activeAccounts[0]) {
                assertEquals(id, testActiveAccountId)
                assertEquals(teamId, null)
                assertEquals(accessToken?.token, testActiveAccountCookie)
                assertEquals(accessToken?.tokenType, testAccessTokenType)
                assertEquals(accessToken?.expiresInMillis, 1582896705028)
                assertEquals(refreshToken, testActiveAccountCookie)
            }
        }
    }

    @Test
    fun givenTeamInsertedIntoTeamsVersion24_whenMigratedToVersion25_thenAssertDataIsStillIntact() {
        val testTeamId = "1"
        val testTeamName = "testTeam"
        val testTeamCreator = "123"
        val testTeamIcon = "teamIcon.png"

        GlobalSQLiteDbTestHelper.insertTeam(
            id = testTeamId,
            name = testTeamName,
            creator = testTeamCreator,
            icon = testTeamIcon,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val teams = getTeams()
            with(teams[0]) {
                assertEquals(teamId, testTeamId)
                assertEquals(creatorId, testTeamCreator)
                assertEquals(iconId, testTeamIcon)
                assertEquals(teamName, testTeamName)
            }
        }
    }


    @Test
    fun givenCacheEntryInsertedIntoCacheEntryVersion24_whenMigratedToVersion25_thenAssertDataIsStillIntact() {
        val testCacheEntryId = "1"
        val testCacheEntryFileId = "fileId"
        val testCacheEntryLastUsed = 38847746L
        val testCacheEntryTimeout = 1582896705028L
        val testCacheEntryFilePath = "/data/downloads/"
        val testCacheEntryfileName = "cachentry2"
        val testCacheEntryMime = ".txt"
        val testCacheEntryEncKey = "AES256"
        val testCacheEntryLength = 200L

        GlobalSQLiteDbTestHelper.insertCacheEntry(
            id = testCacheEntryId,
            fileId = testCacheEntryFileId,
            data = null,
            lastUsed = testCacheEntryLastUsed,
            timeout = testCacheEntryTimeout,
            filePath = testCacheEntryFilePath,
            fileName = testCacheEntryfileName,
            mime = testCacheEntryMime,
            encKey = testCacheEntryEncKey,
            length = testCacheEntryLength,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val cachedEntries = getCacheEntries()
            with(cachedEntries[0]) {
                assertEquals(key, testCacheEntryId)
                assertEquals(fileId, testCacheEntryFileId)
                assertEquals(data, null)
                assertEquals(lastUsed, testCacheEntryLastUsed)
                assertEquals(timeout, testCacheEntryTimeout)
                assertEquals(filePath, testCacheEntryFilePath)
                assertEquals(mime, testCacheEntryMime)
                assertEquals(encKey, testCacheEntryEncKey)
                assertEquals(length, testCacheEntryLength)
            }
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
        private const val TEST_DB_NAME = "ZGlobal.db"
    }
}

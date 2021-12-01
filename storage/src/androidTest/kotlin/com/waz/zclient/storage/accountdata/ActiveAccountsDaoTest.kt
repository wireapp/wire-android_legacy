package com.waz.zclient.storage.accountdata

import androidx.room.Room
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.accountdata.AccessTokenEntity
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.db.accountdata.ActiveAccountsEntity
import com.waz.zclient.storage.db.accountdata.SsoIdEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ActiveAccountsDaoTest : IntegrationTest() {

    private lateinit var activeAccountsDao: ActiveAccountsDao

    private lateinit var globalDatabase: GlobalDatabase

    @Before
    fun setup() {
        globalDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            GlobalDatabase::class.java
        ).build()
        activeAccountsDao = globalDatabase.activeAccountsDao()
    }

    @After
    fun tearDown() {
        globalDatabase.close()
    }

    @Test
    fun givenAnActiveAccountWithAccessToken_whenAccessTokenIsCalledForThatAccount_thenReturnsAccessToken() = runBlocking {
        val token = createAccessTokenEntity()
        val activeAccount = createActiveAccount(TEST_USER_ID, accessToken = token)
        activeAccountsDao.insertActiveAccount(activeAccount)

        val accessToken = activeAccountsDao.accessToken(TEST_USER_ID)

        assertEquals(accessToken?.token, token.token)
        assertEquals(accessToken?.tokenType, token.tokenType)
        assertEquals(accessToken?.expiresInMillis, token.expiresInMillis)
    }

    @Test
    fun givenAnActiveAccountWithoutAccessToken_whenAccessTokenIsCalledForThatAccount_thenReturnsNull() = runBlocking {
        val activeAccount = createActiveAccount(TEST_USER_ID, accessToken = null)
        activeAccountsDao.insertActiveAccount(activeAccount)

        val accessToken = activeAccountsDao.accessToken(TEST_USER_ID)
        assertEquals(accessToken, null)
    }

    @Test
    fun givenAnActiveAccountWithAccessToken_whenUpdateAccessTokenIsCalledWithNewToken_thenUpdatesExistingToken() {
        val initialToken = AccessTokenEntity("oldToken", "oldTokenType", 12345L)
        testAccessTokenUpdated(initialToken)
    }

    @Test
    fun givenAnActiveAccountWithNoAccessToken_whenUpdateAccessTokenIsCalledWithNewToken_thenUpdatesExistingToken() {
        testAccessTokenUpdated(initialToken = null)
    }

    private fun testAccessTokenUpdated(initialToken: AccessTokenEntity?) = runBlocking {
        val activeAccount = createActiveAccount(TEST_USER_ID, accessToken = initialToken)
        activeAccountsDao.insertActiveAccount(activeAccount)

        val newAccessToken = AccessTokenEntity("newToken", "newTokenType", 3459834L)
        activeAccountsDao.updateAccessToken(TEST_USER_ID, newAccessToken)

        val accessToken = activeAccountsDao.accessToken(TEST_USER_ID)

        assertEquals(accessToken?.token, newAccessToken.token)
        assertEquals(accessToken?.tokenType, newAccessToken.tokenType)
        assertEquals(accessToken?.expiresInMillis, newAccessToken.expiresInMillis)
    }

    @Test
    fun givenAnActiveAccountWithRefreshToken_whenUpdateRefreshTokenIsCalled_thenUpdatesExistingRefreshToken() =
        runBlocking {
            val oldRefreshToken = "oldToken"
            val activeAccount = createActiveAccount(TEST_USER_ID, refreshToken = oldRefreshToken)
            activeAccountsDao.insertActiveAccount(activeAccount)

            val newRefreshToken = "newToken"
            activeAccountsDao.updateRefreshToken(TEST_USER_ID, newRefreshToken)

            val refreshToken = activeAccountsDao.refreshToken(TEST_USER_ID)
            assertEquals(refreshToken, newRefreshToken)
        }

    @Test
    fun givenTableHasActiveAccounts_whenAllAccountsIsCalled_thenReturnsAllAccounts() = runBlocking {
        val account1 = createActiveAccount("id1")
        val account2 = createActiveAccount("id2")
        val activeAccounts = listOf(account1, account2)
        activeAccounts.map {
            activeAccountsDao.insertActiveAccount(it)
        }

        val roomActiveAccounts = activeAccountsDao.activeAccounts()
        assertEquals(roomActiveAccounts.size, 2)

        val firstAccount = roomActiveAccounts[0]
        assertEquals(firstAccount, account1)

        val secondAccount = roomActiveAccounts[1]
        assertEquals(secondAccount, account2)
    }

    @Test
    fun givenAnActiveAccountInDatabase_whenActiveAccountByIdIsCalledWithItsId_thenReturnsThatAccount() =
        runBlocking {
            val activeAccount = createActiveAccount(TEST_USER_ID)
            activeAccountsDao.insertActiveAccount(activeAccount)

            val retrievedAccount = activeAccountsDao.activeAccountById(TEST_USER_ID)

            assertEquals(activeAccount, retrievedAccount)
        }

    @Test
    fun givenNoActiveAccountInDatabaseWithGivenId_whenActiveAccountByIdIsCalled_thenReturnsNull() =
        runBlocking {
            activeAccountsDao.insertActiveAccount(createActiveAccount(TEST_USER_ID))

            val retrievedAccount = activeAccountsDao.activeAccountById("someOtherUserId")

            assertEquals(retrievedAccount, null)
        }

    @Test
    fun givenAnActiveAccount_whenRemoveAccountIsCalledForThatAccount_thenRemovesAccount() = runBlocking {
        val activeAccount = createActiveAccount(TEST_USER_ID)
        activeAccountsDao.insertActiveAccount(activeAccount)

        activeAccountsDao.removeAccount(TEST_USER_ID)

        assertTrue(activeAccountsDao.activeAccountById(TEST_USER_ID) == null)
    }

    companion object {
        private const val TEST_USER_ID = "userId"
        private const val TEST_ACTIVE_ACCOUNT_TEAM_ID = "1000229992"
        private const val TEST_ACTIVE_ACCOUNT_COOKIE = "111122333"
        private const val TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH = "11111122222"
        private const val TEST_ACCESS_TOKEN_STRING = "accessToken123"
        private const val TEST_ACCESS_TOKEN_TYPE = "Bearer"
        private const val TEST_ACCESS_TOKEN_EXPIRATION_TIME = 1582896705028
        private const val TEST_ACTIVE_ACCOUNT_SSO_ID_TENANT = "ssoIdTenant"
        private const val TEST_ACTIVE_ACCOUNT_SSO_ID_SUBJECT = "ssoIdSubject"

        private fun createActiveAccount(
            userId: String,
            accessToken: AccessTokenEntity? = createAccessTokenEntity(),
            refreshToken: String = TEST_ACTIVE_ACCOUNT_COOKIE
        ) = ActiveAccountsEntity(
            id = userId,
            domain = "",
            teamId = TEST_ACTIVE_ACCOUNT_TEAM_ID,
            refreshToken = refreshToken,
            accessToken = accessToken,
            pushToken = TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH,
            ssoId = SsoIdEntity(
                TEST_ACTIVE_ACCOUNT_SSO_ID_TENANT,
                TEST_ACTIVE_ACCOUNT_SSO_ID_SUBJECT
            )
        )

        private fun createActiveAccountList(vararg userIds: String): List<ActiveAccountsEntity> =
            userIds.map { createActiveAccount(it) }

        private fun createAccessTokenEntity() = AccessTokenEntity(
            TEST_ACCESS_TOKEN_STRING, TEST_ACCESS_TOKEN_TYPE, TEST_ACCESS_TOKEN_EXPIRATION_TIME
        )
    }

}

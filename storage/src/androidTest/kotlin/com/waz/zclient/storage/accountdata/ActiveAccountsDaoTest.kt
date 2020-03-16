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
    fun givenAnActiveAccount_whenAccessTokenIsCalledWithUserIdThatIsActiveUser_thenDataShouldBeTheSame() = runBlocking {
        val activeAccount = createActiveAccount(TEST_ACTIVE_ACCOUNT_ID_ACTIVE)
        activeAccountsDao.insertActiveAccount(activeAccount)

        val accessToken = activeAccountsDao.accessToken(TEST_ACTIVE_ACCOUNT_ID_ACTIVE)
        assert(accessToken?.token == TEST_ACTIVE_ACCOUNT_COOKIE)
        assert(accessToken?.tokenType == TEST_ACCESS_TOKEN_TYPE)
        assert(accessToken?.expiresInMillis == TEST_ACCESS_TOKEN_EXPIRATION_TIME)
    }

    @Test
    fun givenAnActiveAccount_whenAccessTokenIsCalledWithUserIdThatIsNotAnActiveUser_thenDataShouldBeTheSame() = runBlocking {
        val activeAccount = createActiveAccount(TEST_ACTIVE_ACCOUNT_ID_INACTIVE)
        activeAccountsDao.insertActiveAccount(activeAccount)

        val accessToken = activeAccountsDao.accessToken(TEST_ACTIVE_ACCOUNT_ID_ACTIVE)
        assert(accessToken == null)
    }

    @Test
    fun givenAnActiveAccount_whenUpdateAccessTokenIsCalledWithNewToken_thenDataShouldBeTheSameAsInserted() = runBlocking {
        val activeAccount = createActiveAccount(TEST_ACTIVE_ACCOUNT_ID_ACTIVE)
        activeAccountsDao.insertActiveAccount(activeAccount)

        activeAccountsDao.updateAccessToken(
            TEST_ACTIVE_ACCOUNT_ID_ACTIVE,
            createAccessTokenEntity(
                token = TEST_ACTIVE_ACCOUNT_COOKIE_UPDATED
            )
        )

        val accessToken = activeAccountsDao.accessToken(TEST_ACTIVE_ACCOUNT_ID_ACTIVE)
        assert(accessToken?.token == TEST_ACTIVE_ACCOUNT_COOKIE_UPDATED)
        assert(accessToken?.tokenType == TEST_ACCESS_TOKEN_TYPE)
        assert(accessToken?.expiresInMillis == TEST_ACCESS_TOKEN_EXPIRATION_TIME)
    }

    @Test
    fun givenAnActiveAccount_whenUpdateRefreshTokenIsCalledAndUserIdIsActiveUser_thenDataShouldBeTheSameAsInserted() =
        runBlocking {
            val activeAccount = createActiveAccount(TEST_ACTIVE_ACCOUNT_ID_ACTIVE)
            activeAccountsDao.insertActiveAccount(activeAccount)

            activeAccountsDao.updateRefreshToken(
                TEST_ACTIVE_ACCOUNT_ID_ACTIVE,
                TEST_ACTIVE_ACCOUNT_COOKIE_UPDATED
            )

            val refreshToken = activeAccountsDao.refreshToken(TEST_ACTIVE_ACCOUNT_ID_ACTIVE)
            assert(refreshToken == TEST_ACTIVE_ACCOUNT_COOKIE_UPDATED)
        }

    @Test
    fun givenAnActiveAccount_withGetAllAccountsIsCalled_thenDataShouldBeSameAsInserted() = runBlocking {
        val activeAccounts = createActiveAccountsList()
        activeAccounts.map {
            activeAccountsDao.insertActiveAccount(it)
        }

        val roomActiveAccounts = activeAccountsDao.activeAccounts()
        assert(roomActiveAccounts.size == 2)

        val firstAccount = roomActiveAccounts[0]
        assert(firstAccount.id == TEST_ACTIVE_ACCOUNT_ID_ACTIVE)

        val secondAccount = roomActiveAccounts[1]
        assert(secondAccount.id == TEST_ACTIVE_ACCOUNT_ID_INACTIVE)
    }

    @Test
    fun givenAnActiveAccount_whenDeleteAccountsWithThatAccount_thenRemoveAccountFromDatabase() = runBlocking {
        val activeAccounts = createActiveAccountsList()
        activeAccounts.map {
            activeAccountsDao.insertActiveAccount(it)
        }

        val roomActiveAccounts = activeAccountsDao.activeAccounts()
        roomActiveAccounts.map {
            activeAccountsDao.removeAccount(it)
        }

        assert(activeAccountsDao.activeAccounts().isEmpty())
    }

    private fun createActiveAccount(
        userId: String
    ) = ActiveAccountsEntity(
        id = userId,
        teamId = TEST_ACTIVE_ACCOUNT_TEAM_ID,
        refreshToken = TEST_ACTIVE_ACCOUNT_COOKIE,
        accessToken = createAccessTokenEntity(
            TEST_ACTIVE_ACCOUNT_COOKIE
        ),
        pushToken = TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH,
        ssoId = SsoIdEntity(
            TEST_ACTIVE_ACCOUNT_SSO_ID_TENANT,
            TEST_ACTIVE_ACCOUNT_SSO_ID_SUBJECT
        )
    )

    private fun createAccessTokenEntity(
        token: String,
        tokenType: String = TEST_ACCESS_TOKEN_TYPE,
        expiration: Long = TEST_ACCESS_TOKEN_EXPIRATION_TIME
    ): AccessTokenEntity = AccessTokenEntity(token, tokenType, expiration)

    private fun createActiveAccountsList() =
        listOf(
            createActiveAccount(TEST_ACTIVE_ACCOUNT_ID_ACTIVE),
            createActiveAccount(TEST_ACTIVE_ACCOUNT_ID_INACTIVE)
        )

    companion object {
        private const val TEST_ACTIVE_ACCOUNT_ID_ACTIVE = "101"
        private const val TEST_ACTIVE_ACCOUNT_ID_INACTIVE = "102"
        private const val TEST_ACTIVE_ACCOUNT_TEAM_ID = "1000229992"
        private const val TEST_ACTIVE_ACCOUNT_COOKIE = "111122333"
        private const val TEST_ACTIVE_ACCOUNT_COOKIE_UPDATED = "111122333444"
        private const val TEST_ACTIVE_ACCOUNT_REGISTERED_PUSH = "11111122222"
        private const val TEST_ACCESS_TOKEN_TYPE = "Bearer"
        private const val TEST_ACCESS_TOKEN_EXPIRATION_TIME = 1582896705028
        private const val TEST_ACTIVE_ACCOUNT_SSO_ID_TENANT = "ssoIdTenant"
        private const val TEST_ACTIVE_ACCOUNT_SSO_ID_SUBJECT = "ssoIdSubject"
    }

}

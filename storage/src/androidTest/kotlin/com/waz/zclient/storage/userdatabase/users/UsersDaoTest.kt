package com.waz.zclient.storage.userdatabase.users

import androidx.room.Room
import com.waz.zclient.framework.data.users.UsersTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.model.UsersEntity
import com.waz.zclient.storage.db.users.service.UsersDao
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

class UsersDaoTest : IntegrationTest() {

    private lateinit var usersDao: UsersDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
                getApplicationContext(),
                UserDatabase::class.java
        ).build()
        usersDao = userDatabase.usersDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllUsersIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        listOfUsers().forEach {
            usersDao.insert(it)
        }
        val storedUsers = usersDao.allUsers()

        assertEquals(storedUsers.first().id, TEST_USERS_ENTITY_PRIMARY_ID)
        assertEquals(storedUsers.last().id, TEST_USERS_ENTITY_SECONDARY_ID)
        assertEquals(storedUsers.size, 2)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)
        val storedUsers = usersDao.nextBatch(0, 5)

        assertEquals(storedUsers?.size, 5)
        assertEquals(usersDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedUsers = usersDao.nextBatch(5, 5)
        assertEquals(storedUsers?.size, 5)
        assertEquals(usersDao.count(), 10)
    }

    private fun usersEntity(userKey: String): UsersEntity {
        val data = UsersTestDataProvider.provideDummyTestData()
        return UsersEntity(
            id = userKey,
            teamId = data.teamId,
            name = data.name,
            email = data.email,
            phone = data.phone,
            trackingId = data.trackingId,
            picture = data.picture,
            accentId = data.accentId,
            sKey = data.sKey,
            connection = data.connection,
            connectionTimestamp = data.connectionTimestamp,
            connectionMessage = data.connectionMessage,
            conversation = data.conversation,
            relation = data.relation,
            timestamp = data.timestamp,
            verified = data.verified,
            deleted = data.deleted,
            availability = data.availability,
            handle = data.handle,
            providerId = data.providerId,
            integrationId = data.integrationId,
            expiresAt = data.expiresAt,
            managedBy = data.managedBy,
            selfPermission = data.selfPermission,
            copyPermission = data.copyPermission,
            createdBy = data.createdBy,
            domain = data.domain,
            conversationDomain = data.conversationDomain
        )
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = usersEntity(UUID.randomUUID().toString())
            usersDao.insert(normalEntity)
        }
    }

    private fun listOfUsers() = listOf(
        usersEntity(TEST_USERS_ENTITY_PRIMARY_ID),
        usersEntity(TEST_USERS_ENTITY_SECONDARY_ID)
    )

    companion object {
        private const val TEST_USERS_ENTITY_PRIMARY_ID = "1"
        private const val TEST_USERS_ENTITY_SECONDARY_ID = "2"
        private const val TEST_TENTH_ITEM_ID = "Item 10"
        private const val TEST_FIFTH_ITEM_ID = "Item 5"
    }
}

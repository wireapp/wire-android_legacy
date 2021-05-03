package com.waz.zclient.storage.userdatabase.clients

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class ClientsTable127to128MigrationTest : UserDatabaseMigrationTest(127, 128) {

    @Test
    fun givenClientIntoClientsTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        ClientsTableTestHelper.insertClient(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allClients()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.data, data)
            }
        }
    }

    private suspend fun allClients() =
        getDatabase().userClientDao().allClients()
}

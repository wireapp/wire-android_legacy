package com.waz.zclient.storage.userdatabase.clients

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ClientsTable126to127MigrationTest : UserDatabaseMigrationTest(126, 127) {

    @Test
    fun givenClientIntoClientsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        ClientsTableTestHelper.insertClient(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127)

        runBlocking {
            with(allClients()[0]) {
                assert(this.id == id)
                assert(this.data == data)
            }
        }
    }

    private suspend fun allClients() =
        getDatabase().userClientDao().allClients()
}

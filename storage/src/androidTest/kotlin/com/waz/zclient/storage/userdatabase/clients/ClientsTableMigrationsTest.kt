package com.waz.zclient.storage.userdatabase.clients

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ClientsTableMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenClientIntoClientsTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        ClientsTableTestHelper.insertClient(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allClients()[0]) {
                assert(this.id == id)
                assert(this.data == data)
            }
        }
    }

    private suspend fun allClients() =
        getUserDatabase().userClientDao().allClients()
}

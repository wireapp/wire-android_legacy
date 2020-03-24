package com.waz.zclient.storage.userdatabase.folders

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class FoldersTable126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

    @Test
    fun givenFolderInsertedIntoFoldersTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val name = "testName"
        val type = 2

        FoldersTableTestHelper.insertFolder(
            id = id,
            name = name,
            type = type,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allFolders()[0]) {
                assert(this.id == id)
                assert(this.name == name)
                assert(this.type == type)
            }
        }
    }

    private fun validateMigration() =
        testHelper.validateMigration(
            TEST_DB_NAME,
            127,
            true,
            USER_DATABASE_MIGRATION_126_TO_127
        )

    private fun getUserDb() =
        getUserDatabase(
            getApplicationContext(),
            TEST_DB_NAME,
            UserDatabase.migrations
        )

    private suspend fun allFolders() =
        getUserDb().foldersDao().allFolders()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}

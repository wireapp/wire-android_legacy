package com.waz.zclient.storage.userdatabase.folders

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class FoldersTable126to127MigrationTest : UserDatabaseMigrationTest(126, 127) {

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

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127)

        runBlocking {
            with(allFolders()[0]) {
                assert(this.id == id)
                assert(this.name == name)
                assert(this.type == type)
            }
        }
    }

    private suspend fun allFolders() = getDatabase().foldersDao().allFolders()
}

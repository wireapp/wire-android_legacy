package com.waz.zclient.storage.userdatabase.folders

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class FoldersTableMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenFolderInsertedIntoFoldersTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val id = "testId"
        val name = "testName"
        val type = 2

        FoldersTableTestHelper.insertFolder(
            id = id,
            name = name,
            type = type,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allFolders()[0]) {
                assert(this.id == id)
                assert(this.name == name)
                assert(this.type == type)
            }
        }
    }

    private suspend fun allFolders() = getUserDatabase().foldersDao().allFolders()
}

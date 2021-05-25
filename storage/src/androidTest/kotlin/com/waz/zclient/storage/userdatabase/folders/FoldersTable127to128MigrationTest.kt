package com.waz.zclient.storage.userdatabase.folders

import com.waz.zclient.framework.data.folders.FoldersTestDataProvider
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class FoldersTable127to128MigrationTest : UserDatabaseMigrationTest(127, 128) {

    @Test
    fun givenFolderInsertedIntoFoldersTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val data = FoldersTestDataProvider.provideDummyTestData()
        FoldersTableTestHelper.insertFolder(
            id = data.id,
            name = data.name,
            type = data.type,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allFolders()[0]) {
                assertEquals(this.id, data.id)
                assertEquals(this.name, data.name)
                assertEquals(this.type, data.type)
            }
        }
    }

    private suspend fun allFolders() = getDatabase().foldersDao().allFolders()
}

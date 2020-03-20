package com.waz.zclient.storage.userdatabase.assets

import com.waz.zclient.storage.DbSQLiteOpenHelper
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule
import com.waz.zclient.storage.userdatabase.UserDatabaseHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class AssetTables126to127MigrationTests : IntegrationTest() {
    private lateinit var testOpenHelper: DbSQLiteOpenHelper

    private val databaseHelper: UserDatabaseHelper by lazy {
        UserDatabaseHelper()
    }

    private lateinit var testHelper: MigrationTestHelper

    @Before
    fun setUp() {
        testHelper = MigrationTestHelper(UserDatabase::class.java.canonicalName)
        testOpenHelper = DbSQLiteOpenHelper(getApplicationContext(),
            TEST_DB_NAME, 126)
        databaseHelper.createDatabase(testOpenHelper)
    }

    @After
    fun tearDown() {
        databaseHelper.clearDatabase(testOpenHelper)
    }

    @Test
    fun givenUserInsertedIntoUsersTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {
        val assetId = "i747749kk-77"
        val assetType = "IMAGE"
        val assetData = "data"

        AssetsV1TableTestHelper.insertV1Asset(
            assetId,
            assetType,
            assetData,
            testOpenHelper
        )

        validateMigration()


        runBlocking {
            with(getV1Assets()[0]) {
                assert(this.id == assetId)
                assert(this.assetType == assetType)
                assert(this.data == assetData)
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
        StorageModule.getUserDatabase(
            getApplicationContext(),
            TEST_DB_NAME,
            UserDatabase.migrations
        )

    suspend fun getV1Assets() =
        getUserDb().assetsV1Dao().allAssets()

    companion object {
        private const val TEST_DB_NAME = "UserDatabase.db"
    }
}

package com.waz.zclient.storage

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule

class MigrationTestHelper(assetLocation: String?) {

    @Rule
    @JvmField
    val migrationTestHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            assetLocation,
            FrameworkSQLiteOpenHelperFactory()
        )

    fun validateMigration(
        dbName: String,
        dbVersion: Int,
        validateDroppedTables: Boolean,
        vararg migrations: Migration): SupportSQLiteDatabase =
        migrationTestHelper.runMigrationsAndValidate(
            dbName,
            dbVersion,
            validateDroppedTables,
            *migrations)
}

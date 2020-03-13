package com.waz.zclient.storage

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
abstract class IntegrationTest(assetLocation: String?) {

    @Rule
    @JvmField
    val testHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            assetLocation,
            FrameworkSQLiteOpenHelperFactory()
        )

    protected fun getApplicationContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    protected fun closeDb(db: SupportSQLiteDatabase) = testHelper.closeWhenFinished(db)
}

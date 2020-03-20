package com.waz.zclient.storage.userdatabase.sync

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.waz.zclient.storage.DbSQLiteOpenHelper


class SyncJobsTableTestHelper private constructor() {

    companion object {
        private const val SYNC_JOBS_TABLE_NAME = "SyncJobs"
        private const val SYNC_JOBS_ID_COL = "_id"
        private const val SYNC_JOBS_DATA_COL = "data"

        fun insertSyncJob(id: String, data: String, openHelper: DbSQLiteOpenHelper) {
            val contentValues = ContentValues().also {
                it.put(SYNC_JOBS_ID_COL, id)
                it.put(SYNC_JOBS_DATA_COL, data)
            }

            with(openHelper.writableDatabase) {
                insertWithOnConflict(
                    SYNC_JOBS_TABLE_NAME,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }
}

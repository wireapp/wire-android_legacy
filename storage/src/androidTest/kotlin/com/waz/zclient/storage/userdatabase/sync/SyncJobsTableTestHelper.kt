package com.waz.zclient.storage.userdatabase.sync

import android.content.ContentValues
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

            openHelper.insertWithOnConflict(
                tableName = SYNC_JOBS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

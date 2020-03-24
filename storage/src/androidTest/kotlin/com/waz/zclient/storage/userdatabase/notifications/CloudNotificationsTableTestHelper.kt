package com.waz.zclient.storage.userdatabase.notifications

import android.content.ContentValues

import com.waz.zclient.storage.DbSQLiteOpenHelper


class CloudNotificationsTableTestHelper private constructor() {

    companion object {

        private const val CLOUD_NOTIFICATION_TABLE_NAME = "FCMNotifications"
        private const val CLOUD_NOTIFICATION_ID_COL = "_id"
        private const val CLOUD_NOTIFICATION_STAGE_COL = "stage"
        private const val CLOUD_NOTIFICATION_START_TIME_COL = "stage_start_time"

        fun insertCloudNotification(id: String, stage: String, stageStartTime: Int,
                                    openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(CLOUD_NOTIFICATION_ID_COL, id)
                it.put(CLOUD_NOTIFICATION_STAGE_COL, stage)
                it.put(CLOUD_NOTIFICATION_START_TIME_COL, stageStartTime)
            }

            openHelper.insertWithOnConflict(
                tableName = CLOUD_NOTIFICATION_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

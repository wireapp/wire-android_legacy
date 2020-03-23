package com.waz.zclient.storage.userdatabase.notifications

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class CloudNotificationsStatsTableTestHelper private constructor() {

    companion object {

        private const val CLOUD_NOTIFICATION_STATS_TABLE_NAME = "FCMNotificationStats"
        private const val CLOUD_NOTIFICATION_STATS_STAGE_COL = "stage"
        private const val CLOUD_NOTIFICATION_STATS_FIRST_BUCKET_COL = "bucket1"
        private const val CLOUD_NOTIFICATION_STATS_SECOND_BUCKET_COL = "bucket2"
        private const val CLOUD_NOTIFICATION_STATS_THIRD_BUCKET_COL = "bucket3"

        fun insertCloudNotificationStat(
            stage: String,
            firstBucket: Int,
            secondBucket: Int, thirdBucket: Int,
            openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(CLOUD_NOTIFICATION_STATS_STAGE_COL, stage)
                it.put(CLOUD_NOTIFICATION_STATS_FIRST_BUCKET_COL, firstBucket)
                it.put(CLOUD_NOTIFICATION_STATS_SECOND_BUCKET_COL, secondBucket)
                it.put(CLOUD_NOTIFICATION_STATS_THIRD_BUCKET_COL, thirdBucket)
            }

            openHelper.insertWithOnConflict(
                tableName = CLOUD_NOTIFICATION_STATS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

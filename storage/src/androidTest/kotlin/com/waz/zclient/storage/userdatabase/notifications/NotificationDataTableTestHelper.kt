package com.waz.zclient.storage.userdatabase.notifications

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class NotificationDataTableTestHelper private constructor() {

    companion object {

        private const val NOTIFICATION_DATA_TABLE_NAME = "NotificationData"
        private const val NOTIFICATION_DATA_ID_COL = "_id"
        private const val NOTIFICATION_DATA_DATA_COL = "data"

        fun insertNotificationData(id: String, data: String, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(NOTIFICATION_DATA_ID_COL, id)
                it.put(NOTIFICATION_DATA_DATA_COL, data)
            }

            openHelper.insertWithOnConflict(
                tableName = NOTIFICATION_DATA_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

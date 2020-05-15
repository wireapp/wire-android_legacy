package com.waz.zclient.storage.userdatabase.notifications

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class PushNotificationEventsTableTestHelper private constructor() {

    companion object {

        private const val PUSH_NOTIFICATION_EVENTS_DATA_TABLE_NAME = "PushNotificationEvents"
        private const val PUSH_NOTIFICATION_EVENTS_EVENT_INDEX_COL = "event_index"
        private const val PUSH_NOTIFICATION_EVENTS_PUSH_ID_COL = "pushId"
        private const val PUSH_NOTIFICATION_EVENTS_DECRYPTED_COL = "decrypted"
        private const val PUSH_NOTIFICATION_EVENTS_EVENT_COL = "event"
        private const val PUSH_NOTIFICATION_EVENTS_PLAIN_COL = "plain"
        private const val PUSH_NOTIFICATION_EVENTS_TRANSIENT_COL = "transient"

        fun insertPushNotificationEvent(eventIndex: Int, pushId: String, isDecrypted: Boolean,
                                        eventJson: String, plain: ByteArray?, isTransient: Boolean,
                                        openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(PUSH_NOTIFICATION_EVENTS_EVENT_INDEX_COL, eventIndex)
                it.put(PUSH_NOTIFICATION_EVENTS_PUSH_ID_COL, pushId)
                it.put(PUSH_NOTIFICATION_EVENTS_DECRYPTED_COL, isDecrypted)
                it.put(PUSH_NOTIFICATION_EVENTS_EVENT_COL, eventJson)
                it.put(PUSH_NOTIFICATION_EVENTS_PLAIN_COL, plain)
                it.put(PUSH_NOTIFICATION_EVENTS_TRANSIENT_COL, isTransient)
            }

            openHelper.insertWithOnConflict(
                tableName = PUSH_NOTIFICATION_EVENTS_DATA_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

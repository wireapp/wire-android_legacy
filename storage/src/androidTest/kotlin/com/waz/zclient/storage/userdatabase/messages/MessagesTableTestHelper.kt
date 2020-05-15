package com.waz.zclient.storage.userdatabase.messages

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class MessagesTableTestHelper private constructor() {

    companion object {

        private const val MESSAGES_TABLE_NAME = "Messages"
        private const val MESSAGES_ID_COL = "_id"
        private const val MESSAGES_CONV_ID_COL = "conv_id"
        private const val MESSAGES_TYPE_COL = "msg_type"
        private const val MESSAGES_USER_ID_COL = "user_id"
        private const val MESSAGES_CONTENT_COL = "content"
        private const val MESSAGES_PROTOS_COL = "protos"
        private const val MESSAGES_TIME_COL = "time"
        private const val MESSAGES_LOCAL_TIME_COL = "local_time"
        private const val MESSAGES_FIRST_MESSAGE_COL = "first_msg"
        private const val MESSAGES_MEMEBERS_COL = "members"
        private const val MESSAGES_RECIPIENT_COL = "recipient"
        private const val MESSAGES_EMAIL_COL = "email"
        private const val MESSAGES_NAME_COL = "name"
        private const val MESSAGES_STATE_COL = "msg_state"
        private const val MESSAGES_CONTENT_SIZE_COL = "content_size"
        private const val MESSAGES_EDIT_TIME_COL = "edit_time"
        private const val MESSAGES_EPHEMERAL_COL = "ephemeral"
        private const val MESSAGES_EXPIRY_TIME_COL = "expiry_time"
        private const val MESSAGES_EXPIRED_COL = "expired"
        private const val MESSAGES_DURATION_COL = "duration"
        private const val MESSAGES_QUOTE_COL = "quote"
        private const val MESSAGES_QUOTE_VALIDITY_COL = "quote_validity"
        private const val MESSAGES_FORCE_READ_RECEIPTS_COL = "force_read_receipts"
        private const val MESSAGES_ASSET_ID_COL = "asset_id"

        fun insertMessage(id: String, conversationId: String, messageType: String, userId: String,
                          content: String?, protos: ByteArray?, time: Int, localTime: Int,
                          firstMessage: Boolean, members: String?, recipient: String?,
                          email: String?, name: String?, messageState: String, contentSize: Int,
                          editTime: Int, ephemeral: Int?, expiryTime: Int?, expired: Boolean,
                          duration: Int?, quote: String?, quoteValidity: Int, forceReadReceipts: Int?,
                          assetId: String?, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(MESSAGES_ID_COL, id)
                it.put(MESSAGES_CONV_ID_COL, conversationId)
                it.put(MESSAGES_TYPE_COL, messageType)
                it.put(MESSAGES_USER_ID_COL, userId)
                it.put(MESSAGES_CONTENT_COL, content)
                it.put(MESSAGES_PROTOS_COL, protos)
                it.put(MESSAGES_TIME_COL, time)
                it.put(MESSAGES_LOCAL_TIME_COL, localTime)
                it.put(MESSAGES_FIRST_MESSAGE_COL, firstMessage)
                it.put(MESSAGES_MEMEBERS_COL, members)
                it.put(MESSAGES_RECIPIENT_COL, recipient)
                it.put(MESSAGES_EMAIL_COL, email)
                it.put(MESSAGES_NAME_COL, name)
                it.put(MESSAGES_STATE_COL, messageState)
                it.put(MESSAGES_CONTENT_SIZE_COL, contentSize)
                it.put(MESSAGES_EDIT_TIME_COL, editTime)
                it.put(MESSAGES_EPHEMERAL_COL, ephemeral)
                it.put(MESSAGES_EXPIRY_TIME_COL, expiryTime)
                it.put(MESSAGES_EXPIRED_COL, expired)
                it.put(MESSAGES_DURATION_COL, duration)
                it.put(MESSAGES_QUOTE_COL, quote)
                it.put(MESSAGES_QUOTE_VALIDITY_COL, quoteValidity)
                it.put(MESSAGES_FORCE_READ_RECEIPTS_COL, forceReadReceipts)
                it.put(MESSAGES_ASSET_ID_COL, assetId)
            }

            openHelper.insertWithOnConflict(
                tableName = MESSAGES_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

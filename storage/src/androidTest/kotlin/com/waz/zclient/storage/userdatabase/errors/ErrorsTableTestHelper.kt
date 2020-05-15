package com.waz.zclient.storage.userdatabase.errors

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class ErrorsTableTestHelper private constructor() {

    companion object {

        private const val ERRORS_TABLE_NAME = "Errors"
        private const val ERRORS_ID_COL = "_id"
        private const val ERRORS_TYPE_COL = "err_type"
        private const val ERRORS_USERS_COL = "users"
        private const val ERRORS_MESSAGES_COL = "messages"
        private const val ERRORS_CONV_ID_COL = "conv_id"
        private const val ERRORS_RES_CODE_COL = "res_code"
        private const val ERRORS_RES_MESSAGE_COL = "res_msg"
        private const val ERRORS_RES_LABEL_COL = "res_label"
        private const val ERRORS_TIME_COL = "time"

        fun insertError(id: String, errorType: String, users: String, messages: String,
                        conversationId: String?, resCode: Int, resMessage: String, resLabel: String,
                        time: Int, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(ERRORS_ID_COL, id)
                it.put(ERRORS_TYPE_COL, errorType)
                it.put(ERRORS_USERS_COL, users)
                it.put(ERRORS_MESSAGES_COL, messages)
                it.put(ERRORS_CONV_ID_COL, conversationId)
                it.put(ERRORS_RES_CODE_COL, resCode)
                it.put(ERRORS_RES_MESSAGE_COL, resMessage)
                it.put(ERRORS_RES_LABEL_COL, resLabel)
                it.put(ERRORS_TIME_COL, time)
            }
            openHelper.insertWithOnConflict(
                tableName = ERRORS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

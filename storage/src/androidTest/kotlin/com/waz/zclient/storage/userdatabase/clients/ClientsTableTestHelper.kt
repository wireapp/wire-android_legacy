package com.waz.zclient.storage.userdatabase.clients

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class ClientsTableTestHelper private constructor() {

    companion object {

        private const val CLIENTS_TABLE_NAME = "Clients"
        private const val CLIENTS_ID_COL = "_id"
        private const val CLIENTS_DATA_COL = "data"

        fun insertClient(
            id: String,
            data: String,
            openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(CLIENTS_ID_COL, id)
                it.put(CLIENTS_DATA_COL, data)
            }
            openHelper.insertWithOnConflict(
                tableName = CLIENTS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

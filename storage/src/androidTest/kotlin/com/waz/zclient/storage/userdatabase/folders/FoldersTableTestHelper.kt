package com.waz.zclient.storage.userdatabase.folders

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class FoldersTableTestHelper private constructor() {

    companion object {

        private const val FOLDERS_TABLE_NAME = "Folders"
        private const val FOLDERS_ID_COL = "_id"
        private const val FOLDERS_NAME_COL = "name"
        private const val FOLDERS_TYPE_COL = "type"

        fun insertFolder(id: String, name: String, type: Int, openHelper: DbSQLiteOpenHelper) {
            val contentValues = ContentValues().also {
                it.put(FOLDERS_ID_COL, id)
                it.put(FOLDERS_NAME_COL, name)
                it.put(FOLDERS_TYPE_COL, type)
            }
            openHelper.insertWithOnConflict(
                tableName = FOLDERS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}

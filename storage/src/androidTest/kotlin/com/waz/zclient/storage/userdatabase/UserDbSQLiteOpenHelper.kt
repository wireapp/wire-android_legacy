package com.waz.zclient.storage.userdatabase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * This class is to merely tell the unit tests that an SQLiteDatabase existed before the room ones
 * without having to bridge the gap between Scala and Kotlin.
 */
class UserDbSQLiteOpenHelper(
    context: Context,
    name: String
) : SQLiteOpenHelper(context, name, null, 126) {

    override fun onCreate(db: SQLiteDatabase?) {
        //Not required for testing version 126 to 127
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing version 126 to 127
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing version 126 to 127
    }

}

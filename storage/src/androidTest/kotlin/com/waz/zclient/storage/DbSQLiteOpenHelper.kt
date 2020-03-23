package com.waz.zclient.storage


import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * This class is to merely tell the unit tests that an SQLiteDatabase existed before the room ones
 * without having to bridge the gap between Scala and Kotlin.
 */
class DbSQLiteOpenHelper(
    context: Context,
    name: String,
    version: Int
) : SQLiteOpenHelper(context, name, null, version) {

    override fun onCreate(db: SQLiteDatabase?) {
        //Not required for testing
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing
    }

    fun execSQL(sql: String) {
        writableDatabase.execSQL(sql)
    }

    fun insertWithOnConflict(
        tableName: String, nullColumnHack: String? = null,
        contentValues: ContentValues, conflictAlgorithm: Int = SQLiteDatabase.CONFLICT_REPLACE) {
        writableDatabase.insertWithOnConflict(
            tableName,
            nullColumnHack,
            contentValues,
            conflictAlgorithm
        )
    }
}

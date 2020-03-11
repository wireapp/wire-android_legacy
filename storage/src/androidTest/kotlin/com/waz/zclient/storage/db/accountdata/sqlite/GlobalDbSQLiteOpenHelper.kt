package com.waz.zclient.storage.db.accountdata.sqlite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * This class is to merely tell the unit tests that an SQLiteDatabase existed before the room ones
 * without having to bridge the gap between Scala and Kotlin.
 */
class GlobalDbSQLiteOpenHelper(
    context: Context,
    name: String
) : SQLiteOpenHelper(context, name, null, 24) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("""
            CREATE TABLE ActiveAccounts (_id TEXT PRIMARY KEY, team_id TEXT , cookie TEXT , access_token TEXT , registered_push TEXT , sso_id TEXT )
        """.trimIndent())
        db?.execSQL("""
            CREATE TABLE CacheEntry (key TEXT PRIMARY KEY, file TEXT , data BLOB , lastUsed INTEGER , timeout INTEGER , enc_key TEXT , path TEXT , mime TEXT , file_name TEXT , length INTEGER )
        """.trimIndent())
        db?.execSQL("""
            CREATE TABLE Teams (_id TEXT PRIMARY KEY, name TEXT , creator TEXT , icon TEXT )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing version 24 to 25
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing version 24 to 25
    }

}

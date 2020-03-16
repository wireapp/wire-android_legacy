package com.waz.zclient.storage.globaldb

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * This class is to merely tell the unit tests that an SQLiteDatabase existed before the room ones
 * without having to bridge the gap between Scala and Kotlin.
 */
class GlobalDbSQLiteOpenHelper(
    context: Context,
    name: String
) : SQLiteOpenHelper(context, name, null, 24) {

    override fun onCreate(db: SQLiteDatabase?) {
        //Not required for testing version 24 to 25
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing version 24 to 25
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //Not required for testing version 24 to 25
    }

}

class GlobalSQLiteDbTestHelper private constructor() {

    companion object {

        //ActiveAccount
        private const val ACTIVE_ACCOUNT_ID_COL = "_id"
        private const val ACTIVE_ACCOUNT_TEAM_ID_COL = "team_id"
        private const val ACTIVE_ACCOUNT_COOKIE_COL = "cookie"
        private const val ACTIVE_ACCOUNT_ACCESS_TOKEN_COL = "access_token"
        private const val ACTIVE_ACCOUNT_REGISTERED_PUSH_COL = "registered_push"
        private const val ACTIVE_ACCOUNT_SSO_ID_COL = "sso_id"
        private const val ACTIVE_ACCOUNTS_TABLE_NAME = "ActiveAccounts"

        //Teams
        private const val TEAMS_ID_COL = "_id"
        private const val TEAM_NAME_COL = "name"
        private const val TEAM_CREATOR_COL = "creator"
        private const val TEAM_ICON_ID = "icon"
        private const val TEAM_TABLE_NAME = "Teams"

        //CacheEntry
        private const val CACHE_ENTRY_ID_COL = "key"
        private const val CACHE_ENTRY_FILE_ID_COL = "file"
        private const val CACHE_ENTRY_DATA_COL = "data"
        private const val CACHE_ENTRY_LAST_USED_COL = "lastUsed"
        private const val CACHE_ENTRY_TIMEOUT_COL = "timeout"
        private const val CACHE_ENTRY_PATH_COL = "path"
        private const val CACHE_ENTRY_FILE_NAME_COL = "file_name"
        private const val CACHE_ENTRY_MIME_COL = "mime"
        private const val CACHE_ENTRY_ENC_KEY_COL = "enc_key"
        private const val CACHE_ENTRY_LENGTH_COL = "length"
        private const val CACHE_ENTRY_TABLE_NAME = "CacheEntry"

        fun insertActiveAccount(
            id: String,
            teamId: String?,
            cookie: String,
            accessToken: JSONObject,
            registeredPush: String,
            ssoId: String? = null,
            openHelper: GlobalDbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(ACTIVE_ACCOUNT_ID_COL, id)
                it.put(ACTIVE_ACCOUNT_TEAM_ID_COL, teamId)
                it.put(ACTIVE_ACCOUNT_COOKIE_COL, cookie)
                it.put(ACTIVE_ACCOUNT_ACCESS_TOKEN_COL, accessToken.toString())
                it.put(ACTIVE_ACCOUNT_REGISTERED_PUSH_COL, registeredPush)
                it.put(ACTIVE_ACCOUNT_SSO_ID_COL, ssoId)
            }

            with(openHelper.writableDatabase) {
                insertWithOnConflict(
                    ACTIVE_ACCOUNTS_TABLE_NAME,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                close()
            }
        }

        fun insertTeam(
            id: String,
            name: String,
            creator: String,
            icon: String,
            openHelper: GlobalDbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(TEAMS_ID_COL, id)
                it.put(TEAM_NAME_COL, name)
                it.put(TEAM_CREATOR_COL, creator)
                it.put(TEAM_ICON_ID, icon)
            }

            with(openHelper.writableDatabase) {
                insertWithOnConflict(
                    TEAM_TABLE_NAME,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                close()
            }
        }

        fun insertCacheEntry(
            id: String,
            fileId: String,
            lastUsed: Long,
            data: ByteArray? = null,
            timeout: Long,
            filePath: String? = null,
            fileName: String? = null,
            mime: String,
            encKey: String? = null,
            length: Long? = null,
            openHelper: GlobalDbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(CACHE_ENTRY_ID_COL, id)
                it.put(CACHE_ENTRY_FILE_ID_COL, fileId)
                it.put(CACHE_ENTRY_LAST_USED_COL, lastUsed)
                it.put(CACHE_ENTRY_DATA_COL, data)
                it.put(CACHE_ENTRY_TIMEOUT_COL, timeout)
                it.put(CACHE_ENTRY_PATH_COL, filePath)
                it.put(CACHE_ENTRY_FILE_NAME_COL, fileName)
                it.put(CACHE_ENTRY_MIME_COL, mime)
                it.put(CACHE_ENTRY_ENC_KEY_COL, encKey)
                it.put(CACHE_ENTRY_LENGTH_COL, length)
            }

            with(openHelper.writableDatabase) {
                insertWithOnConflict(
                    CACHE_ENTRY_TABLE_NAME,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                close()
            }
        }


        fun createTable(testOpenHelper: GlobalDbSQLiteOpenHelper) {
            with(testOpenHelper.writableDatabase) {
                execSQL("""
                    CREATE TABLE IF NOT EXISTS ActiveAccounts (_id TEXT PRIMARY KEY, team_id TEXT , cookie TEXT , access_token TEXT , registered_push TEXT , sso_id TEXT )
                """.trimIndent())
                execSQL("""
                    CREATE TABLE Teams (_id TEXT PRIMARY KEY, name TEXT , creator TEXT , icon TEXT )
                """.trimIndent())
                execSQL("""
                    CREATE TABLE CacheEntry (key TEXT PRIMARY KEY, file TEXT , data BLOB , lastUsed INTEGER , timeout INTEGER , enc_key TEXT , path TEXT , mime TEXT , file_name TEXT , length INTEGER )
                """.trimIndent())
                close()
            }
        }

        fun clearDatabase(testOpenHelper: GlobalDbSQLiteOpenHelper) {
            with(testOpenHelper.writableDatabase) {
                execSQL("DROP TABLE IF EXISTS ActiveAccounts")
                execSQL("DROP TABLE IF EXISTS Teams")
                execSQL("DROP TABLE IF EXISTS CacheEntry")
                close()
            }
        }
    }

}

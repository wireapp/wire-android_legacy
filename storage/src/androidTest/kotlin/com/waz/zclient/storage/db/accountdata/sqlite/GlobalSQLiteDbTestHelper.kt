package com.waz.zclient.storage.db.accountdata.sqlite

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

class GlobalSQLiteDbTestHelper private constructor() {

    companion object {

        private const val ACTIVE_ACCOUNT_ID_COL = "_id"
        private const val ACTIVE_ACCOUNT_TEAM_ID_COL = "team_id"
        private const val ACTIVE_ACCOUNT_COOKIE_COL = "cookie"
        private const val ACTIVE_ACCOUNT_ACCESS_TOKEN_COL = "access_token"
        private const val ACTIVE_ACCOUNT_REGISTERED_PUSH_COL = "registered_push"
        private const val ACTIVE_ACCOUNT_SSO_ID_COL = "sso_id"
        private const val ACTIVE_ACCOUNTS_TABLE_NAME = "ActiveAccounts"

        fun insertActiveAccount(
            id: String,
            teamId: String?,
            cookie: String,
            accessToken: JSONObject,
            registeredPush: String,
            ssoId: String? = null,
            openHelper: GlobalDbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().apply {
                put(ACTIVE_ACCOUNT_ID_COL, id)
                put(ACTIVE_ACCOUNT_TEAM_ID_COL, teamId)
                put(ACTIVE_ACCOUNT_COOKIE_COL, cookie)
                put(ACTIVE_ACCOUNT_ACCESS_TOKEN_COL, accessToken.toString())
                put(ACTIVE_ACCOUNT_REGISTERED_PUSH_COL, registeredPush)
                put(ACTIVE_ACCOUNT_SSO_ID_COL, ssoId)
            }

            with(openHelper.writableDatabase) {
                insertWithOnConflict(ACTIVE_ACCOUNTS_TABLE_NAME,
                    null, contentValues, SQLiteDatabase.CONFLICT_REPLACE)
                close()
            }
        }

        fun createTable(testOpenHelper: GlobalDbSQLiteOpenHelper) {
            with(testOpenHelper.writableDatabase) {
                execSQL("""
                    CREATE TABLE IF NOT EXISTS ActiveAccounts (_id TEXT PRIMARY KEY, team_id TEXT , cookie TEXT , access_token TEXT , registered_push TEXT , sso_id TEXT )
                """.trimIndent())
                close()
            }
        }

        fun clearDatabase(testOpenHelper: GlobalDbSQLiteOpenHelper) {
            val db = testOpenHelper.writableDatabase
            db.execSQL("DROP TABLE IF EXISTS ActiveAccounts")
            db.close()
        }
    }

}

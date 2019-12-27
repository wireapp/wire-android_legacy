package com.waz.zclient.storage.db.clients.migration

import android.content.ContentValues
import androidx.room.OnConflictStrategy
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

import kotlin.collections.*

import com.waz.zclient.storage.db.clients.migration.Option.*
import com.waz.zclient.storage.db.clients.migration.ContentKey.*
import com.waz.zclient.storage.db.clients.migration.ContentValue.*

class ClientsTableMigration : Migration(125, 126) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE '$NEW_CLIENT_TABLE_NAME' 
            ('${CLIENT_ID_KEY.str}' TEXT NOT NULL, 
             '${NEW_CLIENT_TIME_KEY.str}' NUMERIC, 
             '${CLIENT_LABEL_KEY.str}' TEXT, 
             '${NEW_CLIENT_TYPE_KEY.str}' TEXT, 
             '${CLIENT_MODEL_KEY.str}' TEXT, 
             '${CLIENT_LOCATION_LAT_KEY.str}' LONG, 
             '${CLIENT_LOCATION_LONG_KEY.str}' DOUBLE, 
             '${NEW_CLIENT_LOCATION_NAME_KEY.str}' TEXT, 
             '${CLIENT_VERIFICATION_KEY.str}' TEXT, 
             '${CLIENT_ENC_KEY.str}' TEXT, 
             '${CLIENT_MAC_KEY.str}' TEXT, 
             PRIMARY KEY('${CLIENT_ID_KEY.str}')
            )""".trimIndent())

        val clientsArray =
                JSONObject(
                        database.query("SELECT * FROM $OLD_CLIENT_TABLE_NAME")
                                .getString(DATA_COLUMN_INDEX)
                )
                .getJSONArray(OLD_CLIENTS_CLIENTS_ARRAY_KEY)

        for (i in 0 until clientsArray.length())
            database.insert(
                    NEW_CLIENT_TABLE_NAME,
                    OnConflictStrategy.REPLACE,
                    parseClientData(clientsArray.getJSONObject(i)).toContentValues()
            )

        database.execSQL("DROP TABLE $OLD_CLIENT_TABLE_NAME")
    }

    companion object {
        private const val NEW_CLIENT_TABLE_NAME = "client"
        private const val OLD_CLIENT_TABLE_NAME = "Clients"
        private const val DATA_COLUMN_INDEX = 1
        private const val OLD_CLIENTS_CLIENTS_ARRAY_KEY = "clients"

        private fun getString(json: JSONObject, key: ContentKey) =
                if (json.has(key.str)) Some(key to StringValue(json.getString(key.str))) else None

        private fun getDouble(json: JSONObject, key: ContentKey) =
                if (json.has(key.str)) Some(key to DoubleValue(json.getDouble(key.str))) else None

        private fun getLong(json: JSONObject, key: ContentKey) =
                if (json.has(key.str)) Some(key to LongValue(json.getLong(key.str))) else None

        private fun getJSONObject(json: JSONObject, key: ContentKey) =
                if (json.has(key.str)) Some(json.getJSONObject(key.str)) else None

        private fun parseClientData(client: JSONObject?) =
                Option.from(client).fold(::emptyMap) {
                    val main = listOf(
                            getString(it, CLIENT_ID_KEY),
                            getString(it, CLIENT_LABEL_KEY),
                            getString(it, CLIENT_MODEL_KEY),
                            getLong(it, OLD_CLIENT_REG_KEY),
                            getString(it, CLIENT_VERIFICATION_KEY),
                            getString(it, OLD_CLIENT_TYPE_KEY)
                    ).flatten()

                    val location = getJSONObject(it, OLD_CLIENT_LOCATION_KEY).fold(::emptyList) {
                        listOf(
                                getDouble(it, CLIENT_LOCATION_LAT_KEY),
                                getDouble(it, CLIENT_LOCATION_LONG_KEY),
                                getString(it, OLD_CLIENT_LOCATION_NAME_KEY)
                        )
                    }.flatten()

                    val signaling = getJSONObject(it, OLD_CLIENT_SIGNALING_KEY).fold(::emptyList) {
                        listOf(
                                getString(it, CLIENT_ENC_KEY),
                                getString(it, CLIENT_MAC_KEY)
                        )
                    }.flatten()

                    listOf(main, location, signaling).flatten().toMap()
                }
    }
}

fun Map<ContentKey, ContentValue>.toContentValues(): ContentValues {
    val contentValues = ContentValues()
    this.forEach {
        when(val value = it.value) {
            is StringValue -> contentValues.put(it.key.str, value.value)
            is DoubleValue -> contentValues.put(it.key.str, value.value)
            is LongValue   -> contentValues.put(it.key.str, value.value)
        }
    }
    return contentValues
}

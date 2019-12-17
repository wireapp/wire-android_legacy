package com.waz.zclient.storage.db.clients.migration

import android.content.ContentValues
import androidx.room.OnConflictStrategy
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

class ClientsTableMigration : Migration(125, 126) {

    override fun migrate(database: SupportSQLiteDatabase) {

        // "Clients" to "client" migration
        database.execSQL("CREATE TABLE '$NEW_CLIENT_TABLE_NAME' ('$CLIENT_ID_KEY' TEXT NOT NULL, '$NEW_CLIENT_TIME_KEY' NUMERIC, '$CLIENT_LABEL_KEY' TEXT, '$NEW_CLIENT_TYPE_KEY' TEXT, '$CLIENT_MODEL_KEY' TEXT, '$CLIENT_LOCATION_LAT_KEY' LONG, '$CLIENT_LOCATION_LONG_KEY' DOUBLE, '$NEW_CLIENT_LOCATION_NAME_KEY' TEXT, '$CLIENT_VERIFICATION_KEY' TEXT, '$CLIENT_ENC_KEY' TEXT, '$CLIENT_MAC_KEY' TEXT, PRIMARY KEY('$CLIENT_ID_KEY'))")

        val oldTableContent = database.query("SELECT * FROM $OLD_CLIENT_TABLE_NAME")
        val clientsData = oldTableContent.getString(DATA_COLUMN_INDEX)
        val dataObject = JSONObject(clientsData)
        val clientsArray = dataObject.getJSONArray(OLD_CLIENTS_CLIENTS_ARRAY_KEY)
        for (i in 0 until clientsArray.length()) {
            val values = parseClientData(clientsArray.getJSONObject(i))
            database.insert(NEW_CLIENT_TABLE_NAME, OnConflictStrategy.REPLACE, values)
        }
        database.execSQL("DROP TABLE $OLD_CLIENT_TABLE_NAME")

    }

    private fun parseClientData(client: JSONObject?): ContentValues {
        val contentValues = ContentValues()
        client?.apply {
            if (has(CLIENT_ID_KEY)) {
                val clientId = getString(CLIENT_ID_KEY)
                contentValues.put(CLIENT_ID_KEY, clientId)
            }

            if (has(CLIENT_LABEL_KEY)) {
                val clientLabel = getString(CLIENT_LABEL_KEY)
                contentValues.put(CLIENT_LABEL_KEY, clientLabel)
            }

            if (has(CLIENT_MODEL_KEY)) {
                val clientModel = getString(CLIENT_MODEL_KEY)
                contentValues.put(CLIENT_MODEL_KEY, clientModel)
            }

            if (has(OLD_CLIENT_REG_KEY)) {
                val clientTime = getLong(OLD_CLIENT_REG_KEY)
                contentValues.put(NEW_CLIENT_TIME_KEY, clientTime)
            }

            if (has(OLD_CLIENT_LOCATION_KEY)) {
                val clientLocation = getJSONObject(OLD_CLIENT_LOCATION_KEY)

                if (clientLocation.has(CLIENT_LOCATION_LAT_KEY)) {
                    val clientLocationLat = clientLocation.getDouble(CLIENT_LOCATION_LAT_KEY)
                    contentValues.put(CLIENT_LOCATION_LAT_KEY, clientLocationLat)
                }

                if (clientLocation.has(CLIENT_LOCATION_LONG_KEY)) {
                    val clientLocationLong = clientLocation.getDouble(CLIENT_LOCATION_LONG_KEY)
                    contentValues.put(CLIENT_LOCATION_LONG_KEY, clientLocationLong)
                }

                if (clientLocation.has(OLD_CLIENT_LOCATION_NAME_KEY)) {
                    val clientLocationName = clientLocation.getString(OLD_CLIENT_LOCATION_NAME_KEY)
                    contentValues.put(NEW_CLIENT_LOCATION_NAME_KEY, clientLocationName)
                }
            }

            if (has(OLD_CLIENT_SIGNALING_KEY)) {
                val clientSignalingData = getJSONObject(OLD_CLIENT_SIGNALING_KEY)

                if (clientSignalingData.has(CLIENT_ENC_KEY)) {
                    val clientEncKey = clientSignalingData.getString(CLIENT_ENC_KEY)
                    contentValues.put(CLIENT_ENC_KEY, clientEncKey)
                }

                if (clientSignalingData.has(CLIENT_MAC_KEY)) {
                    val clientMacKey = clientSignalingData.getString(CLIENT_MAC_KEY)
                    contentValues.put(CLIENT_MAC_KEY, clientMacKey)
                }
            }

            if (has(CLIENT_VERIFICATION_KEY)) {
                val clientVerification = getString(CLIENT_VERIFICATION_KEY)
                contentValues.put(CLIENT_VERIFICATION_KEY, clientVerification)
            }

            if (has(OLD_CLIENT_TYPE_KEY)) {
                val clientType = getString(OLD_CLIENT_TYPE_KEY)
                contentValues.put(NEW_CLIENT_TYPE_KEY, clientType)
            }
        }
        return contentValues
    }

    companion object {
        //Shared keys
        private const val CLIENT_ID_KEY = "id"
        private const val CLIENT_LABEL_KEY = "label"
        private const val CLIENT_LOCATION_LAT_KEY = "lat"
        private const val CLIENT_LOCATION_LONG_KEY = "lon"
        private const val CLIENT_ENC_KEY = "encKey"
        private const val CLIENT_MAC_KEY = "macKey"
        private const val CLIENT_VERIFICATION_KEY = "verification"
        private const val CLIENT_MODEL_KEY = "model"

        //New table keys
        private const val NEW_CLIENT_TABLE_NAME = "client"
        private const val NEW_CLIENT_LOCATION_NAME_KEY = "locationName"
        private const val NEW_CLIENT_TIME_KEY = "time"
        private const val NEW_CLIENT_TYPE_KEY = "type"

        //Old table keys
        private const val OLD_CLIENT_TABLE_NAME = "Clients"
        private const val DATA_COLUMN_INDEX = 1
        private const val OLD_CLIENTS_CLIENTS_ARRAY_KEY = "clients"
        private const val OLD_CLIENT_REG_KEY = "regTime"
        private const val OLD_CLIENT_LOCATION_KEY = "regLocation"
        private const val OLD_CLIENT_LOCATION_NAME_KEY = "name"
        private const val OLD_CLIENT_SIGNALING_KEY = "signalingKey"
        private const val OLD_CLIENT_TYPE_KEY = "devType"
    }
}

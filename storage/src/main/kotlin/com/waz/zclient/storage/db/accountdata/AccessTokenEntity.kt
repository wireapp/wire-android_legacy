package com.waz.zclient.storage.db.accountdata

import androidx.room.TypeConverter
import org.json.JSONException
import org.json.JSONObject

data class AccessTokenEntity(
    val token: String,
    val tokenType: String,
    val expiresInMillis: Long
)

class AccessTokenConverter {
    @TypeConverter
    fun fromStringToAccessToken(tokenString: String?): AccessTokenEntity? = tokenString?.let {
        try {
            val json = JSONObject(it)
            AccessTokenEntity(
                token = json.getString(KEY_TOKEN),
                tokenType = json.getString(KEY_TOKEN_TYPE),
                expiresInMillis = json.getLong(KEY_EXPIRY)
            )
        } catch (e: JSONException) {
            null
        }
    }

    @TypeConverter
    fun accessTokenToString(entity: AccessTokenEntity?): String? =
        entity?.let {
            """
            {
                "$KEY_TOKEN": "${it.token}",
                "$KEY_TOKEN_TYPE": "${it.tokenType}",
                "$KEY_EXPIRY": ${it.expiresInMillis}
            }
        """.trimIndent()
        }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_TYPE = "type"
        private const val KEY_EXPIRY = "expires"
    }
}

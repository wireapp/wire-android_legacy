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
    fun fromStringToAccessToken(tokenString: String): AccessTokenEntity? =
        try {
            val json = JSONObject(tokenString)
            AccessTokenEntity(
                token = json.getString("token"),
                tokenType = json.getString("type"),
                expiresInMillis = json.getLong("expires")
            )
        } catch (e: JSONException) {
            null
        }

    @TypeConverter
    fun accessTokenToString(entity: AccessTokenEntity): String =
        """
            {
                "token": "${entity.token}",
                "type": "${entity.tokenType}",
                "expires": ${entity.expiresInMillis}
            }
        """.trimIndent()
}

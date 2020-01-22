package com.waz.zclient.storage.db.accountdata

import androidx.room.TypeConverter
import org.json.JSONException
import org.json.JSONObject

data class SsoIdEntity(
    val subject: String,
    val tenant: String
)

class SsoIdConverter {

    @TypeConverter
    fun fromStringToSsoId(tokenString: String): SsoIdEntity? =
        try {
            val json = JSONObject(tokenString)
            SsoIdEntity(
                subject = json.getString("subject"),
                tenant = json.getString("tenant")
            )
        } catch (e: JSONException) {
            null
        }

    @TypeConverter
    fun ssoIdToString(entity: SsoIdEntity): String =
        """
            {
                "subject": "${entity.subject}",
                "tenant": "${entity.tenant}"
            }
        """.trimIndent()
}

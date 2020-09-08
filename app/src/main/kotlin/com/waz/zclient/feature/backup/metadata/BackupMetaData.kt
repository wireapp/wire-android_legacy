package com.waz.zclient.feature.backup.metadata

import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.empty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.threeten.bp.Instant
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class BackupMetaData(
    val platform: String = "Android",
    @SerialName("user_id") val userId: String = String.empty(),
    val version: String = Config.versionName(),
    @SerialName("creation_time") val creationTime: String = timeNow(),
    @SerialName("client_id") val clientId: String = String.empty(),
    // Android specific data
    val userHandle: String = String.empty(),
    val backUpVersion: Int = 0
) {
    companion object BackupMetaData {
        private fun timeNow(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Instant.now().toEpochMilli())
    }
}

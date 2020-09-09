package com.waz.zclient.feature.backup.metadata

import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.utilities.DateAndTimeUtils.instantToString
import com.waz.zclient.core.utilities.DatePattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupMetaData(
    val platform: String = "Android",
    @SerialName("user_id") val userId: String = String.empty(),
    val version: String = Config.versionName(),
    @SerialName("creation_time") val creationTime: String = instantToString(pattern = DatePattern.DATE_TIME_ISO8601),
    @SerialName("client_id") val clientId: String = String.empty(),
    // Android specific data
    val userHandle: String = String.empty(),
    val backUpVersion: Int = 0
)

package com.waz.zclient.feature.backup.metadata

import com.waz.zclient.core.extension.empty
import kotlinx.serialization.Serializable

@Serializable
data class BackupMetaData(
    val userId: String = String.empty(),
    val userHandle: String = String.empty(),
    val backUpVersion: Int = 0
)

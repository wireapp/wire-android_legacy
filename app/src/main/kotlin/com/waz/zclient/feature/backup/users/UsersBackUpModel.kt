package com.waz.zclient.feature.backup.users

import com.waz.zclient.core.extension.empty
import kotlinx.serialization.Serializable

@Serializable
data class UsersBackUpModel(
    val id: String,
    val domain: String?,
    val teamId: String? = null,
    val name: String = String.empty(),
    val email: String? = null,
    val phone: String? = null,
    val trackingId: String? = null,
    val picture: String? = null,
    val accentId: Int = 0,
    val sKey: String = String.empty(),
    val connection: String = String.empty(),
    val connectionTimestamp: Long = 0,
    val connectionMessage: String? = null,
    val conversation: String? = null,
    val relation: String = String.empty(),
    val timestamp: Long? = null,
    val verified: String? = null,
    val deleted: Boolean = false,
    val availability: Int = 0,
    val handle: String? = null,
    val providerId: String? = null,
    val integrationId: String? = null,
    val expiresAt: Long? = null,
    val managedBy: String? = null,
    val selfPermission: Int = 0,
    val copyPermission: Int = 0,
    val createdBy: String? = null
)

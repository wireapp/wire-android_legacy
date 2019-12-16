package com.waz.zclient.user.domain.model


data class User(
    val id: String,
    val teamId: String?,
    val name: String,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val trackingId: String?,
    var pictures: String?,
    var picture: List<String>? = listOf(),
    val accentId: Int?,
    val sKey: String?,
    val connection: String?,
    val connectionTimestamp: Long?,
    val connectionMessage: String?,
    val conversation: String?,
    val relation: String?,
    val timestamp: Long?,
    val displayName: String?,
    val verified: String?,
    val deleted: Int,
    val availability: Int?,
    val providerId: String?,
    val integrationId: String?,
    val expiresAt: Int?,
    val managedBy: String?,
    val selfPermission: Int?,
    val copyPermission: Int?,
    val createdBy: String?)
)

package com.waz.zclient.framework.data.users

import com.waz.zclient.framework.data.TestDataProvider
import java.util.UUID

data class UsersTestData(
    val id: String,
    val domain: String,
    val teamId: String,
    val name: String,
    val email: String,
    val phone: String,
    val trackingId: String,
    val picture: String,
    val accentId: Int,
    val sKey: String,
    val connection: String,
    val connectionTimestamp: Long,
    val connectionMessage: String,
    val conversation: String,
    val conversationDomain: String,
    val relation: String,
    val timestamp: Long,
    val verified: String,
    val deleted: Boolean,
    val availability: Int,
    val handle: String,
    val providerId: String,
    val integrationId: String,
    val expiresAt: Long,
    val managedBy: String,
    val selfPermission: Int,
    val copyPermission: Int,
    val createdBy: String
)

object UsersTestDataProvider : TestDataProvider<UsersTestData>() {
    override fun provideDummyTestData(): UsersTestData =
        UsersTestData(
            id = UUID.randomUUID().toString(),
            domain = "staging.zinfra.io",
            teamId = UUID.randomUUID().toString(),
            name = "name",
            email = "email@email.com",
            phone = "0123456789",
            trackingId = UUID.randomUUID().toString(),
            picture = UUID.randomUUID().toString(),
            accentId = 0,
            sKey = "key",
            connection = "0",
            connectionTimestamp = 0,
            connectionMessage = "connectionMessage",
            conversation = UUID.randomUUID().toString(),
            conversationDomain = "staging.zinfra.io",
            relation = "0",
            timestamp = System.currentTimeMillis(),
            verified = "",
            deleted = false,
            availability = 0,
            handle = "handle",
            providerId = UUID.randomUUID().toString(),
            integrationId = UUID.randomUUID().toString(),
            expiresAt = 0,
            managedBy = "",
            selfPermission = 0,
            copyPermission = 0,
            createdBy = ""
        )
}

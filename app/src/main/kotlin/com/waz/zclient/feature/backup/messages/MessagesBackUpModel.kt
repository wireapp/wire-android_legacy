package com.waz.zclient.feature.backup.messages

import kotlinx.serialization.Serializable

@Serializable
data class MessagesBackUpModel(
    val id: String,
    val conversationId: String,
    val messageType: String,
    val userId: String,
    val content: String?,
    val protos: ByteArray?, //TODO override equals/hashCode if necessary
    val time: Int,
    val firstMessage: Boolean,
    val members: String?,
    val recipient: String?,
    val email: String?,
    val name: String?,
    val messageState: String,
    val contentSize: Int,
    val localTime: Int,
    val editTime: Int,
    val ephemeral: Int?,
    val expiryTime: Int?,
    val expired: Boolean,
    val duration: Int?,
    val quote: String?,
    val quoteValidity: Int,
    val forceReadReceipts: Int?,
    val assetId: String?
)

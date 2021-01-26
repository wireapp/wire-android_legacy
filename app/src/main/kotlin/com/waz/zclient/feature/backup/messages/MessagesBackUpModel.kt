package com.waz.zclient.feature.backup.messages

import com.waz.zclient.core.extension.empty
import kotlinx.serialization.Serializable

@Serializable
data class MessagesBackUpModel(
    val id: String,
    val conversationId: String = String.empty(),
    val messageType: String = String.empty(),
    val userId: String = String.empty(),
    val clientId: String? = null,
    val errorCode: Long? = null,
    val content: String? = null,
    val protos: ByteArray? = null,
    val time: Long = 0,
    val firstMessage: Boolean = false,
    val members: String? = null,
    val recipient: String? = null,
    val email: String? = null,
    val name: String? = null,
    val messageState: String = String.empty(),
    val contentSize: Int = 0,
    val localTime: Long = 0,
    val editTime: Long = 0,
    val ephemeral: Long? = null,
    val expiryTime: Long? = null,
    val expired: Boolean = false,
    val duration: Long? = null,
    val quote: String? = null,
    val quoteValidity: Int = 0,
    val forceReadReceipts: Int? = null,
    val assetId: String? = null
)

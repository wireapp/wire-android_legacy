package com.waz.zclient.framework.data.messages

import com.waz.zclient.framework.data.TestDataProvider

data class MessageTestData(
    val id: String,
    val conversationId: String,
    val messageType: String,
    val userId: String,
    val content: String?,
    val protos: ByteArray?,
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

object MessagesTestDataProvider : TestDataProvider<MessageTestData>() {
    override fun data(): MessageTestData = MessageTestData(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        conversationId = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        messageType = "ConnectRequest",
        userId = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        content = "[{\"type\":\"TextEmojiOnly\",\"content\":\" \"}]",
        protos = ByteArray(PROTOS_BYTE_SIZE) { it.toByte() },
        time = 0,
        firstMessage = false,
        members = null,
        recipient = "2f9e89c9-78a7-477d-8def-fbd7ca3846b5",
        email = null,
        name = "",
        messageState = "SENT",
        contentSize = 1,
        localTime = 0,
        editTime = 0,
        ephemeral = null,
        expiryTime = null,
        expired = false,
        duration = null,
        quote = null,
        quoteValidity = 0,
        forceReadReceipts = null,
        assetId = null
    )

    const val PROTOS_BYTE_SIZE = 256
}

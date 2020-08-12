package com.waz.zclient.framework.data.conversations

import com.waz.zclient.framework.data.TestDataProvider

data class ConversationMembersTestData(
    val userId: String,
    val conversationId: String,
    val role: String
)

object ConversationMembersTestDataProvider : TestDataProvider<ConversationMembersTestData>() {
    override fun provideDummyTestData(): ConversationMembersTestData =
        ConversationMembersTestData(
            userId = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
            conversationId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
            role = "admin"
        )
}

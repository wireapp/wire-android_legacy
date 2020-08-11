package com.waz.zclient.framework.data.conversations

import com.waz.zclient.framework.data.TestDataProvider

data class ConversationRoleActionTestData(
    val label: String,
    val action: String,
    val convId: String
)

object ConversationRolesTestDataProvider : TestDataProvider<ConversationRoleActionTestData>() {
    override fun provideDummyTestData() = ConversationRoleActionTestData(
        label = "Join",
        action = "JOINED",
        convId = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a"
    )
}

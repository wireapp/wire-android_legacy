package com.waz.zclient.framework.data.conversations

import com.waz.zclient.framework.data.TestDataProvider

data class ConversationFoldersTestData(
    val convId: String,
    val folderId: String
)

object ConversationFoldersTestDataProvider : TestDataProvider<ConversationFoldersTestData>() {
    override fun provideDummyTestData(): ConversationFoldersTestData =
        ConversationFoldersTestData(
            convId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
            folderId = "1"
        )
}

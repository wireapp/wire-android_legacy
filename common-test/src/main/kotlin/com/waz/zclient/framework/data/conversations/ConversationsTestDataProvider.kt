package com.waz.zclient.framework.data.conversations

import com.waz.zclient.framework.data.TestDataProvider

data class ConversationsTestData(
    val id: String,
    val remoteId: String,
    val name: String?,
    val creator: String,
    val conversationType: Int,
    val team: String?,
    val managed: Boolean?,
    val lastEventTime: Int,
    val active: Boolean,
    val lastRead: Int,
    val mutedStatus: Int,
    val muteTime: Int,
    val archived: Boolean,
    val archiveTime: Int,
    val cleared: Int?,
    val generatedName: String,
    val searchKey: String?,
    val unreadCount: Int,
    val unsentCount: Int,
    val hidden: Boolean,
    val missedCall: String?,
    val incomingKnock: String?,
    val verified: String?,
    val ephemeral: Int?,
    val globalEphemeral: Int?,
    val unreadCallCount: Int,
    val unreadPingCount: Int,
    val access: String?,
    val accessRole: String?,
    val link: String?,
    val unreadMentionsCount: Int,
    val unreadQuoteCount: Int,
    val receiptMode: Int?
)

object ConversationsTestDataProvider : TestDataProvider<ConversationsTestData>() {
    override fun provideDummyTestData(): ConversationsTestData = ConversationsTestData(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        remoteId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        name = null,
        creator = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        conversationType = 0,
        team = null,
        managed = false,
        lastEventTime = 0,
        active = true,
        lastRead = 0,
        mutedStatus = 0,
        muteTime = 0,
        archived = false,
        archiveTime = 0,
        cleared = null,
        generatedName = "",
        searchKey = null,
        unreadCount = 0,
        unsentCount = 0,
        hidden = false,
        missedCall = null,
        incomingKnock = null,
        verified = null,
        ephemeral = null,
        globalEphemeral = null,
        unreadCallCount = 0,
        unreadPingCount = 0,
        access = null,
        accessRole = null,
        link = null,
        unreadMentionsCount = 0,
        unreadQuoteCount = 0,
        receiptMode = null
    )
}

package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.conversations.ConversationsDao
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import kotlinx.serialization.Serializable

class ConversationsLocalDataSource(private val conversationsDao: ConversationsDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<ConversationsEntity, ConversationsJSONEntity>(ConversationsJSONEntity.serializer(), batchSize) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<ConversationsEntity> =
        conversationsDao.getConversationsInBatch(batchSize, offset)

    override fun toJSON(entity: ConversationsEntity): ConversationsJSONEntity = ConversationsJSONEntity.from(entity)
    override fun toEntity(json: ConversationsJSONEntity): ConversationsEntity = json.toEntity()
}

@Serializable
data class ConversationsJSONEntity(
    val id: String,
    val remoteId: String = "",
    val name: String? = null,
    val creator: String = "",
    val conversationType: Int = 0,
    val team: String? = null,
    val managed: Boolean? = null,
    val lastEventTime: Int = 0,
    val active: Boolean = false,
    val lastRead: Int = 0,
    val mutedStatus: Int = 0,
    val muteTime: Int = 0,
    val archived: Boolean = false,
    val archiveTime: Int = 0,
    val cleared: Int? = null,
    val generatedName: String = "",
    val searchKey: String? = null,
    val unreadCount: Int = 0,
    val unsentCount: Int = 0,
    val hidden: Boolean = false,
    val missedCall: String? = null,
    val incomingKnock: String? = null,
    val verified: String? = null,
    val ephemeral: Int? = null,
    val globalEphemeral: Int? = null,
    val unreadCallCount: Int = 0,
    val unreadPingCount: Int = 0,
    val access: String? = null,
    val accessRole: String? = null,
    val link: String? = null,
    val unreadMentionsCount: Int = 0,
    val unreadQuoteCount: Int = 0,
    val receiptMode: Int? = null
) {
    fun toEntity(): ConversationsEntity = ConversationsEntity(
        id = id,
        remoteId = remoteId,
        name = name,
        creator = creator,
        conversationType = conversationType,
        team = team,
        managed = managed,
        lastEventTime = lastEventTime,
        active = active,
        lastRead = lastRead,
        mutedStatus = mutedStatus,
        muteTime = muteTime,
        archived = archived,
        archiveTime = archiveTime,
        cleared = cleared,
        generatedName = generatedName,
        searchKey = searchKey,
        unreadCount = unreadCount,
        unsentCount = unsentCount,
        hidden = hidden,
        missedCall = missedCall,
        incomingKnock = incomingKnock,
        verified = verified,
        ephemeral = ephemeral,
        globalEphemeral = globalEphemeral,
        unreadCallCount = unreadCallCount,
        unreadPingCount = unreadPingCount,
        access = access,
        accessRole = accessRole,
        link = link,
        unreadMentionsCount = unreadMentionsCount,
        unreadQuoteCount = unreadQuoteCount,
        receiptMode = receiptMode
    )

    companion object {
        fun from(entity: ConversationsEntity): ConversationsJSONEntity = ConversationsJSONEntity(
            id = entity.id,
            remoteId = entity.remoteId,
            name = entity.name,
            creator = entity.creator,
            conversationType = entity.conversationType,
            team = entity.team,
            managed = entity.managed,
            lastEventTime = entity.lastEventTime,
            active = entity.active,
            lastRead = entity.lastRead,
            mutedStatus = entity.mutedStatus,
            muteTime = entity.muteTime,
            archived = entity.archived,
            archiveTime = entity.archiveTime,
            cleared = entity.cleared,
            generatedName = entity.generatedName,
            searchKey = entity.searchKey,
            unreadCount = entity.unreadCount,
            unsentCount = entity.unsentCount,
            hidden = entity.hidden,
            missedCall = entity.missedCall,
            incomingKnock = entity.incomingKnock,
            verified = entity.verified,
            ephemeral = entity.ephemeral,
            globalEphemeral = entity.globalEphemeral,
            unreadCallCount = entity.unreadCallCount,
            unreadPingCount = entity.unreadPingCount,
            access = entity.access,
            accessRole = entity.accessRole,
            link = entity.link,
            unreadMentionsCount = entity.unreadMentionsCount,
            unreadQuoteCount = entity.unreadQuoteCount,
            receiptMode = entity.receiptMode
        )
    }
}

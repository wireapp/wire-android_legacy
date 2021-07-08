package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ConversationsBackUpModel(
    val id: String,
    val remoteId: String = String.empty(),
    val name: String? = null,
    val creator: String = String.empty(),
    val conversationType: Int = 0,
    val team: String? = null,
    val managed: Boolean? = null,
    val lastEventTime: Long = 0,
    val active: Boolean = false,
    val lastRead: Long = 0,
    val mutedStatus: Int = 0,
    val muteTime: Long = 0,
    val archived: Boolean = false,
    val archiveTime: Long = 0,
    val cleared: Long? = null,
    val generatedName: String = String.empty(),
    val searchKey: String? = null,
    val unreadCount: Int = 0,
    val unsentCount: Int = 0,
    val hidden: Boolean = false,
    val missedCall: String? = null,
    val incomingKnock: String? = null,
    val verified: String? = null,
    val ephemeral: Long? = null,
    val globalEphemeral: Long? = null,
    val unreadCallCount: Int = 0,
    val unreadPingCount: Int = 0,
    val access: String? = null,
    val accessRole: String? = null,
    val link: String? = null,
    val unreadMentionsCount: Int = 0,
    val unreadQuoteCount: Int = 0,
    val receiptMode: Int? = null,
    val legalHoldStatus: Int = 0,
    val domain: String? = null
)

class ConversationsBackupMapper : BackUpDataMapper<ConversationsBackUpModel, ConversationsEntity> {
    override fun fromEntity(entity: ConversationsEntity) = ConversationsBackUpModel(
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
        receiptMode = entity.receiptMode,
        legalHoldStatus = entity.legalHoldStatus,
        domain = entity.domain
    )

    override fun toEntity(model: ConversationsBackUpModel) = ConversationsEntity(
        id = model.id,
        remoteId = model.remoteId,
        name = model.name,
        creator = model.creator,
        conversationType = model.conversationType,
        team = model.team,
        managed = model.managed,
        lastEventTime = model.lastEventTime,
        active = model.active,
        lastRead = model.lastRead,
        mutedStatus = model.mutedStatus,
        muteTime = model.muteTime,
        archived = model.archived,
        archiveTime = model.archiveTime,
        cleared = model.cleared,
        generatedName = model.generatedName,
        searchKey = model.searchKey,
        unreadCount = model.unreadCount,
        unsentCount = model.unsentCount,
        hidden = model.hidden,
        missedCall = model.missedCall,
        incomingKnock = model.incomingKnock,
        verified = model.verified,
        ephemeral = model.ephemeral,
        globalEphemeral = model.globalEphemeral,
        unreadCallCount = model.unreadCallCount,
        unreadPingCount = model.unreadPingCount,
        access = model.access,
        accessRole = model.accessRole,
        link = model.link,
        unreadMentionsCount = model.unreadMentionsCount,
        unreadQuoteCount = model.unreadQuoteCount,
        receiptMode = model.receiptMode,
        legalHoldStatus = model.legalHoldStatus,
        domain = model.domain
    )
}

class ConversationsBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<ConversationsEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<ConversationsBackUpModel, File>,
    override val mapper: BackUpDataMapper<ConversationsBackUpModel, ConversationsEntity>
) : BackUpDataSource<ConversationsBackUpModel, ConversationsEntity>()

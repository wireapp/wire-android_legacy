package com.waz.zclient.storage.userdatabase.conversations

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ConversationTables126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenFolderInsertedIntoConversationFoldersTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val conversationId = "7577489"
        val folderId = "377474"

        ConversationFoldersTableTestHelper.insertConversationFolder(
            conversationId,
            folderId,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allConversationFolders()[0]) {
                assert(this.convId == conversationId)
                assert(this.folderId == folderId)
            }
        }
    }

    @Test
    fun givenMemberInsertedIntoConversationMembersTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val userId = "h477474849jfnj777478-"
        val convId = "7577489"
        val roleId = "1100"

        ConversationMembersTableTestHelper.insertConversationMember(
            userId,
            convId,
            roleId,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allConversationMembers()[0]) {
                assert(this.userId == userId)
                assert(this.conversationId == convId)
                assert(this.role == roleId)
            }
        }
    }

    @Test
    fun givenRoleActionInsertedIntoConversationRoleActionTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val convId = "7577489"
        val label = "Join"
        val action = "JOINED"

        ConversatonsRoleActionTableTestHelper.insertConversationRoleAction(
            convId,
            label,
            action,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allConversationRoleActions()[0]) {
                assert(this.convId == convId)
                assert(this.label == label)
                assert(this.action == action)
            }
        }
    }

    @Test
    fun givenRoleActionInsertedIntoConversationsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val convId = "7577489"
        val remoteId = "888"
        val name = "Test Conversation Name"
        val creator = "h477474849jfnj777478-"
        val conversationType = 1 // Group
        val team = "7477749KKY888"
        val managed = false
        val lastEventTime = 124778833
        val active = false
        val lastRead = 1188777
        val mutedStatus = 0 // false
        val muteTime = 0 //none
        val archived = true
        val archiveTime = 124778833
        val cleared = 0 // false
        val generatedName = name
        val searchKey = convId
        val unreadCount = 12
        val unsentCount = 0
        val hidden = true
        val missedCall = "false"
        val incomingKnock = "false"
        val verified = "true"
        val ephemeral = 200
        val globalEphemeral = 200
        val unreadCallCount = 1
        val unreadPingCount = 2
        val access = "Admin"
        val accessRole = "1100"
        val unreadMentionCount = 12
        val unreadQuoteCount = 0
        val receiptMode = 2 // On

        ConversationsTableTestHelper.insertConversation(
            convId,
            remoteId,
            name,
            creator,
            conversationType,
            team,
            managed,
            lastEventTime,
            active,
            lastRead,
            mutedStatus,
            muteTime,
            archived,
            archiveTime,
            cleared,
            generatedName,
            searchKey,
            unreadCount,
            unsentCount,
            hidden,
            missedCall,
            incomingKnock,
            verified,
            ephemeral,
            globalEphemeral,
            unreadCallCount,
            unreadPingCount,
            access,
            accessRole,
            null,
            unreadMentionCount,
            unreadQuoteCount,
            receiptMode,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allConversations()[0]) {
                assert(this.id == convId)
                assert(this.remoteId == remoteId)
                assert(this.name == name)
                assert(this.creator == creator)
                assert(this.conversationType == conversationType)
                assert(this.team == team)
                assert(this.managed == managed)
                assert(this.lastEventTime == lastEventTime)
                assert(this.active == active)
                assert(this.lastRead == lastRead)
                assert(this.mutedStatus == mutedStatus)
                assert(this.muteTime == muteTime)
                assert(this.archived == archived)
                assert(this.archiveTime == archiveTime)
                assert(this.cleared == cleared)
                assert(this.generatedName == generatedName)
                assert(this.searchKey == searchKey)
                assert(this.unreadCount == unreadCount)
                assert(this.unsentCount == unsentCount)
                assert(this.hidden == hidden)
                assert(this.missedCall == missedCall)
                assert(this.incomingKnock == incomingKnock)
                assert(this.verified == verified)
                assert(this.ephemeral == ephemeral)
                assert(this.globalEphemeral == globalEphemeral)
                assert(this.unreadCallCount == unreadCallCount)
                assert(this.unreadPingCount == unreadPingCount)
                assert(this.access == access)
                assert(this.accessRole == accessRole)
                assert(this.link == null)
                assert(this.unreadMentionsCount == unreadMentionsCount)
                assert(this.unreadQuoteCount == unreadQuoteCount)
                assert(this.receiptMode == receiptMode)
            }
        }
    }

    private suspend fun allConversationFolders() =
        getDatabase().conversationFoldersDao().allConversationFolders()

    private suspend fun allConversationMembers() =
        getDatabase().conversationMembersDao().allConversationMembers()

    private suspend fun allConversationRoleActions() =
        getDatabase().conversationRoleActionDao().allConversationRoleActions()

    private suspend fun allConversations() =
        getDatabase().conversationsDao().allConversations()
}

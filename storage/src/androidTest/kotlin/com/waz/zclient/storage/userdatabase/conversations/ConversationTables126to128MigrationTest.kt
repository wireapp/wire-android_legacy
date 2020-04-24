package com.waz.zclient.storage.userdatabase.conversations

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
                assertEquals(this.convId, conversationId)
                assertEquals(this.folderId, folderId)
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
                assertEquals(this.userId, userId)
                assertEquals(this.conversationId, convId)
                assertEquals(this.role, roleId)
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
                assertEquals(this.convId, convId)
                assertEquals(this.label, label)
                assertEquals(this.action, action)
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
                assertEquals(this.id, convId)
                assertEquals(this.remoteId, remoteId)
                assertEquals(this.name, name)
                assertEquals(this.creator, creator)
                assertEquals(this.conversationType, conversationType)
                assertEquals(this.team, team)
                assertEquals(this.managed, managed)
                assertEquals(this.lastEventTime, lastEventTime)
                assertEquals(this.active, active)
                assertEquals(this.lastRead, lastRead)
                assertEquals(this.mutedStatus, mutedStatus)
                assertEquals(this.muteTime, muteTime)
                assertEquals(this.archived, archived)
                assertEquals(this.archiveTime, archiveTime)
                assertEquals(this.cleared, cleared)
                assertEquals(this.generatedName, generatedName)
                assertEquals(this.searchKey, searchKey)
                assertEquals(this.unreadCount, unreadCount)
                assertEquals(this.unsentCount, unsentCount)
                assertEquals(this.hidden, hidden)
                assertEquals(this.missedCall, missedCall)
                assertEquals(this.incomingKnock, incomingKnock)
                assertEquals(this.verified, verified)
                assertEquals(this.ephemeral, ephemeral)
                assertEquals(this.globalEphemeral, globalEphemeral)
                assertEquals(this.unreadCallCount, unreadCallCount)
                assertEquals(this.unreadPingCount, unreadPingCount)
                assertEquals(this.access, access)
                assertEquals(this.accessRole, accessRole)
                assertEquals(this.link, null)
                assertEquals(this.unreadMentionsCount, unreadMentionsCount)
                assertEquals(this.unreadQuoteCount, unreadQuoteCount)
                assertEquals(this.receiptMode, receiptMode)
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

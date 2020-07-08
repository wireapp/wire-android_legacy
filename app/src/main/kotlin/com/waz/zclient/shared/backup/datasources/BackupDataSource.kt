package com.waz.zclient.shared.backup.datasources

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.Password
import com.waz.zclient.shared.backup.datasources.local.*

class BackupDataSource(
     private val assetLocalDataSource: AssetLocalDataSource,
     private val buttonLocalDataSource: ButtonLocalDataSource,
     private val conversationFoldersLocalDataSource: ConversationFoldersLocalDataSource,
     private val conversationLocalDataSource: ConversationLocalDataSource,
     private val conversationMemberLocalDataSource: ConversationMemberLocalDataSource,
     private val conversationRoleActionLocalDataSource: ConversationRoleActionLocalDataSource,
     private val foldersLocalDataSource: FoldersLocalDataSource,
     private val keyValueLocalDataSource: KeyValueLocalDataSource,
     private val likesLocalDataSource: LikesLocalDataSource,
     private val messagesLocalDataSource: MessagesLocalDataSource,
     private val propertiesLocalDataSource: PropertiesLocalDataSource,
     private val readReceiptsLocalDataSource: ReadReceiptsLocalDataSource,
     private val userLocalDataSource: UserLocalDataSource
): BackupRepository {
    override suspend fun exportDatabase(userId: UserId, userHandle: Handle, backupPassword: Password) {
        TODO("Not yet implemented")
    }

}
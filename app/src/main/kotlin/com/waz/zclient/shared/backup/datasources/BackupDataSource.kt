package com.waz.zclient.shared.backup.datasources

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.Password
import com.waz.zclient.shared.backup.datasources.local.*
import java.io.File

class BackupDataSource(
        private val assetLocalDataSource: AssetLocalDataSource,
        private val buttonLocalDataSource: ButtonLocalDataSource,
        private val conversationFoldersLocalDataSource: ConversationFoldersLocalDataSource,
        private val conversationLocalDataSource: ConversationsLocalDataSource,
        private val conversationMemberLocalDataSource: ConversationMemberLocalDataSource,
        private val conversationRoleActionLocalDataSource: ConversationRoleActionLocalDataSource,
        private val foldersLocalDataSource: FoldersLocalDataSource,
        private val keyValueLocalDataSource: KeyValuesLocalDataSource,
        private val likesLocalDataSource: LikesLocalDataSource,
        private val messagesLocalDataSource: MessagesLocalDataSource,
        private val propertiesLocalDataSource: PropertiesLocalDataSource,
        private val readReceiptsLocalDataSource: ReadReceiptsLocalDataSource,
        private val userLocalDataSource: UserLocalDataSource
): BackupRepository {
    override suspend fun exportDatabase(userId: UserId, userHandle: Handle, backupPassword: Password, targetDir: File): Either<String, File> {
        TODO("Not yet implemented")
    }
}

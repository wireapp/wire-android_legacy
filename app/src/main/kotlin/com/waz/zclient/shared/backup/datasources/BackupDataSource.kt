package com.waz.zclient.shared.backup.datasources

import com.waz.model.Handle
import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Utils.returning
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.utilities.UiUtils.writeTextToFile
import com.waz.zclient.core.utilities.mapOrFail
import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.datasources.local.AssetLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ButtonLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationFoldersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationsLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationMembersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationRoleActionLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.FoldersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.KeyValuesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.LikesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.MessagesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.PropertiesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ReadReceiptsLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.UserLocalDataSource

import java.io.File

class BackupDataSource(
    assetLocalDataSource: AssetLocalDataSource,
    buttonLocalDataSource: ButtonLocalDataSource,
    conversationFoldersLocalDataSource: ConversationFoldersLocalDataSource,
    conversationLocalDataSource: ConversationsLocalDataSource,
    conversationMembersLocalDataSource: ConversationMembersLocalDataSource,
    conversationRoleActionLocalDataSource: ConversationRoleActionLocalDataSource,
    foldersLocalDataSource: FoldersLocalDataSource,
    keyValueLocalDataSource: KeyValuesLocalDataSource,
    likesLocalDataSource: LikesLocalDataSource,
    messagesLocalDataSource: MessagesLocalDataSource,
    propertiesLocalDataSource: PropertiesLocalDataSource,
    readReceiptsLocalDataSource: ReadReceiptsLocalDataSource,
    usersLocalDataSource: UserLocalDataSource
) : BackupRepository {
    override suspend fun exportDatabase(userId: UserId, userHandle: Handle, backupPassword: String, targetDir: File): Either<String, File> {
        TODO("Not yet implemented")
    }

    private val sources = mapOf(
        "assets" to assetLocalDataSource,
        "buttons" to buttonLocalDataSource,
        "conversationFolders" to conversationFoldersLocalDataSource,
        "conversations" to conversationLocalDataSource,
        "conversationMembers" to conversationMembersLocalDataSource,
        "conversationRoleActions" to conversationRoleActionLocalDataSource,
        "folders" to foldersLocalDataSource,
        "keyValues" to keyValueLocalDataSource,
        "likes" to likesLocalDataSource,
        "messages" to messagesLocalDataSource,
        "properties" to propertiesLocalDataSource,
        "readReceipts" to readReceiptsLocalDataSource,
        "users" to usersLocalDataSource
    )

    private fun writeAllToFiles(targetDir: File) =
        sources.toList().fold(Either.Right(emptyList())) {
            acc: Either<Failure, List<File>>, pair: Pair<String, BackupLocalDataSource<out Any, out Any>> ->
            acc.flatMap { files ->
                writeToFiles(targetDir, pair.first, pair.second).map { listOf(files, it).flatten() }
            }
        }

    private fun <Entity, JSON> writeToFiles(
        targetDir: File,
        fileNamePrefix: String,
        dataSource: BackupLocalDataSource<Entity, JSON>
    ): Either<Failure, List<File>> {
        var index = 0

        return dataSource.mapOrFail {
            returning(writeTextToFile(targetDir, "${fileNamePrefix}_$index.json") { it }) { ++index }
        }
    }
}

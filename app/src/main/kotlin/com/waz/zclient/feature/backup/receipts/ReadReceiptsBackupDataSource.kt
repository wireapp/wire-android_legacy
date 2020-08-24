package com.waz.zclient.feature.backup.receipts

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ReadReceiptsBackUpModel(
    val messageId: String = String.empty(),
    val userId: String = String.empty(),
    val timestamp: Long = 0
)

class ReadReceiptsBackupMapper : BackUpDataMapper<ReadReceiptsBackUpModel, ReadReceiptsEntity> {
    override fun fromEntity(entity: ReadReceiptsEntity) = ReadReceiptsBackUpModel(
        messageId = entity.messageId,
        userId = entity.userId,
        timestamp = entity.timestamp
    )

    override fun toEntity(model: ReadReceiptsBackUpModel) = ReadReceiptsEntity(
        messageId = model.messageId,
        userId = model.userId,
        timestamp = model.timestamp
    )
}

class ReadReceiptsBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<ReadReceiptsEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<ReadReceiptsBackUpModel, File>,
    override val mapper: BackUpDataMapper<ReadReceiptsBackUpModel, ReadReceiptsEntity>
) : BackUpDataSource<ReadReceiptsBackUpModel, ReadReceiptsEntity>()

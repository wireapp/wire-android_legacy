package com.waz.zclient.feature.backup.assets

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.assets.AssetsEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AssetsBackUpModel(
    val id: String,
    val token: String? = null,
    val name: String = String.empty(),
    val encryption: String = String.empty(),
    val mime: String = String.empty(),
    val sha: ByteArray? = null,
    val size: Int = 0,
    val source: String? = null,
    val preview: String? = null,
    val details: String = String.empty(),
    val conversationId: String? = null
)

class AssetsBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<AssetsEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<AssetsBackUpModel, File>,
    override val mapper: BackUpDataMapper<AssetsBackUpModel, AssetsEntity>
) : BackUpDataSource<AssetsBackUpModel, AssetsEntity>()

class AssetsBackupMapper : BackUpDataMapper<AssetsBackUpModel, AssetsEntity> {
    override fun fromEntity(entity: AssetsEntity) = AssetsBackUpModel(
        id = entity.id,
        token = entity.token,
        name = entity.name,
        encryption = entity.encryption,
        mime = entity.mime,
        sha = entity.sha,
        size = entity.size,
        source = entity.source,
        preview = entity.preview,
        details = entity.details
    )

    override fun toEntity(model: AssetsBackUpModel) = AssetsEntity(
        id = model.id,
        token = model.token,
        name = model.name,
        encryption = model.encryption,
        mime = model.mime,
        sha = model.sha,
        size = model.size,
        source = model.source,
        preview = model.preview,
        details = model.details
    )
}

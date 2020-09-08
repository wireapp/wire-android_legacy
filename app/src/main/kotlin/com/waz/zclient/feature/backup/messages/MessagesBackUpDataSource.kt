package com.waz.zclient.feature.backup.messages

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.messages.MessagesEntity
import java.io.File

class MessagesBackUpDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<MessagesEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<MessagesBackUpModel, File>,
    override val mapper: BackUpDataMapper<MessagesBackUpModel, MessagesEntity>
) : BackUpDataSource<MessagesBackUpModel, MessagesEntity>()

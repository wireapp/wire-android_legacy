package com.waz.zclient.feature.backup.messages

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.messages.MessagesEntity

class MessagesBackUpDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<MessagesEntity>,
    override val backUpLocalDataSource: BackUpIOHandler<MessagesBackUpModel>,
    override val mapper: BackUpDataMapper<MessagesBackUpModel, MessagesEntity>
) : BackUpDataSource<MessagesBackUpModel, MessagesEntity>()

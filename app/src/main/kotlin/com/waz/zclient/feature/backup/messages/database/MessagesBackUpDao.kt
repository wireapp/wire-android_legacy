package com.waz.zclient.feature.backup.messages.database

import com.waz.zclient.feature.backup.io.database.BatchReadableDao
import com.waz.zclient.storage.db.messages.MessagesDao
import com.waz.zclient.storage.db.messages.MessagesEntity

class MessagesBackUpDao(private val messagesDao: MessagesDao) : BatchReadableDao<MessagesEntity> {

    override suspend fun count(): Int = messagesDao.size()

    override suspend fun nextBatch(start: Int, batchSize: Int): List<MessagesEntity>? =
        messagesDao.batch(start, batchSize)

    override suspend fun insert(item: MessagesEntity) = messagesDao.insert(item)
}

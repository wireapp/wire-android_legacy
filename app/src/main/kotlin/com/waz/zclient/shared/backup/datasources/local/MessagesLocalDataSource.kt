package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.messages.MessagesDao
import com.waz.zclient.storage.db.messages.MessagesEntity

class MessagesLocalDataSource(private val messagesDao: MessagesDao) {
    suspend fun getAllMessages(): List<MessagesEntity> = messagesDao.allMessages()
}
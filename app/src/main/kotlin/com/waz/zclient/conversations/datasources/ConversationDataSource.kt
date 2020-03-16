package com.waz.zclient.conversations.datasources

import com.waz.zclient.conversations.ConversationsRepository
import com.waz.zclient.conversations.datasources.remote.ConversationsRemoteDataSource

class ConversationDataSource(private val remoteDataSource: ConversationsRemoteDataSource)
    : ConversationsRepository {

    override suspend fun joinConversation(convId: String) =
        remoteDataSource.joinConversation(convId)
}


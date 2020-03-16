package com.waz.zclient.conversations.datasources.remote

class ConversationsRemoteDataSource(private val conversationsService: ConversationsService) {

    suspend fun joinConversation(convId: String) = conversationsService.joinConversation(convId)
}

package com.waz.zclient.conversations.datasources.remote

import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class ConversationsService(override val networkHandler: NetworkHandler,
                           private val conversationsApi: ConversationsApi) : ApiService() {

    suspend fun joinConversation(convId: String) =
        request { conversationsApi.joinConversation(convId) }

}

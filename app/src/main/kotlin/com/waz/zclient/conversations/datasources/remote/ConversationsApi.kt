package com.waz.zclient.conversations.datasources.remote

import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Path

interface ConversationsApi {

    @POST("/conversations/{cnv}/join")
    suspend fun joinConversation(@Path("cnv") cnv: String): Response<Unit>
}

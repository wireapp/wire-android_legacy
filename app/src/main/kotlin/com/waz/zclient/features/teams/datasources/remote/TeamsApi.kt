package com.waz.zclient.features.teams.datasources.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface TeamsApi {

    @GET("/teams/{tid}/conversations")
    suspend fun getAllTeamConversations(@Path("tid") tid: String): Response<TeamsConversationListResponse>

}

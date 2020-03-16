package com.waz.zclient.features.teams.datasources.remote

import com.google.gson.annotations.SerializedName


data class TeamsConversationListResponse(
    @SerializedName("conversations")
    val conversations: List<TeamsConversationResponse>
)

data class TeamsConversationResponse(
    @SerializedName("conversation")
    val conversationId: String,

    @SerializedName("managed")
    val managed: Boolean
)

package com.waz.zclient.features.teams.conversations.mapper

import com.waz.zclient.features.teams.conversations.TeamConversation
import com.waz.zclient.features.teams.conversations.list.TeamConversationItem
import com.waz.zclient.features.teams.datasources.remote.TeamsConversationResponse

class TeamsConversationMapper {

    fun toTeamConversation(teamsResponse: TeamsConversationResponse) = TeamConversation(
        title = teamsResponse.conversationId
    )

    fun toTeamConversationItem(teamConversation: TeamConversation) = TeamConversationItem(
        title = teamConversation.title
    )

}

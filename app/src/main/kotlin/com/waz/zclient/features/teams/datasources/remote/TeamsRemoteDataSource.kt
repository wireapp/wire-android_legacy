package com.waz.zclient.features.teams.datasources.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

class TeamsRemoteDataSource(private val teamsService: TeamsService) {

    suspend fun getAllTeamConversations(teamId: String): Either<Failure, List<TeamsConversationResponse>> =
        teamsService.getAllTeamConversation(teamId)
}

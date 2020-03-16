package com.waz.zclient.features.teams.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.features.teams.TeamsRepository
import com.waz.zclient.features.teams.conversations.TeamConversation


object InvalidTeamId : TeamConversationError()
sealed class TeamConversationError : FeatureFailure()

class GetAllTeamConversationsUseCase(
    private val teamsRepository: TeamsRepository
) : UseCase<List<TeamConversation>, GetAllTeamConversationsParams>() {

    override suspend fun run(params: GetAllTeamConversationsParams): Either<Failure, List<TeamConversation>> =
        params.teamId?.let {
            teamsRepository.getAllTeamConversations(it) }
            ?: Either.Left(InvalidTeamId)
}

data class GetAllTeamConversationsParams(val teamId: String?)

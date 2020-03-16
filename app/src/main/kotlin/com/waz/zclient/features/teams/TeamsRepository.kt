package com.waz.zclient.features.teams

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.features.teams.conversations.TeamConversation

interface TeamsRepository {

    suspend fun getAllTeamConversations(teamId: String): Either<Failure, List<TeamConversation>>
}

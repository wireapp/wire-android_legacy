package com.waz.zclient.features.teams.datasources

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.features.teams.TeamsRepository
import com.waz.zclient.features.teams.conversations.TeamConversation
import com.waz.zclient.features.teams.conversations.mapper.TeamsConversationMapper
import com.waz.zclient.features.teams.datasources.remote.TeamsRemoteDataSource

class TeamsDataSource(
    private val remoteDataSource: TeamsRemoteDataSource,
    private val teamsConversationMapper: TeamsConversationMapper
) : TeamsRepository {

    override suspend fun getAllTeamConversations(teamId: String): Either<Failure, List<TeamConversation>> =
        remoteDataSource.getAllTeamConversations(teamId).map { teamConversations ->
            teamConversations.map {
                teamsConversationMapper.toTeamConversation(it)
            }
        }
}

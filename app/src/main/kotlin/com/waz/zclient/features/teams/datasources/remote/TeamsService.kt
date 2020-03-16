package com.waz.zclient.features.teams.datasources.remote

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class TeamsService(override val networkHandler: NetworkHandler,
                   private val teamsApi: TeamsApi) : ApiService() {

    suspend fun getAllTeamConversation(teamId: String) =
        request { teamsApi.getAllTeamConversations(teamId) }.flatMap {
            Either.Right(it.conversations)
        }

}

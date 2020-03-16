package com.waz.zclient.features.teams.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.features.teams.TeamsRepository
import com.waz.zclient.features.teams.conversations.TeamsConversationsViewModel
import com.waz.zclient.features.teams.conversations.mapper.TeamsConversationMapper
import com.waz.zclient.features.teams.datasources.TeamsDataSource
import com.waz.zclient.features.teams.datasources.remote.TeamsApi
import com.waz.zclient.features.teams.datasources.remote.TeamsRemoteDataSource
import com.waz.zclient.features.teams.datasources.remote.TeamsService
import com.waz.zclient.features.teams.usecase.GetAllTeamConversationsUseCase
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module


const val TEAMS_SCOPE_ID = "teamsScopeId"
const val TEAMS_SCOPE = "teamsScope"

val teamsModule: Module = module {
    scope(named(TEAMS_SCOPE)) {
        viewModel { TeamsConversationsViewModel(get(), get(), get(), get()) }

        factory { GetAllTeamConversationsUseCase(get()) }
        factory { TeamsDataSource(get(), get()) as TeamsRepository }
        factory { TeamsRemoteDataSource(get()) }
        factory { TeamsConversationMapper() }
        factory { TeamsService(get(), get()) }
        factory { get<NetworkClient>().create(TeamsApi::class.java) }
    }
}

package com.waz.zclient.conversations.di

import com.waz.zclient.conversations.ConversationsRepository
import com.waz.zclient.conversations.datasources.ConversationDataSource
import com.waz.zclient.conversations.datasources.remote.ConversationsApi
import com.waz.zclient.conversations.datasources.remote.ConversationsRemoteDataSource
import com.waz.zclient.conversations.datasources.remote.ConversationsService
import com.waz.zclient.conversations.usecase.JoinConversationUseCase
import com.waz.zclient.core.network.NetworkClient
import org.koin.core.module.Module
import org.koin.dsl.module

val conversationsModule: Module = module {
    factory { ConversationDataSource(get()) as ConversationsRepository }
    factory { ConversationsRemoteDataSource(get()) }
    factory { ConversationsService(get(), get()) }
    factory { get<NetworkClient>().create(ConversationsApi::class.java) }
    factory { JoinConversationUseCase(get()) }

}

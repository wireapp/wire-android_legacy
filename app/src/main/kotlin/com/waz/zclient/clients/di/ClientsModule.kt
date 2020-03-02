package com.waz.zclient.clients.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.clients.data.ClientsDataSource
import com.waz.zclient.clients.data.ClientsRepository
import com.waz.zclient.clients.data.source.ClientMapper
import com.waz.zclient.clients.data.source.local.ClientsLocalDataSource
import com.waz.zclient.clients.data.source.remote.ClientsApi
import com.waz.zclient.clients.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.storage.db.UserDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val clientsModule: Module = module {
    single { ClientsDataSource(get(), get(), get()) as ClientsRepository }
    factory { ClientMapper() }
    factory { get<NetworkClient>().create(ClientsApi::class.java) }
    factory { ClientsRemoteDataSource(get(), get()) }
    factory { ClientsLocalDataSource(get()) }
    factory { get<UserDatabase>().clientsDbService() }
}

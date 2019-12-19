package com.waz.zclient.devices.di

import com.waz.zclient.core.network.Network
import com.waz.zclient.devices.data.ClientsDataSource
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.data.source.ClientMapper
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsNetworkService
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.storage.db.UserDatabase
import org.koin.core.module.Module
import org.koin.dsl.module


val clientsModule: Module = module {
    single { ClientsDataSource(get(), get(), get()) as ClientsRepository }
    factory { ClientMapper() }
    factory { ClientsRemoteDataSource(get()) }
    factory { ClientsLocalDataSource(get()) }
    factory { Network().networkClient().create(ClientsNetworkService::class.java) }
    factory { get<UserDatabase>().clientsDbService() }
}

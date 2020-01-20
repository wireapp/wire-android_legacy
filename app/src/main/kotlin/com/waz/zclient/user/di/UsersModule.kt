package com.waz.zclient.user.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.user.data.UsersDataSource
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.data.mapper.UserMapper
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersNetworkService
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.module.Module
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val usersModule: Module = module {
    single { UsersDataSource(get(), get(), get()) as UsersRepository }
    factory { UserMapper() }
    factory { UsersRemoteDataSource(get(), get()) }
    factory { UsersLocalDataSource(get(), get()) }
    factory { get<NetworkClient>().create(UsersNetworkService::class.java) }
    factory { get<UserDatabase>().userDbService() }
    factory { get<UserDatabase>().userPreferencesDbService() }
}

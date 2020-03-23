package com.waz.zclient.user.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.user.UsersRepository
import com.waz.zclient.user.datasources.UsersDataSource
import com.waz.zclient.user.datasources.local.UsersLocalDataSource
import com.waz.zclient.user.datasources.remote.UsersApi
import com.waz.zclient.user.datasources.remote.UsersRemoteDataSource
import com.waz.zclient.user.handle.UserHandleDataSource
import com.waz.zclient.user.handle.UserHandleRepository
import com.waz.zclient.user.mapper.UserMapper
import com.waz.zclient.user.phonenumber.PhoneNumberDataSource
import com.waz.zclient.user.phonenumber.PhoneNumberRepository
import com.waz.zclient.user.profile.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.module.Module
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val usersModule: Module = module {
    //TODO keep slimming down UserDataSource when more use cases come in
    single { UsersDataSource(get(), get(), get()) as UsersRepository }
    single { PhoneNumberDataSource(get(), get()) as PhoneNumberRepository }
    single { UserHandleDataSource(get(), get()) as UserHandleRepository }

    factory { UserMapper() }
    factory { UsersRemoteDataSource(get(), get()) }
    factory { UsersLocalDataSource(get(), get()) }
    factory { get<NetworkClient>().create(UsersApi::class.java) }
    factory { get<UserDatabase>().userDbService() }
    factory { get<UserDatabase>().keyValuesDao() }

    factory { GetUserProfileUseCase(get()) }
}

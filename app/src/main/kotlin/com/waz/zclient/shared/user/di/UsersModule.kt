package com.waz.zclient.shared.user.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.shared.user.UsersRepository
import com.waz.zclient.shared.user.datasources.UsersDataSource
import com.waz.zclient.shared.user.datasources.local.UsersLocalDataSource
import com.waz.zclient.shared.user.datasources.remote.UsersApi
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource
import com.waz.zclient.shared.user.email.ValidateEmailUseCase
import com.waz.zclient.shared.user.handle.UserHandleDataSource
import com.waz.zclient.shared.user.handle.UserHandleRepository
import com.waz.zclient.shared.user.mapper.UserMapper
import com.waz.zclient.shared.user.name.ValidateNameUseCase
import com.waz.zclient.shared.user.password.ValidatePasswordUseCase
import com.waz.zclient.shared.user.phonenumber.PhoneNumberDataSource
import com.waz.zclient.shared.user.phonenumber.PhoneNumberRepository
import com.waz.zclient.shared.user.profile.GetUserProfilePictureUseCase
import com.waz.zclient.shared.user.profile.GetUserProfileUseCase
import com.waz.zclient.shared.user.profile.ProfilePictureMapper
import com.waz.zclient.storage.db.UserDatabase
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
    factory { get<UserDatabase>().userDao() }
    factory { get<UserDatabase>().keyValuesDao() }

    factory { GetUserProfileUseCase(get()) }
    factory { ProfilePictureMapper() }
    factory { GetUserProfilePictureUseCase(get(), get()) }

    factory { ValidateEmailUseCase() }
    factory { ValidateNameUseCase() }
    factory { ValidatePasswordUseCase() }
}

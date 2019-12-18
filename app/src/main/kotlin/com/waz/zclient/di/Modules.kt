package com.waz.zclient.di

import androidx.room.Room
import com.waz.zclient.core.network.Network
import com.waz.zclient.devices.data.ClientsDataSource
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.data.source.ClientMapper
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsNetworkService
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetClientUseCase
import com.waz.zclient.settings.account.SettingsAccountViewModel
import com.waz.zclient.settings.devices.detail.SettingsDeviceDetailViewModel
import com.waz.zclient.settings.devices.list.SettingsDeviceListViewModel
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.UserDatabaseMigration
import com.waz.zclient.storage.pref.GlobalPreferences
import com.waz.zclient.user.data.UsersDataSource
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.data.mapper.UserMapper
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersNetworkService
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.usecase.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.ChangePhoneUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val viewModelModule: Module = module {
    viewModel { SettingsAccountViewModel(get()) }
    viewModel { SettingsDeviceListViewModel(get()) }
    viewModel { SettingsDeviceDetailViewModel(get()) }
}

val useCaseModule: Module = module {
    factory { GetUserProfileUseCase(get()) }
    factory { ChangePhoneUseCase(get()) }
    factory { ChangeHandleUseCase(get()) }
    factory { GetAllClientsUseCase(get()) }
    factory { GetClientUseCase(get()) }
}

val repositoryModule: Module = module {
    single { UsersDataSource(get(), get(), get()) as UsersRepository }
    single { ClientsDataSource(get(), get(), get()) as ClientsRepository }
}

val mapperModule: Module = module {
    single { UserMapper() }
    single { ClientMapper() }
}

val dataSourceModule: Module = module {
    single { UsersRemoteDataSource(get()) }
    single { UsersLocalDataSource(get(), get()) }
    single { ClientsRemoteDataSource(get()) }
    single { ClientsLocalDataSource(get()) }
}

val networkModule: Module = module {
    single { Network().networkClient().create(UsersNetworkService::class.java) }
    single { Network().networkClient().create(ClientsNetworkService::class.java) }

}

val cacheModule: Module = module {
    single { GlobalPreferences(androidContext()) }
    single {
        Room.databaseBuilder(androidContext(),
            UserDatabase::class.java, GlobalPreferences(androidContext()).activeUserId)
            .addMigrations(UserDatabaseMigration()).build()
    }
    single { get<UserDatabase>().userDbService() }
    single { get<UserDatabase>().clientsDbService() }
    single { get<UserDatabase>().userPreferencesDbService() }
}



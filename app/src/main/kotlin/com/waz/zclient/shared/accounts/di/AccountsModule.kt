package com.waz.zclient.shared.accounts.di

import com.waz.zclient.shared.accounts.AccountMapper
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.accounts.datasources.AccountsDataSource
import com.waz.zclient.shared.accounts.datasources.local.AccountsLocalDataSource
import com.waz.zclient.shared.accounts.datasources.remote.AccountsRemoteDataSource
import com.waz.zclient.shared.accounts.usecase.GetActiveAccountUseCase
import com.waz.zclient.storage.db.GlobalDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val accountsModule: Module = module {
    single { AccountsDataSource(get(), get(), get(), get()) as AccountsRepository }
    factory { AccountsLocalDataSource(get<GlobalDatabase>().activeAccountsDao()) }
    factory { AccountsRemoteDataSource(get(), get()) }
    factory { AccountMapper() }
    factory { GetActiveAccountUseCase(get(), get()) }
}

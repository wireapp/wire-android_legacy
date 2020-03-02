package com.waz.zclient.accounts.di

import com.waz.zclient.accounts.datasources.AccountsDataSource
import com.waz.zclient.accounts.datasources.local.AccountsLocalDataSource
import com.waz.zclient.accounts.AccountsRepository
import com.waz.zclient.accounts.AccountMapper
import com.waz.zclient.accounts.usecase.GetActiveAccountUseCase
import com.waz.zclient.storage.db.GlobalDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val accountsModule: Module = module {
    single { AccountsDataSource(get(), get(), get()) as AccountsRepository }
    factory { AccountsLocalDataSource(get<GlobalDatabase>().activeAccountsDao()) }
    factory { AccountMapper() }
    factory { GetActiveAccountUseCase(get(), get()) }
}

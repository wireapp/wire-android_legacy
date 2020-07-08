package com.waz.zclient.shared.backup.di

import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.datasources.BackupDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

val backupModule: Module = module {
    single { BackupDataSource(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) as BackupRepository }
}
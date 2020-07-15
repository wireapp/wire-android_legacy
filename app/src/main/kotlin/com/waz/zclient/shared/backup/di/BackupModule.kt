package com.waz.zclient.shared.backup.di

import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.datasources.BackupDataSource
import com.waz.zclient.shared.backup.datasources.local.*
import org.koin.core.module.Module
import org.koin.dsl.module

val backupModule: Module = module {
    single { BackupDataSource(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) as BackupRepository }
    factory { AssetLocalDataSource(get()) }
    factory { ButtonLocalDataSource(get()) }
    factory { ConversationFoldersLocalDataSource(get()) }
    factory { ConversationMembersLocalDataSource(get()) }
    factory { ConversationRoleActionLocalDataSource(get()) }
    factory { ConversationsLocalDataSource(get()) }
    factory { FoldersLocalDataSource(get()) }
    factory { KeyValuesLocalDataSource(get()) }
    factory { LikesLocalDataSource(get()) }
    factory { MessagesLocalDataSource(get()) }
    factory { PropertiesLocalDataSource(get()) }
    factory { ReadReceiptsLocalDataSource(get()) }
    factory { UserLocalDataSource(get()) }
}

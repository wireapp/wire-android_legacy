package com.waz.zclient.shared.backup.di

import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.datasources.BackupDataSource
import org.koin.core.module.Module
import org.koin.dsl.module
import com.waz.zclient.shared.backup.datasources.local.AssetLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ButtonLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationFoldersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationsLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationMembersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationRoleActionLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.FoldersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.KeyValuesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.LikesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.MessagesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.PropertiesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ReadReceiptsLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.UserLocalDataSource

val backupModule: Module = module {
    single {
        BackupDataSource(
            get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get()
        ) as BackupRepository
    }
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

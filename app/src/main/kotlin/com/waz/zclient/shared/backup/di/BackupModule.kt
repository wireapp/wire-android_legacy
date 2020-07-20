package com.waz.zclient.shared.backup.di

import com.waz.zclient.shared.backup.BackupRepository
import com.waz.zclient.shared.backup.datasources.local.AssetsLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ButtonLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationFoldersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationMembersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationRoleActionLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ConversationsLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.FoldersLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.KeyValuesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.LikesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.MessagesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.PropertiesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.ReadReceiptsLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.UsersLocalDataSource
import com.waz.zclient.shared.backup.datasources.BackupDataSource

import org.koin.core.module.Module
import org.koin.dsl.module

val backupModule: Module = module {
    single { BackupDataSource(get()) as BackupRepository }

    factory {
        listOf<BackupLocalDataSource<out Any, out Any>>(
            AssetsLocalDataSource(get()),
            ButtonLocalDataSource(get()),
            ConversationFoldersLocalDataSource(get()),
            ConversationMembersLocalDataSource(get()),
            ConversationRoleActionLocalDataSource(get()),
            ConversationsLocalDataSource(get()),
            FoldersLocalDataSource(get()),
            KeyValuesLocalDataSource(get()),
            LikesLocalDataSource(get()),
            MessagesLocalDataSource(get()),
            PropertiesLocalDataSource(get()),
            ReadReceiptsLocalDataSource(get()),
            UsersLocalDataSource(get())
        )
    }

    factory { AssetsLocalDataSource(get()) }
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
    factory { UsersLocalDataSource(get()) }
}

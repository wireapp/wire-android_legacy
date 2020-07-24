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
import com.waz.zclient.shared.backup.handlers.LibSodiumEncryption
import com.waz.zclient.shared.backup.handlers.LibSodiumEncryptionImpl
import com.waz.zclient.shared.backup.handlers.EncryptionHandler
import com.waz.zclient.shared.backup.handlers.EncryptionHandlerImpl
import com.waz.zclient.shared.backup.handlers.ZipBackupHandler
import com.waz.zclient.shared.backup.handlers.ZipBackupHandlerImpl
import com.waz.zclient.shared.backup.usecase.BackupUseCase

import org.koin.core.module.Module
import org.koin.dsl.module

val backupModule: Module = module {
    single { BackupDataSource(get()) as BackupRepository }
    single { ZipBackupHandlerImpl() as ZipBackupHandler }
    single { EncryptionHandlerImpl(get()) as EncryptionHandler }
    single { LibSodiumEncryptionImpl() as LibSodiumEncryption }

    single { BackupUseCase(get(), get(), get()) }

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
}

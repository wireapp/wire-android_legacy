package com.waz.zclient.feature.backup.di

import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.conversations.folders.FoldersBackUpDao
import com.waz.zclient.feature.backup.conversations.folders.FoldersBackUpModel
import com.waz.zclient.feature.backup.conversations.folders.FoldersBackupDataSource
import com.waz.zclient.feature.backup.conversations.folders.FoldersBackupMapper
import com.waz.zclient.feature.backup.io.database.BatchDatabaseIOHandler
import com.waz.zclient.feature.backup.io.database.SingleReadDatabaseIOHandler
import com.waz.zclient.feature.backup.io.file.BackUpFileIOHandler
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpDao
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpDataSource
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpMapper
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpModel
import com.waz.zclient.feature.backup.messages.MessagesBackUpDataSource
import com.waz.zclient.feature.backup.messages.MessagesBackUpModel
import com.waz.zclient.feature.backup.messages.database.MessagesBackUpDao
import com.waz.zclient.feature.backup.messages.mapper.MessagesBackUpDataMapper
import com.waz.zclient.feature.backup.usecase.CreateBackUpUseCase
import com.waz.zclient.storage.db.folders.FoldersEntity
import com.waz.zclient.storage.db.messages.MessagesEntity
import com.waz.zclient.storage.db.property.KeyValuesEntity
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

const val KEY_VALUES_FILE_NAME = "KeyValues"
const val MESSAGES_FILE_NAME = "Messages"
const val FOLDERS_FILE_NAME = "Folders"

val backupModules: List<Module>
    get() = listOf(backUpModule)

val backUpModule = module {
    factory { CreateBackUpUseCase(getAll()) } //this resolves all instances of type BackUpRepository

    // KeyValues
    factory { KeyValuesBackUpDao(get()) }
    factory { SingleReadDatabaseIOHandler<KeyValuesEntity>(get()) }
    factory { JsonConverter(KeyValuesBackUpModel.serializer()) } //TODO check if koin can resolve generics. use named parameters otherwise.
    factory { BackUpFileIOHandler<KeyValuesBackUpModel>(KEY_VALUES_FILE_NAME, get()) }
    factory { KeyValuesBackUpMapper() }
    factory { KeyValuesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Folders
    factory { FoldersBackUpDao(get()) }
    factory { SingleReadDatabaseIOHandler<FoldersEntity>(get()) }
    factory { JsonConverter(FoldersBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<FoldersBackUpModel>(FOLDERS_FILE_NAME, get()) }
    factory { FoldersBackupMapper() }
    factory { FoldersBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Messages
    factory { MessagesBackUpDao(get()) }
    factory { BatchDatabaseIOHandler<MessagesEntity>(get(), batchSize = 20) }
    factory { JsonConverter(MessagesBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<MessagesBackUpModel>(MESSAGES_FILE_NAME, get()) }
    factory { MessagesBackUpDataMapper() }
    factory { MessagesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class
}

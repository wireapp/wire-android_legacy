package com.waz.zclient.feature.backup.di

import android.os.Environment
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.io.database.BatchDatabaseIOHandler
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
import com.waz.zclient.storage.db.messages.MessagesEntity
import com.waz.zclient.storage.db.property.KeyValuesEntity
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

const val KEY_VALUES_FILE_NAME = "KeyValues"
const val MESSAGES_FILE_NAME = "Messages"

const val BATCH_SIZE = 1000

val targetDirForBackupFiles = Environment.getExternalStorageDirectory()

val backupModules: List<Module>
    get() = listOf(backUpModule)

val backUpModule = module {
    factory { CreateBackUpUseCase(getAll()) } //this resolves all instances of type BackUpRepository

    // KeyValues
    factory { KeyValuesBackUpDao(get()) }
    factory { BatchDatabaseIOHandler<KeyValuesEntity>(get(), batchSize = BATCH_SIZE) }
    factory { JsonConverter(KeyValuesBackUpModel.serializer()) } //TODO check if koin can resolve generics. use named parameters otherwise.
    factory { BackUpFileIOHandler<KeyValuesBackUpModel>(KEY_VALUES_FILE_NAME, get(), targetDirForBackupFiles) }
    factory { KeyValuesBackUpMapper() }
    factory { KeyValuesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Messages
    factory { MessagesBackUpDao(get()) }
    factory { BatchDatabaseIOHandler<MessagesEntity>(get(), batchSize = BATCH_SIZE) }
    factory { JsonConverter(MessagesBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<MessagesBackUpModel>(MESSAGES_FILE_NAME, get(), targetDirForBackupFiles) }
    factory { MessagesBackUpDataMapper() }
    factory { MessagesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class
}

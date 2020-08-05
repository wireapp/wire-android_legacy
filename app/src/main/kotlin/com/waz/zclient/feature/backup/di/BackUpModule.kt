package com.waz.zclient.feature.backup.di

import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.io.database.BatchDatabaseIOHandler
import com.waz.zclient.feature.backup.io.database.SingleReadDatabaseIOHandler
import com.waz.zclient.feature.backup.io.file.BackUpFileIOHandler
import com.waz.zclient.feature.backup.io.file.JsonConverter
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
import org.koin.dsl.bind
import org.koin.dsl.module

val backUpModule = module {
    factory { CreateBackUpUseCase(getAll()) } //this resolves all instances of type BackUpRepository

    // KeyValues
    factory { KeyValuesBackUpDao(get()) }
    factory { SingleReadDatabaseIOHandler<KeyValuesEntity>(get()) }
    factory { JsonConverter(KeyValuesBackUpModel.serializer()) } //TODO check if koin can resolve generics. use named parameters otherwise.
    factory { BackUpFileIOHandler<KeyValuesBackUpModel>("KeyValues", get()) }
    factory { KeyValuesBackUpMapper() }
    factory { KeyValuesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Messages
    factory { MessagesBackUpDao(get()) }
    factory { BatchDatabaseIOHandler<MessagesEntity>(get(), batchSize = 20) }
    factory { JsonConverter(MessagesBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<MessagesBackUpModel>("Messages", get()) }
    factory { MessagesBackUpDataMapper() }
    factory { MessagesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class
}

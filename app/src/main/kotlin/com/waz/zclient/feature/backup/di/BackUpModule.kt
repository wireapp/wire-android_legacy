package com.waz.zclient.feature.backup.di

import android.os.Environment
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.conversations.ConversationRoleActionBackUpModel
import com.waz.zclient.feature.backup.conversations.ConversationRoleBackupMapper
import com.waz.zclient.feature.backup.conversations.ConversationRolesBackupDataSource
import com.waz.zclient.feature.backup.folders.FoldersBackUpModel
import com.waz.zclient.feature.backup.folders.FoldersBackupDataSource
import com.waz.zclient.feature.backup.folders.FoldersBackupMapper
import com.waz.zclient.feature.backup.ZipHandler
import com.waz.zclient.feature.backup.io.database.BatchDatabaseIOHandler
import com.waz.zclient.feature.backup.io.file.BackUpFileIOHandler
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpDataSource
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpMapper
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpModel
import com.waz.zclient.feature.backup.messages.MessagesBackUpDataSource
import com.waz.zclient.feature.backup.messages.MessagesBackUpModel
import com.waz.zclient.feature.backup.messages.mapper.MessagesBackUpDataMapper
import com.waz.zclient.feature.backup.usecase.CreateBackUpUseCase
import com.waz.zclient.storage.db.UserDatabase
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

private const val KEY_VALUES_FILE_NAME = "KeyValues"
private const val MESSAGES_FILE_NAME = "Messages"
private const val FOLDERS_FILE_NAME = "Folders"
private const val CONVERSATION_ROLE_ACTION_FILE_NAME = "ConversationRoleAction"

val backupModules: List<Module>
    get() = listOf(backUpModule)

val backUpModule = module {
    single { Environment.getExternalStorageDirectory() }
    single { ZipHandler(get()) }

    factory { CreateBackUpUseCase(getAll()) } //this resolves all instances of type BackUpRepository

    // KeyValues
    factory { BatchDatabaseIOHandler((get<UserDatabase>()).keyValuesDao()) }
    factory { JsonConverter(KeyValuesBackUpModel.serializer()) } //TODO check if koin can resolve generics. use named parameters otherwise.
    factory { BackUpFileIOHandler<KeyValuesBackUpModel>(KEY_VALUES_FILE_NAME, get(), get()) }
    factory { KeyValuesBackUpMapper() }
    factory { KeyValuesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Folders
    factory { BatchDatabaseIOHandler(get<UserDatabase>().foldersDao()) }
    factory { JsonConverter(FoldersBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<FoldersBackUpModel>(FOLDERS_FILE_NAME, get(), get()) }
    factory { FoldersBackupMapper() }
    factory { FoldersBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Conversation Roles
    factory { BatchDatabaseIOHandler(get<UserDatabase>().conversationRoleActionDao()) }
    factory { JsonConverter(ConversationRoleActionBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<ConversationRoleActionBackUpModel>(CONVERSATION_ROLE_ACTION_FILE_NAME, get(), get()) }
    factory { ConversationRoleBackupMapper() }
    factory { ConversationRolesBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Messages
    factory { BatchDatabaseIOHandler(get<UserDatabase>().messagesDao()) }
    factory { JsonConverter(MessagesBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<MessagesBackUpModel>(MESSAGES_FILE_NAME, get(), get()) }
    factory { MessagesBackUpDataMapper() }
    factory { MessagesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class
}

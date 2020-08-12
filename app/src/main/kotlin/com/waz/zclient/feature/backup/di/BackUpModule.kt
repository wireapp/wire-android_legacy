package com.waz.zclient.feature.backup.di

import android.os.Environment
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.ZipHandler
import com.waz.zclient.feature.backup.assets.AssetsBackUpModel
import com.waz.zclient.feature.backup.assets.AssetsBackupDataSource
import com.waz.zclient.feature.backup.assets.AssetsBackupMapper
import com.waz.zclient.feature.backup.buttons.ButtonBackUpModel
import com.waz.zclient.feature.backup.buttons.ButtonBackupMapper
import com.waz.zclient.feature.backup.buttons.ButtonsBackupDataSource
import com.waz.zclient.feature.backup.conversations.ConversationFoldersBackUpModel
import com.waz.zclient.feature.backup.conversations.ConversationFoldersBackupDataSource
import com.waz.zclient.feature.backup.conversations.ConversationFoldersBackupMapper
import com.waz.zclient.feature.backup.conversations.ConversationRoleActionBackUpModel
import com.waz.zclient.feature.backup.conversations.ConversationRoleBackupMapper
import com.waz.zclient.feature.backup.conversations.ConversationRolesBackupDataSource
import com.waz.zclient.feature.backup.conversations.ConversationsBackUpModel
import com.waz.zclient.feature.backup.conversations.ConversationsBackupDataSource
import com.waz.zclient.feature.backup.conversations.ConversationsBackupMapper
import com.waz.zclient.feature.backup.folders.FoldersBackUpModel
import com.waz.zclient.feature.backup.folders.FoldersBackupDataSource
import com.waz.zclient.feature.backup.folders.FoldersBackupMapper
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
private const val CONVERSATIONS_FILE_NAME = "Conversations"
private const val CONVERSATION_FOLDERS_FILE_NAME = "ConversationFolders"
private const val CONVERSATION_ROLE_ACTION_FILE_NAME = "ConversationRoleAction"
private const val ASSETS_FILE_NAME = "Assets"
private const val BUTTONS_FILE_NAME = "Buttons"

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

    // Conversation Folders
    factory { BatchDatabaseIOHandler(get<UserDatabase>().conversationFoldersDao()) }
    factory { JsonConverter(ConversationFoldersBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<ConversationFoldersBackUpModel>(CONVERSATION_FOLDERS_FILE_NAME, get(), get()) }
    factory { ConversationFoldersBackupMapper() }
    factory { ConversationFoldersBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Conversations
    factory { BatchDatabaseIOHandler(get<UserDatabase>().conversationsDao()) }
    factory { JsonConverter(ConversationsBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<ConversationsBackUpModel>(CONVERSATIONS_FILE_NAME, get(), get()) }
    factory { ConversationsBackupMapper() }
    factory { ConversationsBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Assets
    factory { BatchDatabaseIOHandler(get<UserDatabase>().assetsDao()) }
    factory { JsonConverter(AssetsBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<AssetsBackUpModel>(ASSETS_FILE_NAME, get(), get()) }
    factory { AssetsBackupMapper() }
    factory { AssetsBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Messages
    factory { BatchDatabaseIOHandler(get<UserDatabase>().messagesDao()) }
    factory { JsonConverter(MessagesBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<MessagesBackUpModel>(MESSAGES_FILE_NAME, get(), get()) }
    factory { MessagesBackUpDataMapper() }
    factory { MessagesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Buttons
    factory { BatchDatabaseIOHandler(get<UserDatabase>().conversationsDao()) }
    factory { JsonConverter(ButtonBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<ButtonBackUpModel>(BUTTONS_FILE_NAME, get(), get()) }
    factory { ButtonBackupMapper() }
    factory { ButtonsBackupDataSource(get(), get(), get()) } bind BackUpRepository::class
}

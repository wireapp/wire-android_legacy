package com.waz.zclient.feature.backup.di

import android.os.Environment
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.ZipHandler
import com.waz.zclient.feature.backup.assets.AssetsBackUpModel
import com.waz.zclient.feature.backup.assets.AssetsBackupDataSource
import com.waz.zclient.feature.backup.assets.AssetsBackupMapper
import com.waz.zclient.feature.backup.buttons.ButtonsBackUpModel
import com.waz.zclient.feature.backup.buttons.ButtonsBackupMapper
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
import com.waz.zclient.feature.backup.conversations.ConversationMembersBackUpModel
import com.waz.zclient.feature.backup.conversations.ConversationMembersBackupDataSource
import com.waz.zclient.feature.backup.conversations.ConversationMembersBackupMapper
import com.waz.zclient.feature.backup.folders.FoldersBackUpModel
import com.waz.zclient.feature.backup.folders.FoldersBackupDataSource
import com.waz.zclient.feature.backup.folders.FoldersBackupMapper
import com.waz.zclient.feature.backup.io.database.BatchDatabaseIOHandler
import com.waz.zclient.feature.backup.io.file.BackUpFileIOHandler
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpDataSource
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpMapper
import com.waz.zclient.feature.backup.keyvalues.KeyValuesBackUpModel
import com.waz.zclient.feature.backup.messages.LikesBackUpModel
import com.waz.zclient.feature.backup.messages.LikesBackupDataSource
import com.waz.zclient.feature.backup.messages.LikesBackupMapper
import com.waz.zclient.feature.backup.messages.MessagesBackUpDataSource
import com.waz.zclient.feature.backup.messages.MessagesBackUpModel
import com.waz.zclient.feature.backup.messages.MessagesBackUpDataMapper
import com.waz.zclient.feature.backup.properties.PropertiesBackUpDataSource
import com.waz.zclient.feature.backup.properties.PropertiesBackUpMapper
import com.waz.zclient.feature.backup.properties.PropertiesBackUpModel
import com.waz.zclient.feature.backup.receipts.ReadReceiptsBackUpModel
import com.waz.zclient.feature.backup.receipts.ReadReceiptsBackupDataSource
import com.waz.zclient.feature.backup.receipts.ReadReceiptsBackupMapper
import com.waz.zclient.feature.backup.usecase.CreateBackUpUseCase
import com.waz.zclient.feature.backup.users.UsersBackUpDataSource
import com.waz.zclient.feature.backup.users.UsersBackUpDataMapper
import com.waz.zclient.feature.backup.users.UsersBackUpModel
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
private const val CONVERSATION_MEMBERS_FILE_NAME = "Buttons"
private const val LIKES_FILE_NAME = "Likes"
private const val PROPERTIES_FILE_NAME = "Properties"
private const val READ_RECEIPTS_FILE_NAME = "ReadReceipts"
private const val USERS_FILE_NAME = "Users"

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
    factory { BatchDatabaseIOHandler(get<UserDatabase>().buttonsDao()) }
    factory { JsonConverter(ButtonsBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<ButtonsBackUpModel>(BUTTONS_FILE_NAME, get(), get()) }
    factory { ButtonsBackupMapper() }
    factory { ButtonsBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // ConversationMembers
    factory { BatchDatabaseIOHandler(get<UserDatabase>().conversationMembersDao()) }
    factory { JsonConverter(ConversationMembersBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<ConversationMembersBackUpModel>(CONVERSATION_MEMBERS_FILE_NAME, get(), get()) }
    factory { ConversationMembersBackupMapper() }
    factory { ConversationMembersBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Likes
    factory { BatchDatabaseIOHandler(get<UserDatabase>().likesDao()) }
    factory { JsonConverter(LikesBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<LikesBackUpModel>(LIKES_FILE_NAME, get(), get()) }
    factory { LikesBackupMapper() }
    factory { LikesBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Properties
    factory { BatchDatabaseIOHandler((get<UserDatabase>()).propertiesDao()) }
    factory { JsonConverter(PropertiesBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<PropertiesBackUpModel>(PROPERTIES_FILE_NAME, get(), get()) }
    factory { PropertiesBackUpMapper() }
    factory { PropertiesBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class
    
    // ReadReceipts
    factory { BatchDatabaseIOHandler(get<UserDatabase>().readReceiptsDao()) }
    factory { JsonConverter(ReadReceiptsBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<ReadReceiptsBackUpModel>(READ_RECEIPTS_FILE_NAME, get(), get()) }
    factory { ReadReceiptsBackupMapper() }
    factory { ReadReceiptsBackupDataSource(get(), get(), get()) } bind BackUpRepository::class

    // Users
    factory { BatchDatabaseIOHandler((get<UserDatabase>()).usersDao()) }
    factory { JsonConverter(UsersBackUpModel.serializer()) }
    factory { BackUpFileIOHandler<UsersBackUpModel>(USERS_FILE_NAME, get(), get()) }
    factory { UsersBackUpDataMapper() }
    factory { UsersBackUpDataSource(get(), get(), get()) } bind BackUpRepository::class
}

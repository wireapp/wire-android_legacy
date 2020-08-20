package com.waz.zclient.feature.backup.di

import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.BackUpViewModel
import com.waz.zclient.feature.backup.zip.ZipHandler
import com.waz.zclient.feature.backup.encryption.EncryptionHandler
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
import com.waz.zclient.feature.backup.encryption.EncryptionHandlerDataSource
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
import com.waz.zclient.feature.backup.metadata.BackupMetaData
import com.waz.zclient.feature.backup.metadata.MetaDataHandler
import com.waz.zclient.feature.backup.properties.PropertiesBackUpDataSource
import com.waz.zclient.feature.backup.properties.PropertiesBackUpMapper
import com.waz.zclient.feature.backup.properties.PropertiesBackUpModel
import com.waz.zclient.feature.backup.receipts.ReadReceiptsBackUpModel
import com.waz.zclient.feature.backup.receipts.ReadReceiptsBackupDataSource
import com.waz.zclient.feature.backup.receipts.ReadReceiptsBackupMapper
import com.waz.zclient.feature.backup.usecase.CreateBackUpUseCase
import com.waz.zclient.feature.backup.usecase.RestoreBackUpUseCase
import com.waz.zclient.feature.backup.users.UsersBackUpDataSource
import com.waz.zclient.feature.backup.users.UsersBackUpDataMapper
import com.waz.zclient.feature.backup.users.UsersBackUpModel
import com.waz.zclient.storage.db.UserDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

private const val METADATA = "Metadata"
private const val KEY_VALUES = "KeyValues"
private const val MESSAGES = "Messages"
private const val FOLDERS = "Folders"
private const val CONVERSATIONS = "Conversations"
private const val CONVERSATION_FOLDERS = "ConversationFolders"
private const val CONVERSATION_ROLE_ACTION = "ConversationRoleAction"
private const val ASSETS = "Assets"
private const val BUTTONS = "Buttons"
private const val CONVERSATION_MEMBERS = "ConversationMembers"
private const val LIKES = "Likes"
private const val PROPERTIES = "Properties"
private const val READ_RECEIPTS = "ReadReceipts"
private const val USERS = "Users"

private const val JSON = "Json"
private const val FILE = "File"
private const val DB = "Db"
private const val MAPPER = "Mapper"

private const val BACKUP_VERSION = 1

val backupModules: List<Module>
    get() = listOf(backUpModule)

val backUpModule = module {
    single { androidContext().externalCacheDir }
    single { ZipHandler(get()) }
    single { EncryptionHandlerDataSource() } bind EncryptionHandler::class

    factory { CreateBackUpUseCase(getAll(), get(), get(), get()) } //this resolves all instances of type BackUpRepository
    factory { RestoreBackUpUseCase(getAll(), get(), get(), get()) }
    viewModel { BackUpViewModel(get()) }

    // MetaData
    factory(named(METADATA + JSON)) { JsonConverter(BackupMetaData.serializer()) }
    factory { MetaDataHandler(BACKUP_VERSION, get(named(METADATA + JSON)), get()) } bind MetaDataHandler::class

    // KeyValues
    factory(named(KEY_VALUES + JSON)) { JsonConverter(KeyValuesBackUpModel.serializer()) }
    factory(named(KEY_VALUES + FILE)) { BackUpFileIOHandler<KeyValuesBackUpModel>(KEY_VALUES, get(named(KEY_VALUES + JSON)), get()) }
    factory(named(KEY_VALUES + DB)) { BatchDatabaseIOHandler((get<UserDatabase>()).keyValuesDao()) }
    factory(named(KEY_VALUES + MAPPER)) { KeyValuesBackUpMapper() }
    factory {
        KeyValuesBackUpDataSource(get(named(KEY_VALUES + DB)), get(named(KEY_VALUES + FILE)), get(named(KEY_VALUES + MAPPER)))
    } bind BackUpRepository::class

    // Folders
    factory(named(FOLDERS + JSON)) { JsonConverter(FoldersBackUpModel.serializer()) }
    factory(named(FOLDERS + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().foldersDao()) }
    factory(named(FOLDERS + FILE)) { BackUpFileIOHandler<FoldersBackUpModel>(FOLDERS, get(named(FOLDERS + JSON)), get()) }
    factory(named(FOLDERS + MAPPER)) { FoldersBackupMapper() }
    factory {
        FoldersBackupDataSource(get(named(FOLDERS + DB)), get(named(FOLDERS + FILE)), get(named(FOLDERS + MAPPER)))
    } bind BackUpRepository::class

    // Conversation Roles
    factory(named(CONVERSATION_ROLE_ACTION + JSON)) { JsonConverter(ConversationRoleActionBackUpModel.serializer()) }
    factory(named(CONVERSATION_ROLE_ACTION + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().conversationRoleActionDao()) }
    factory(named(CONVERSATION_ROLE_ACTION + FILE)) {
        BackUpFileIOHandler<ConversationRoleActionBackUpModel>(CONVERSATION_ROLE_ACTION, get(named(CONVERSATION_ROLE_ACTION + JSON)), get())
    }
    factory(named(CONVERSATION_ROLE_ACTION + MAPPER)) { ConversationRoleBackupMapper() }
    factory {
        ConversationRolesBackupDataSource(
            get(named(CONVERSATION_ROLE_ACTION + DB)),
            get(named(CONVERSATION_ROLE_ACTION + FILE)),
            get(named(CONVERSATION_ROLE_ACTION + MAPPER))
        )
    } bind BackUpRepository::class

    // Conversation Folders
    factory(named(CONVERSATION_FOLDERS + JSON)) { JsonConverter(ConversationFoldersBackUpModel.serializer()) }
    factory(named(CONVERSATION_FOLDERS + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().conversationFoldersDao()) }
    factory(named(CONVERSATION_FOLDERS + FILE)) {
        BackUpFileIOHandler<ConversationFoldersBackUpModel>(CONVERSATION_FOLDERS, get(named(CONVERSATION_FOLDERS + JSON)), get())
    }
    factory(named(CONVERSATION_FOLDERS + MAPPER)) { ConversationFoldersBackupMapper() }
    factory {
        ConversationFoldersBackupDataSource(
            get(named(CONVERSATION_FOLDERS + DB)),
            get(named(CONVERSATION_FOLDERS + FILE)),
            get(named(CONVERSATION_FOLDERS + MAPPER))
        )
    } bind BackUpRepository::class

    // Conversations
    factory(named(CONVERSATIONS + JSON)) { JsonConverter(ConversationsBackUpModel.serializer()) }
    factory(named(CONVERSATIONS + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().conversationsDao()) }
    factory(named(CONVERSATIONS + FILE)) {
        BackUpFileIOHandler<ConversationsBackUpModel>(CONVERSATIONS, get(named(CONVERSATIONS + JSON)), get())
    }
    factory(named(CONVERSATIONS + MAPPER)) { ConversationsBackupMapper() }
    factory {
        ConversationsBackupDataSource(
            get(named(CONVERSATIONS + DB)),
            get(named(CONVERSATIONS + FILE)),
            get(named(CONVERSATIONS + MAPPER))
        )
    } bind BackUpRepository::class

    // Assets
    factory(named(ASSETS + JSON)) { JsonConverter(AssetsBackUpModel.serializer()) }
    factory(named(ASSETS + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().assetsDao()) }
    factory(named(ASSETS + FILE)) { BackUpFileIOHandler<AssetsBackUpModel>(ASSETS, get(named(ASSETS + JSON)), get()) }
    factory(named(ASSETS + MAPPER)) { AssetsBackupMapper() }
    factory {
        AssetsBackupDataSource(get(named(ASSETS + DB)), get(named(ASSETS + FILE)), get(named(ASSETS + MAPPER)))
    } bind BackUpRepository::class

    // Messages
    factory(named(MESSAGES + JSON)) { JsonConverter(MessagesBackUpModel.serializer()) }
    factory(named(MESSAGES + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().messagesDao()) }
    factory(named(MESSAGES + FILE)) { BackUpFileIOHandler<MessagesBackUpModel>(MESSAGES, get(named(MESSAGES + JSON)), get()) }
    factory(named(MESSAGES + MAPPER)) { MessagesBackUpDataMapper() }
    factory {
        MessagesBackUpDataSource(get(named(MESSAGES + DB)), get(named(MESSAGES + FILE)), get(named(MESSAGES + MAPPER)))
    } bind BackUpRepository::class

    // Buttons
    factory(named(BUTTONS + JSON)) { JsonConverter(ButtonsBackUpModel.serializer()) }
    factory(named(BUTTONS + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().buttonsDao()) }
    factory(named(BUTTONS + FILE)) { BackUpFileIOHandler<ButtonsBackUpModel>(BUTTONS, get(named(BUTTONS + JSON)), get()) }
    factory(named(BUTTONS + MAPPER)) { ButtonsBackupMapper() }
    factory {
        ButtonsBackupDataSource(get(named(BUTTONS + DB)), get(named(BUTTONS + FILE)), get(named(BUTTONS + MAPPER)))
    } bind BackUpRepository::class

    // ConversationMembers
    factory(named(CONVERSATION_MEMBERS + JSON)) { JsonConverter(ConversationMembersBackUpModel.serializer()) }
    factory(named(CONVERSATION_MEMBERS + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().conversationMembersDao()) }
    factory(named(CONVERSATION_MEMBERS + FILE)) {
        BackUpFileIOHandler<ConversationMembersBackUpModel>(CONVERSATION_MEMBERS, get(named(CONVERSATION_MEMBERS + JSON)), get())
    }
    factory(named(CONVERSATION_MEMBERS + MAPPER)) { ConversationMembersBackupMapper() }
    factory {
        ConversationMembersBackupDataSource(
            get(named(CONVERSATION_MEMBERS + DB)),
            get(named(CONVERSATION_MEMBERS + FILE)),
            get(named(CONVERSATION_MEMBERS + MAPPER))
        )
    } bind BackUpRepository::class

    // Likes
    factory(named(LIKES + JSON)) { JsonConverter(LikesBackUpModel.serializer()) }
    factory(named(LIKES + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().likesDao()) }
    factory(named(LIKES + FILE)) { BackUpFileIOHandler<LikesBackUpModel>(LIKES, get(named(LIKES + JSON)), get()) }
    factory(named(LIKES + MAPPER)) { LikesBackupMapper() }
    factory {
        LikesBackupDataSource(get(named(LIKES + DB)), get(named(LIKES + FILE)), get(named(LIKES + MAPPER)))
    } bind BackUpRepository::class

    // Properties
    factory(named(PROPERTIES + JSON)) { JsonConverter(PropertiesBackUpModel.serializer()) }
    factory(named(PROPERTIES + DB)) { BatchDatabaseIOHandler((get<UserDatabase>()).propertiesDao()) }
    factory(named(PROPERTIES + FILE)) { BackUpFileIOHandler<PropertiesBackUpModel>(PROPERTIES, get(named(PROPERTIES + JSON)), get()) }
    factory(named(PROPERTIES + MAPPER)) { PropertiesBackUpMapper() }
    factory {
        PropertiesBackUpDataSource(get(named(PROPERTIES + DB)), get(named(PROPERTIES + FILE)), get(named(PROPERTIES + MAPPER)))
    } bind BackUpRepository::class

    // ReadReceipts
    factory(named(READ_RECEIPTS + JSON)) { JsonConverter(ReadReceiptsBackUpModel.serializer()) }
    factory(named(READ_RECEIPTS + DB)) { BatchDatabaseIOHandler(get<UserDatabase>().readReceiptsDao()) }
    factory(named(READ_RECEIPTS + FILE)) {
        BackUpFileIOHandler<ReadReceiptsBackUpModel>(READ_RECEIPTS, get(named(READ_RECEIPTS + JSON)), get())
    }
    factory(named(READ_RECEIPTS + MAPPER)) { ReadReceiptsBackupMapper() }
    factory {
        ReadReceiptsBackupDataSource(get(named(READ_RECEIPTS + DB)), get(named(READ_RECEIPTS + FILE)), get(named(READ_RECEIPTS + MAPPER)))
    } bind BackUpRepository::class

    // Users
    factory(named(USERS + JSON)) { JsonConverter(UsersBackUpModel.serializer()) }
    factory(named(USERS + DB)) { BatchDatabaseIOHandler((get<UserDatabase>()).usersDao()) }
    factory(named(USERS + FILE)) { BackUpFileIOHandler<UsersBackUpModel>(USERS, get(named(USERS + JSON)), get()) }
    factory(named(USERS + MAPPER)) { UsersBackUpDataMapper() }
    factory {
        UsersBackUpDataSource(get(named(USERS + DB)), get(named(USERS + FILE)), get(named(USERS + MAPPER)))
    } bind BackUpRepository::class
}

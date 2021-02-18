/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import java.io.File

import android.content.Context
import com.softwaremill.macwire._
import com.waz.api.ContentSearchQuery
import com.waz.content.{MembersStorageImpl, UsersStorageImpl, ZmsDatabase, _}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.repository.{FCMNotificationStatsRepositoryImpl, FCMNotificationsRepositoryImpl}
import com.waz.service.EventScheduler.{Sequential, Stage}
import com.waz.service.assets._
import com.waz.service.call._
import com.waz.service.conversation._
import com.waz.service.media._
import com.waz.service.messages._
import com.waz.service.otr._
import com.waz.service.push._
import com.waz.service.teams.{FeatureFlagsService, FeatureFlagsServiceImpl, TeamsService, TeamsServiceImpl}
import com.waz.service.tracking.TrackingService
import com.waz.sync._
import com.waz.sync.client._
import com.waz.sync.handler._
import com.waz.sync.otr.{OtrClientsSyncHandler, OtrClientsSyncHandlerImpl, OtrSyncHandler, OtrSyncHandlerImpl}
import com.waz.sync.queue.{SyncContentUpdater, SyncContentUpdaterImpl}
import com.waz.threading.Threading
import com.waz.ui.UiModule
import com.waz.utils.crypto._
import com.waz.utils.wrappers.{AndroidContext, DB, GoogleApi}
import com.waz.utils.{IoUtils, Locales}
import com.waz.znet2.http.HttpClient
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.{AuthRequestInterceptor, OkHttpWebSocketFactory}
import com.wire.signals.{EventContext, SerialDispatchQueue}
import org.threeten.bp.{Clock, Duration, Instant}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try

class ZMessagingFactory(global: GlobalModule) {

  implicit val tracking = global.trackingService

  def baseStorage(userId: UserId) = new StorageModule(global.context, userId, global.prefs, global.trackingService)

  def auth(userId: UserId) = new AuthenticationManager(userId, global.accountsService, global.accountsStorage, global.loginClient)

  def credentialsClient(urlCreator: UrlCreator, httpClient: HttpClient, authRequestInterceptor: AuthRequestInterceptor) =
    new CredentialsUpdateClientImpl()(urlCreator, httpClient, authRequestInterceptor)

  def cryptobox(userId: UserId, storage: StorageModule) = new CryptoBoxService(global.context, userId, global.metadata, storage.userPrefs)

  def zmessaging(teamId: Option[TeamId], clientId: ClientId, accountManager: AccountManager, storage: StorageModule, cryptoBox: CryptoBoxService) = wire[ZMessaging]
}

trait Assets2Module {
  import assets._

  def uriHelper: UriHelper
  def assetDetailsService: AssetDetailsService
  def assetPreviewService: AssetPreviewService
}

class StorageModule(context: Context, val userId: UserId, globalPreferences: GlobalPreferences, tracking: TrackingService) {
  lazy val db                                         = new ZmsDatabase(userId, context)
  lazy val userPrefs                                  = UserPreferences.apply(context, db, globalPreferences)
  lazy val usersStorage:      UsersStorage            = wire[UsersStorageImpl]
  lazy val otrClientsStorage: OtrClientsStorage       = wire[OtrClientsStorageImpl]
  lazy val membersStorage                             = wire[MembersStorageImpl]
  lazy val rolesStorage: ConversationRolesStorage     = wire[ConversationRolesStorageImpl]
  lazy val oldAssetStorage:      AssetsStorage        = wire[AssetsStorageImpl]
  lazy val reactionsStorage                           = wire[ReactionsStorageImpl]
  lazy val notifStorage:      NotificationStorage     = wire[NotificationStorageImpl]
  lazy val convsStorage:      ConversationStorage     = wire[ConversationStorageImpl]
  lazy val msgDeletions:      MsgDeletionStorage      = wire[MsgDeletionStorageImpl]
  lazy val msgEdits:          EditHistoryStorage      = wire[EditHistoryStorageImpl]
  lazy val propertiesStorage:   PropertiesStorage       = new PropertiesStorageImpl()(context, db2, Threading.IO)

  lazy val db2: DB = DB(db.dbHelper.getWritableDatabase)
  lazy val inProgressAssetStorage: DownloadAssetStorage = new DownloadAssetStorageImpl(context, db2)(Threading.IO)
  lazy val rawAssetStorage: UploadAssetStorage   = new UploadAssetStorageImpl(context, db2)(Threading.IO)
  lazy val assetsStorage: AssetStorage         = new AssetStorageImpl(context, db2, Threading.IO)
}

class ZMessaging(val teamId: Option[TeamId], val clientId: ClientId, account: AccountManager, val storage: StorageModule, val cryptoBox: CryptoBoxService) extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  val clock = ZMessaging.clock

  val global     = account.global
  val selfUserId = account.userId

  val auth       = account.auth
  val urlCreator = global.urlCreator
  implicit val httpClient: HttpClient = account.global.httpClient
  val httpClientForLongRunning: HttpClient = account.global.httpClientForLongRunning
  val lifecycle  = global.lifecycle

  lazy val accounts           = ZMessaging.currentAccounts
  implicit lazy val evContext = account.accountContext

  lazy val sync:              SyncServiceHandle       = wire[AndroidSyncServiceHandle]
  lazy val syncRequests:      SyncRequestService      = wire[SyncRequestServiceImpl]
  lazy val syncContent:       SyncContentUpdater      = wire[SyncContentUpdaterImpl]

  lazy val otrClientsService: OtrClientsService       = wire[OtrClientsServiceImpl]
  lazy val otrClientsSync:    OtrClientsSyncHandler   = wire[OtrClientsSyncHandlerImpl]
  lazy val otrClient:         OtrClientImpl           = account.otrClient
  lazy val credentialsClient: CredentialsUpdateClientImpl = account.credentialsClient
  implicit lazy val authRequestInterceptor: AuthRequestInterceptor = account.authRequestInterceptor

  def context           = global.context
  def accountStorage    = global.accountsStorage
  def contextWrapper    = new AndroidContext(context)
  def googleApi         = global.googleApi
  def globalToken       = global.tokenService
  def permissions       = global.permissions
  def phoneNumbers      = global.phoneNumbers
  def prefs             = global.prefs
  def bitmapDecoder     = global.bitmapDecoder
  def timeouts          = global.timeouts
  def cache             = global.cache
  def globalRecAndPlay  = global.recordingAndPlayback
  def tempFiles         = global.tempFiles
  def metadata          = global.metadata
  def network           = global.network
  def blacklist         = global.blacklist
  def backend           = global.backend
  def teamsStorage      = global.teamsStorage
  def videoTranscoder   = global.videoTranscoder
  def audioTranscader   = global.audioTranscoder
  def avs               = global.avs
  def flowmanager       = global.flowmanager
  def mediamanager      = global.mediaManager
  def notifcationsUi    = global.notificationsUi
  def tracking          = global.trackingService
  def syncHandler = global.syncHandler

  def db                = storage.db
  def userPrefs         = storage.userPrefs
  def usersStorage      = storage.usersStorage
  def otrClientsStorage = storage.otrClientsStorage
  def membersStorage    = storage.membersStorage
  def rolesStorage = storage.rolesStorage
  def assetsStorage     = storage.assetsStorage
  def oldAssetStorage                    = storage.oldAssetStorage
  def rawAssetStorage               = storage.rawAssetStorage
  def downloadAssetStorage               = storage.inProgressAssetStorage
  def reactionsStorage  = storage.reactionsStorage
  def notifStorage      = storage.notifStorage
  def convsStorage      = storage.convsStorage
  def msgDeletions      = storage.msgDeletions
  def msgEdits          = storage.msgEdits
  def propertiesStorage = storage.propertiesStorage
  def uriHelper         = assets2Module.uriHelper

  lazy val messagesStorage: MessagesStorage                       = wire[MessagesStorageImpl]
  lazy val msgAndLikes: MessageAndLikesStorageImpl                = wire[MessageAndLikesStorageImpl]
  lazy val messagesIndexStorage: MessageIndexStorage              = wire[MessageIndexStorage]
  lazy val eventStorage: PushNotificationEventsStorage            = wire[PushNotificationEventsStorageImpl]
  lazy val readReceiptsStorage: ReadReceiptsStorage               = wire[ReadReceiptsStorageImpl]
  lazy val foldersStorage: FoldersStorage                         = wire[FoldersStorageImpl]
  lazy val conversationFoldersStorage: ConversationFoldersStorage = wire[ConversationFoldersStorageImpl]
  lazy val buttonsStorage: ButtonsStorage                         = wire[ButtonsStorageImpl]

  lazy val googleMapsClient   = new GoogleMapsClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val youtubeClient      = new YouTubeClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val assetClient        = new AssetClientImpl()(urlCreator, httpClientForLongRunning, authRequestInterceptor)
  lazy val usersClient        = new UsersClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val convClient         = new ConversationsClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val teamClient         = new TeamsClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val featureFlagsClient = new FeatureFlagsClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val pushNotificationsClient: PushNotificationsClient = new PushNotificationsClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val gcmClient          = new PushTokenClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val typingClient       = new TypingClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val invitationClient   = account.invitationClient
  lazy val giphyClient: GiphyClient = new GiphyClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val userSearchClient   = new UserSearchClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val connectionsClient  = new ConnectionsClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val messagesClient     = new MessagesClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val openGraphClient    = wire[OpenGraphClientImpl]
  lazy val handlesClient      = new HandlesClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val integrationsClient = new IntegrationsClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val callingClient      = new CallingClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val propertiesClient: PropertiesClient = new PropertiesClientImpl()(urlCreator, httpClient, authRequestInterceptor)
  lazy val fcmNotsRepo        = new FCMNotificationsRepositoryImpl()(db)
  lazy val fcmNotStatsRepo    = new FCMNotificationStatsRepositoryImpl(fcmNotsRepo)(db, Threading.Background)

  lazy val convsContent: ConversationsContentUpdaterImpl = wire[ConversationsContentUpdaterImpl]
  lazy val messagesContent: MessagesContentUpdater = wire[MessagesContentUpdater]

  lazy val push: PushService                          = wire[PushServiceImpl]
  lazy val pushToken: PushTokenService                = wire[PushTokenService]
  lazy val errors                                     = wire[ErrorsServiceImpl]
  lazy val reporting                                  = new ZmsReportingService(selfUserId, global.reporting)
  lazy val wsFactory                                  = new OkHttpWebSocketFactory(global.httpProxy)
  lazy val wsPushService: WSPushService               = wireWith(WSPushServiceImpl.apply _)
  lazy val userSearch: UserSearchService              = wire[UserSearchServiceImpl]
  lazy val users: UserService                         = wire[UserServiceImpl]
  lazy val conversations: ConversationsService        = wire[ConversationsServiceImpl]
  lazy val convOrder: ConversationOrderEventsService  = wire[ConversationOrderEventsService]
  lazy val convsUi: ConversationsUiService            = wire[ConversationsUiServiceImpl]
  lazy val selectedConv: SelectedConversationService  = wire[SelectedConversationServiceImpl]
  lazy val teams: TeamsService                        = wire[TeamsServiceImpl]
  lazy val featureFlags: FeatureFlagsService          = wire[FeatureFlagsServiceImpl]
  lazy val integrations: IntegrationsService          = wire[IntegrationsServiceImpl]
  lazy val messages: MessagesServiceImpl              = wire[MessagesServiceImpl]
  lazy val verificationUpdater                        = wire[VerificationStateUpdater]
  lazy val msgEvents: MessageEventProcessor           = wire[MessageEventProcessor]
  lazy val connection: ConnectionService              = wire[ConnectionServiceImpl]
  lazy val httpProxy: Option[java.net.Proxy]          = global.httpProxy
  lazy val calling: CallingServiceImpl                = wire[CallingServiceImpl]
  lazy val callLogging: CallLoggingService            = wire[CallLoggingService]
  lazy val typing: TypingService                      = wire[TypingService]
  lazy val richmedia                                  = wire[RichMediaService]
  lazy val giphy: GiphyService                        = new GiphyServiceImpl(giphyClient)(Threading.Background)
  lazy val youtubeMedia                               = wire[YouTubeMediaService]
  lazy val googleMapsMediaService                     = wire[GoogleMapsMediaServiceImpl]
  lazy val otrService: OtrService                     = wire[OtrServiceImpl]
  lazy val genericMsgs: GenericMessageService         = wire[GenericMessageService]
  lazy val reactions: ReactionsService                = wire[ReactionsService]
  lazy val notifications: NotificationService         = wire[NotificationServiceImpl]
  lazy val recordAndPlay                              = wire[RecordAndPlayService]
  lazy val receipts                                   = wire[ReceiptService]
  lazy val ephemeral                                  = wire[EphemeralMessagesService]
  lazy val replyHashing                               = wire[ReplyHashingImpl]
  lazy val foldersService: FoldersService             = wire[FoldersServiceImpl]
  lazy val rolesService: ConversationRolesService     = wire[ConversationRolesServiceImpl]
  lazy val usersearchSync                             = wire[UserSearchSyncHandler]
  lazy val usersSync: UsersSyncHandler                = wire[UsersSyncHandlerImpl]
  lazy val conversationSync                           = wire[ConversationsSyncHandler]
  lazy val teamsSync:       TeamsSyncHandler          = wire[TeamsSyncHandlerImpl]
  lazy val featureFlagsSync: FeatureFlagsSyncHandler  = wire[FeatureFlagsSyncHandlerImpl]
  lazy val connectionsSync                            = wire[ConnectionsSyncHandler]
  lazy val gcmSync                                    = wire[PushTokenSyncHandler]
  lazy val typingSync                                 = wire[TypingSyncHandler]
  lazy val richmediaSync                              = wire[RichMediaSyncHandler]
  lazy val messagesSync                               = wire[MessagesSyncHandler]
  lazy val otrSync: OtrSyncHandler                    = wire[OtrSyncHandlerImpl]
  lazy val reactionsSync                              = wire[ReactionsSyncHandler]
  lazy val lastReadSync                               = wire[LastReadSyncHandler]
  lazy val clearedSync                                = wire[ClearedSyncHandler]
  lazy val openGraphSync                              = wire[OpenGraphSyncHandler]
  lazy val integrationsSync: IntegrationsSyncHandler  = wire[IntegrationsSyncHandlerImpl]
  lazy val expiringUsers                              = wire[ExpiredUsersService]
  lazy val propertiesSyncHandler                      = wire[PropertiesSyncHandler]
  lazy val foldersSyncHandler                         = wire[FoldersSyncHandler]
  lazy val propertiesService: PropertiesService       = wire[PropertiesServiceImpl]
  lazy val fcmNotStatsService                         = wire[FCMNotificationStatsServiceImpl]
  lazy val trackingSync                               = wire[TrackingSyncHandler]

  lazy val eventPipeline: EventPipeline = new EventPipelineImpl(Vector(), eventScheduler.enqueue)

  lazy val assets2Module = ZMessaging.assets2Module

  lazy val assetService = {
    val lruCacheDirectory = new File(context.getCacheDir, s"assets_${selfUserId.str}")
    val rawCacheDirectory = new File(context.getCacheDir, s"raw_assets_${selfUserId.str}")

    IoUtils.createDirectory(lruCacheDirectory)
    IoUtils.createDirectory(rawCacheDirectory)

    new AssetServiceImpl(
      storage.assetsStorage,
      storage.rawAssetStorage,
      storage.inProgressAssetStorage,
      assets2Module.assetDetailsService,
      assets2Module.assetPreviewService,
      new AssetRestrictionsServiceImpl(assets2Module.uriHelper, teamId),
      assets2Module.uriHelper,
      new AssetContentCacheImpl(
        cacheDirectory = lruCacheDirectory,
        directorySizeThreshold = 1024 * 1024 * 200L,
        sizeCheckingInterval = 30.seconds
      )(Threading.IO),
      new UploadAssetContentCacheImpl(rawCacheDirectory)(Threading.IO),
      assetClient,
      sync
    )
  }

  lazy val eventScheduler = {

    new EventScheduler(
      Stage(Sequential)(
        connection.connectionEventsStage,
        users.userUpdateEventsStage,
        users.userDeleteEventsStage,
        calling.callMessagesStage,
        teams.eventsProcessingStage,
        typing.typingEventStage,
        otrClientsService.otrClientsProcessingStage,
        pushToken.eventProcessingStage,
        convOrder.conversationOrderEventsStage,
        conversations.convStateEventProcessingStage,
        msgEvents.messageEventProcessingStage,
        notifications.messageNotificationEventsStage,
        notifications.connectionNotificationEventStage,
        genericMsgs.eventProcessingStage,
        foldersService.eventProcessingStage,
        propertiesService.eventProcessor
      )
    )
  }

  private lazy val blockStreamsWhenProcessing = push.processing(messagesStorage.blockStreams)

  // force loading of services which should run on start
  {
    conversations
    users
    expiringUsers
    callLogging

    push // connect on start
    notifications
    blockStreamsWhenProcessing

    // services listening for storage updates
    richmedia
    ephemeral
    receipts

    tempFiles
    recordAndPlay
    messagesIndexStorage
    verificationUpdater
    propertiesService

    rolesService.ensureDefaultRoles()

    reporting.addStateReporter { pw =>
      Future {
        userPrefs foreachCached {
          case KeyValueData(k, v) if k.contains("time") |
                                     (Try(v.toLong).toOption.isDefined && v.length == 13) => pw.println(s"$k: ${Instant.ofEpochMilli(Try(v.toLong).getOrElse(0L))}")
          case KeyValueData(k, v) => pw.println(s"$k: $v")
        }
      }
    }
  }
}

/**
  * All vars are there for tests only - do not modify from production code!!
  */
object ZMessaging extends DerivedLogTag { self =>

  def accountTag[A: reflect.Manifest](userId: UserId): LogTag = LogTag(s"${implicitly[reflect.Manifest[A]].runtimeClass.getSimpleName}#${userId.str.take(8)}")

  private[waz] var context: Context = _

  private var prefs:               GlobalPreferences = _
  private var googleApi:           GoogleApi = _
  private var backend:             BackendConfig = _
  private var syncRequests:        SyncRequestService = _
  private var notificationsUi:     NotificationUiController = _
  private var assets2Module:       Assets2Module = _
  private var fileRestrictionList: FileRestrictionList = _
  private var defaultProxyDetails: ProxyDetails = _

  //var for tests - and set here so that it is globally available without the need for DI
  var clock = Clock.systemUTC()

  private lazy val _global: GlobalModule = new GlobalModuleImpl(
    context, backend, prefs, googleApi, syncRequests, notificationsUi, fileRestrictionList, defaultProxyDetails
  )
  private lazy val ui: UiModule = new UiModule(_global)

  //Try to avoid using these - map from the futures instead.
  var currentUi:       UiModule = _
  var currentGlobal:   GlobalModule = _
  var currentAccounts: AccountsService = _

  var globalReady = Promise[GlobalModule]()

  def globalModule:    Future[GlobalModule]    = globalReady.future
  def accountsService: Future[AccountsService] = globalModule.map(_.accountsService)(Threading.Background)

  lazy val beDrift = _global.prefs.preference(GlobalPreferences.BackendDrift).signal
  def currentBeDrift = beDrift.currentValue.getOrElse(Duration.ZERO)

  //TODO - we should probably just request the entire GlobalModule from the UI here
  def onCreate(context:             Context,
               beConfig:            BackendConfig,
               prefs:               GlobalPreferences,
               googleApi:           GoogleApi,
               syncRequests:        SyncRequestService,
               notificationUi:      NotificationUiController,
               assets2:             Assets2Module,
               fileRestrictionList: FileRestrictionList,
               defaultProxyDetails: ProxyDetails
              ) = {
    Threading.assertUiThread()

    if (this.currentUi == null) {
      this.context = context.getApplicationContext
      this.backend = beConfig
      this.prefs = prefs
      this.googleApi = googleApi
      this.syncRequests = syncRequests
      this.notificationsUi = notificationUi
      this.assets2Module = assets2
      this.fileRestrictionList = fileRestrictionList
      this.defaultProxyDetails = defaultProxyDetails

      currentUi = ui
      currentGlobal = _global
      currentAccounts = currentGlobal.accountsService

      globalReady.success(_global)

      Threading.Background {
        Locales.preloadTransliterator()
        ContentSearchQuery.preloadTransliteration()
      } // "preload"... - this should be very fast, normally, but slows down to 10 to 20 seconds when multidexed...
    }
  }
}

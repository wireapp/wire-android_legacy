/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient

import java.io.File
import java.util.Calendar

import android.app.{Activity, ActivityManager, Notification, NotificationChannel, NotificationManager}
import android.content.{Context, ContextWrapper}
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.{Build, PowerManager, Vibrator}
import android.renderscript.RenderScript
import android.telephony.TelephonyManager
import android.util.Log
import androidx.fragment.app.{FragmentActivity, FragmentManager}
import androidx.multidex.MultiDexApplication
import com.evernote.android.job.{Job, JobCreator, JobManager}
import com.waz.api.NetworkMode
import com.waz.background.WorkManagerSyncRequestService
import com.waz.content.Preferences.PrefKey
import com.waz.content._
import com.waz.jobs.PushTokenCheckJob
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log._
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service._
import com.waz.service.assets._
import com.waz.service.call.GlobalCallingService
import com.waz.service.conversation._
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService.{ForceSync, SyncHistory}
import com.waz.service.teams.{FeatureConfigsService, TeamsService}
import com.waz.service.tracking.TrackingService
import com.waz.services.fcm.FetchJob
import com.waz.services.gps.GoogleApiImpl
import com.waz.services.websocket.WebSocketController
import com.waz.sync.client.CustomBackendClient
import com.waz.sync.{SyncHandler, SyncRequestService}
import com.waz.threading.Threading
import com.waz.utils.SafeBase64
import com.waz.utils.wrappers.GoogleApi
import com.waz.zclient.appentry.controllers.{CreateTeamController, InvitationsController}
import com.waz.zclient.assets.{AndroidUriHelper, AssetDetailsServiceImpl, AssetPreviewServiceImpl}
import com.waz.zclient.calling.controllers.{CallController, CallStartController}
import com.waz.zclient.camera.controllers.{AndroidCameraFactory, GlobalCameraController}
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers._
import com.waz.zclient.common.controllers.global.{AccentColorController, ClientsController, KeyboardController, PasswordController, SodiumHandler}
import com.waz.zclient.controllers._
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation.IConfirmationController
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.controllers.navigation.INavigationController
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.conversation.{ConversationController, ReplyController}
import com.waz.zclient.conversationlist.{ConversationListController, FolderStateController}
import com.waz.zclient.cursor.CursorController
import com.waz.zclient.deeplinks.DeepLinkService
import com.waz.zclient.legalhold.{LegalHoldApprovalHandler, LegalHoldController, LegalHoldStatusChangeListener}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.controllers.{MessageActionsController, NavigationController}
import com.waz.zclient.messages.{LikesController, MessagePagedListController, MessageViewFactory, MessagesController, UsersController}
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.{AndroidNotificationsManager, IncomingCallNotificationsChannelId, MessageNotificationsChannelId, OngoingNotificationsChannelId, PingNotificationsChannelId}
import com.waz.zclient.notifications.controllers._
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.preferences.PreferencesController
import com.waz.zclient.search.SearchController
import com.waz.zclient.security.{ActivityLifecycleCallback, SecurityPolicyChecker}
import com.waz.zclient.tracking.{CountlyApi, CrashController, GlobalTrackingController, UiTrackingController}
import com.waz.zclient.utils.{AndroidBase64Delegate, BackStackNavigator, BackendController, ExternalFileSharing, LocalThumbnailCache, RingtoneUtils, UiStorage}
import com.waz.zclient.views.DraftMap
import com.wire.signals.{EventContext, Signal}
import org.threeten.bp.Clock

import scala.concurrent.Future

object WireApplication extends DerivedLogTag {
  var APP_INSTANCE: WireApplication = _

  def isInitialized: Boolean =
    if (Option(APP_INSTANCE).isEmpty) false
    else APP_INSTANCE.isInitialized

  def ensureInitialized(): Boolean =
    if (Option(APP_INSTANCE).isEmpty) false // too early
    else APP_INSTANCE.ensureInitialized()

  type AccountToAssetsStorage = (UserId) => Future[Option[AssetStorage]]
  type AccountToUsersStorage = (UserId) => Future[Option[UsersStorage]]
  type AccountToConvsStorage = (UserId) => Future[Option[ConversationStorage]]
  type AccountToConvsService = (UserId) => Future[Option[ConversationsService]]
  type AccountToUserService = (UserId) => Future[Option[UserService]]

  lazy val Global = new Module {

    verbose(l"Global module created!!")

    implicit lazy val ctx:          WireApplication = WireApplication.APP_INSTANCE
    implicit lazy val wContext:     WireContext     = ctx

    //Android services
    bind [ActivityManager]      to ctx.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    bind [PowerManager]         to ctx.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    bind [Vibrator]             to ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
    bind [AudioManager]         to ctx.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    bind [SensorManager]        to ctx.getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]
    bind [NotificationManager]  to ctx.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    bind [TelephonyManager]     to ctx.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    bind [RenderScript]         to RenderScript.create(ctx)

    def controllerFactory = APP_INSTANCE.asInstanceOf[ZApplication].getControllerFactory

    bind [NotificationManagerWrapper] to new AndroidNotificationsManager(inject[NotificationManager])

    //SE Services
    bind [GlobalModule]                   to ZMessaging.currentGlobal
    bind [AccountsService]                to ZMessaging.currentAccounts
    bind [Signal[AccountsService]]        to Signal.from(ZMessaging.accountsService) //use for early-initialised classes
    bind [BackendConfig]                  to inject[GlobalModule].backend
    bind [AccountStorage]                 to inject[GlobalModule].accountsStorage
    bind [TeamsStorage]                   to inject[GlobalModule].teamsStorage
    bind [SSOService]                     to inject[GlobalModule].ssoService
    bind [GoogleApi]                      to inject[GlobalModule].googleApi
    bind [GlobalCallingService]           to inject[GlobalModule].calling
    bind [SyncHandler]                    to inject[GlobalModule].syncHandler
    bind [Clock]                          to ZMessaging.clock

    bind [Signal[Option[AccountManager]]] to ZMessaging.currentAccounts.activeAccountManager
    bind [Signal[AccountManager]]         to inject[Signal[Option[AccountManager]]].collect { case Some(am) => am }
    bind [Signal[Option[ZMessaging]]]     to ZMessaging.currentUi.currentZms
    bind [Signal[ZMessaging]]             to inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
    bind [GlobalPreferences]              to inject[GlobalModule].prefs
    bind [NetworkModeService]             to inject[GlobalModule].network
    bind [UiLifeCycle]                    to inject[GlobalModule].lifecycle
    bind [TrackingService]                to inject[GlobalModule].trackingService
    bind [PermissionsService]             to inject[GlobalModule].permissions
    bind [MetaDataService]                to inject[GlobalModule].metadata
    bind [LogsService]                    to inject[GlobalModule].logsService
    bind [CustomBackendClient]            to inject[GlobalModule].customBackendClient

    import com.waz.threading.Threading.Implicits.Background
    bind [AccountToAssetsStorage] to (userId => inject[AccountsService].getZms(userId).map(_.map(_.assetsStorage)))
    bind [AccountToUsersStorage]  to (userId => inject[AccountsService].getZms(userId).map(_.map(_.usersStorage)))
    bind [AccountToConvsStorage]  to (userId => inject[AccountsService].getZms(userId).map(_.map(_.convsStorage)))
    bind [AccountToConvsService]  to (userId => inject[AccountsService].getZms(userId).map(_.map(_.conversations)))
    bind [AccountToUserService]   to (userId => inject[AccountsService].getZms(userId).map(_.map(_.users)))

    // the current user's id
    bind [Signal[Option[UserId]]] to inject[Signal[AccountsService]].flatMap(_.activeAccountId)
    bind [Signal[UserId]]         to inject[Signal[ZMessaging]].map(_.selfUserId)
    bind [Signal[Option[TeamId]]] to inject[Signal[ZMessaging]].map(_.teamId)

    // the current domain should be constant and the same for all current user accounts on the same device
    // but technically we can't guarantee it, hence every time it's used we need to check its value
    // (so, `toProvider`, not `to`)
    bind[Domain] toProvider inject[Signal[ZMessaging]].map(_.selfDomain).currentValue.getOrElse(Domain.Empty)

    // services  and storages of the current zms
    bind [Signal[ConversationsService]]          to inject[Signal[ZMessaging]].map(_.conversations)
    bind [Signal[ConversationsContentUpdater]]   to inject[Signal[ZMessaging]].map(_.convsContent)
    bind [Signal[SelectedConversationService]]   to inject[Signal[ZMessaging]].map(_.selectedConv)
    bind [Signal[ConversationsUiService]]        to inject[Signal[ZMessaging]].map(_.convsUi)
    bind [Signal[UserService]]                   to inject[Signal[ZMessaging]].map(_.users)
    bind [Signal[UserSearchService]]             to inject[Signal[ZMessaging]].map(_.userSearch)
    bind [Signal[ConversationStorage]]           to inject[Signal[ZMessaging]].map(_.convsStorage)
    bind [Signal[UsersStorage]]                  to inject[Signal[ZMessaging]].map(_.usersStorage)
    bind [Signal[MembersStorage]]                to inject[Signal[ZMessaging]].map(_.membersStorage)
    bind [Signal[ConversationRolesService]]      to inject[Signal[ZMessaging]].map(_.rolesService)
    bind [Signal[OtrClientsStorage]]             to inject[Signal[ZMessaging]].map(_.otrClientsStorage)
    bind [Signal[AssetStorage]]                  to inject[Signal[ZMessaging]].map(_.assetsStorage)
    bind [Signal[AssetService]]                  to inject[Signal[ZMessaging]].map(_.assetService)
    bind [Signal[MessagesStorage]]               to inject[Signal[ZMessaging]].map(_.messagesStorage)
    bind [Signal[MessagesService]]               to inject[Signal[ZMessaging]].map(_.messages)
    bind [Signal[IntegrationsService]]           to inject[Signal[ZMessaging]].map(_.integrations)
    bind [Signal[UserPreferences]]               to inject[Signal[ZMessaging]].map(_.userPrefs)
    bind [Signal[MessageAndLikesStorage]]        to inject[Signal[ZMessaging]].map(_.msgAndLikes)
    bind [Signal[ReadReceiptsStorage]]           to inject[Signal[ZMessaging]].map(_.readReceiptsStorage)
    bind [Signal[ReactionsStorage]]              to inject[Signal[ZMessaging]].map(_.reactionsStorage)
    bind [Signal[FoldersStorage]]                to inject[Signal[ZMessaging]].map(_.foldersStorage)
    bind [Signal[ConversationFoldersStorage]]    to inject[Signal[ZMessaging]].map(_.conversationFoldersStorage)
    bind [Signal[FoldersService]]                to inject[Signal[ZMessaging]].map(_.foldersService)
    bind [Signal[TeamsService]]                  to inject[Signal[ZMessaging]].map(_.teams)
    bind [Signal[FeatureConfigsService]]         to inject[Signal[ZMessaging]].map(_.featureConfigs)
    bind [Signal[MessageIndexStorage]]           to inject[Signal[ZMessaging]].map(_.messagesIndexStorage)
    bind [Signal[ConnectionService]]             to inject[Signal[ZMessaging]].map(_.connection)
    bind [Signal[ButtonsStorage]]                to inject[Signal[ZMessaging]].map(_.buttonsStorage)
    bind [Signal[LegalHoldService]]              to inject[Signal[ZMessaging]].map(_.legalHold)

    // old controllers
    // TODO: remove controller factory, reimplement those controllers
    bind [IControllerFactory]            toProvider controllerFactory
    bind [IPickUserController]           toProvider controllerFactory.getPickUserController
    bind [IConversationScreenController] toProvider controllerFactory.getConversationScreenController
    bind [INavigationController]         toProvider controllerFactory.getNavigationController
    bind [IUserPreferencesController]    toProvider controllerFactory.getUserPreferencesController
    bind [ISingleImageController]        toProvider controllerFactory.getSingleImageController
    bind [ISlidingPaneController]        toProvider controllerFactory.getSlidingPaneController
    bind [IGlobalLayoutController]       toProvider controllerFactory.getGlobalLayoutController
    bind [ILocationController]           toProvider controllerFactory.getLocationController
    bind [ICameraController]             toProvider controllerFactory.getCameraController
    bind [IConfirmationController]       toProvider controllerFactory.getConfirmationController

    // global controllers
    bind [BackendController]       to new BackendController()
    bind [WebSocketController]     to new WebSocketController
    bind [CrashController]         to new CrashController
    bind [AccentColorController]   to new AccentColorController()
    bind [PasswordController]      to new PasswordController()
    bind [SodiumHandler]           to new SodiumHandler()
    bind [CallController]          to new CallController()
    bind [GlobalCameraController]  to new GlobalCameraController(new AndroidCameraFactory)
    bind [SoundController]         to new SoundControllerImpl()
    bind [ThemeController]         to new ThemeController
    bind [SpinnerController]       to new SpinnerController()
    bind [SearchController]        to new SearchController()
    bind [LegalHoldController]     to new LegalHoldController()

    bind [UiStorage] to new UiStorage()

    bind [SyncRequestService]  to new WorkManagerSyncRequestService()

    //notifications
    bind [MessageNotificationsController]  to new MessageNotificationsController()
    bind [ImageNotificationsController]    to new ImageNotificationsController()
    bind [CallingNotificationsController]  to new CallingNotificationsController()

    bind [GlobalTrackingController]        to new GlobalTrackingController()
    bind [PreferencesController]           to new PreferencesController()
    bind [UserAccountsController]          to new UserAccountsController()

    bind [LocalThumbnailCache]              to LocalThumbnailCache(ctx)

    bind [SharingController]               to new SharingController()
    bind [ConversationController]          to new ConversationController()

    bind [NavigationController]            to new NavigationController()
    bind [InvitationsController]           to new InvitationsController()
    bind [ClientsController]               to new ClientsController()
    bind [CreateTeamController]            to new CreateTeamController()
    bind [CreateConversationController]    to new CreateConversationController()

    // current conversation data
    bind [Signal[ConversationData]] to inject[ConversationController].currentConv

    // selected conversation id
    bind [Signal[Option[ConvId]]] to inject[Signal[SelectedConversationService]].flatMap(_.selectedConversationId)

    // accent color
    bind [Signal[AccentColor]] to inject[AccentColorController].accentColor

    // drafts
    bind [DraftMap] to new DraftMap()

    bind [MessagesController]        to new MessagesController()

    bind [ClipboardUtils]       to new ClipboardUtils(ctx)
    bind [ExternalFileSharing]  to new ExternalFileSharing(ctx)
    bind [FileRestrictionList]  to new FileRestrictionList(BuildConfig.FILE_RESTRICTION_LIST, BuildConfig.FILE_RESTRICTION_ENABLED)

    bind [DeepLinkService]      to new DeepLinkService()

    bind [UriHelper] to new AndroidUriHelper(ctx)

    bind[MediaRecorderController] to new MediaRecorderControllerImpl(ctx)

    bind[ActivityLifecycleCallback] to new ActivityLifecycleCallback()

    bind[SecurityPolicyChecker] to new SecurityPolicyChecker()

    bind[FolderStateController] to new FolderStateController()

    bind[LegalHoldApprovalHandler] to new LegalHoldApprovalHandler()
    bind[LegalHoldStatusChangeListener] to new LegalHoldStatusChangeListener()

    KotlinServices.INSTANCE.init(ctx)
  }

  def controllers(implicit ctx: WireContext) = new Module {

    private implicit val eventContext = ctx.eventContext

    bind [Activity] to {
      def getActivity(ctx: Context): Activity = ctx match {
        case a: Activity => a
        case w: ContextWrapper => getActivity(w.getBaseContext)
      }
      getActivity(ctx)
    }
    bind [FragmentManager] to inject[Activity].asInstanceOf[FragmentActivity].getSupportFragmentManager

    bind [KeyboardController]        to new KeyboardController()
    bind [CallStartController]       to new CallStartController()
    bind [AssetsController]          to new AssetsController()
    bind [BrowserController]         to new BrowserController()
    bind [MessageViewFactory]        to new MessageViewFactory()
    bind [ReplyController]           to new ReplyController()

    bind [ScreenController]          to new ScreenController()
    bind [MessageActionsController]  to new MessageActionsController()
    bind [LikesController]           to new LikesController()
    bind [CollectionController]      to new CollectionController()
    bind [BackStackNavigator]        to new BackStackNavigator()

    bind [CursorController]             to new CursorController()
    bind [ConversationListController]   to new ConversationListController()
    bind [ParticipantsController]       to new ParticipantsController()
    bind [UsersController]              to new UsersController()

    bind [ErrorsController]             to new ErrorsController()

    /**
      * Since tracking controllers will immediately instantiate other necessary controllers, we keep them separated
      * based on the activity responsible for generating their events (we don't want to instantiate an uneccessary
      * MessageActionsController in the CallingActivity, for example
      */
    bind [UiTrackingController]    to new UiTrackingController()

    bind[MessagePagedListController] to new MessagePagedListController()

    bind [FeatureConfigsController] to new FeatureConfigsController()
  }

  def clearOldVideoFiles(context: Context): Unit = {
    val oneWeekAgo = Calendar.getInstance
    oneWeekAgo.add(Calendar.DAY_OF_YEAR, -7)
    Option(context.getExternalCacheDir).foreach { dir =>
      Option(dir.listFiles).fold[List[File]](Nil)(_.toList).foreach { file =>
        val fileName = file.getName
        val fileModified = Calendar.getInstance()
        fileModified.setTimeInMillis(file.lastModified)
        if (fileName.startsWith("VID_") &&
            fileName.endsWith(".mp4") &&
            fileModified.before(oneWeekAgo)
        ) file.delete()
      }
    }
  }
}

class WireApplication extends MultiDexApplication with WireContext with Injectable {
  type NetworkSignal = Signal[NetworkMode]
  import WireApplication._

  import scala.concurrent.ExecutionContext.Implicits.global

  WireApplication.APP_INSTANCE = this

  override def eventContext: EventContext = EventContext.Global

  lazy val module: Injector = Global

  protected var controllerFactory: IControllerFactory = _

  def contextModule(ctx: WireContext): Injector = controllers(ctx)

  override def onCreate(): Unit = {
    super.onCreate()

    SafeBase64.setDelegate(new AndroidBase64Delegate)

    ZMessaging.globalReady.future.foreach { global =>
      InternalLog.setLogsService(inject[LogsService])
      InternalLog.add(new AndroidLogOutput(showSafeOnly = BuildConfig.SAFE_LOGGING))
      InternalLog.add(new BufferedLogOutput(
        baseDir = getApplicationContext.getApplicationInfo.dataDir,
        showSafeOnly = BuildConfig.SAFE_LOGGING)
      )
      global.trackingService.isTrackingEnabled.head.foreach(_ => CountlyApi.applicationOnCreate())(Threading.Background)
      global.httpProxy.foreach(KotlinServices.INSTANCE.setHttpProxy)
    }

    verbose(l"onCreate")

    controllerFactory = new ControllerFactory(getApplicationContext)

    ensureInitialized()
  }

  private[waz] def isInitialized: Boolean = Option(ZMessaging.currentGlobal).isDefined

  private[waz] def ensureInitialized(): Boolean =
    if (isInitialized) true // the app is initialized, nothing to do here
    else
      try {
        inject[BackendController].getStoredBackendConfig.fold(false){ config =>
          ensureInitialized(config)
          true
        }
      } catch {
        case t: Throwable =>
          Log.e(WireApplication.getClass.getName, "Failed to initialize the app", t)
          false
      }

  def ensureInitialized(backend: BackendConfig): Unit = {
    JobManager.create(this).addJobCreator(new JobCreator {
      override def create(tag: String): Job =
        if      (tag.contains(FetchJob.Tag))          new FetchJob
        else if (tag.contains(PushTokenCheckJob.Tag)) new PushTokenCheckJob
        else    null
    })

    val prefs = GlobalPreferences(this)
    val googleApi = GoogleApiImpl(this, backend, prefs)

    val assets2Module = new Assets2Module {
      override def uriHelper: UriHelper = inject[UriHelper]
      override def assetDetailsService: AssetDetailsService =
        new AssetDetailsServiceImpl(uriHelper)(getApplicationContext, Threading.IO)
      override def assetPreviewService: AssetPreviewService =
        new AssetPreviewServiceImpl()(getApplicationContext, Threading.IO)
    }

    ZMessaging.onCreate(
      this,
      backend,
      prefs,
      googleApi,
      null, //TODO: Use sync engine's version for now
      inject[MessageNotificationsController],
      assets2Module,
      inject[FileRestrictionList],
      ProxyDetails(BuildConfig.HTTP_PROXY_URL, BuildConfig.HTTP_PROXY_PORT.toInt)
    )

    val activityLifecycleCallback = inject[ActivityLifecycleCallback]
    // we're unable to check if the callback is already registered - we have to re-register it to be sure
    unregisterActivityLifecycleCallbacks(activityLifecycleCallback)
    registerActivityLifecycleCallbacks(activityLifecycleCallback)

    inject[NotificationManagerWrapper]
    inject[ImageNotificationsController]
    inject[CallingNotificationsController]
    inject[MessageNotificationsController].initialize()
    activityLifecycleCallback.appInBackground.map(!_._1).foreach {
      case true =>
        inject[Signal[ZMessaging]].head.foreach(_.push.syncNotifications(SyncHistory(ForceSync)))
      case false =>
    }

//    //TODO [AN-4942] - is this early enough for app launch events?
    inject[GlobalTrackingController]
    inject[CrashController] //needs to register crash handler
    inject[ThemeController]
    inject[PreferencesController]
    Future(clearOldVideoFiles(getApplicationContext))(Threading.Background)
    Future(checkForPlayServices(prefs, googleApi))(Threading.Background)

    inject[SecurityPolicyChecker]
    inject[LegalHoldStatusChangeListener]

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) buildNotificationChannels(prefs)
  }

  private def buildNotificationChannels(prefs: GlobalPreferences): Unit = {
    def getSound(pref: PrefKey[String], default: Int): Future[Uri] = {
      for {
        uriString <- prefs(pref).apply()
      } yield uriString match {
        case ""  => RingtoneUtils.getUriForRawId(this, default)
        case str => Uri.parse(str)
      }
    }

    lazy val notificationManager = inject[NotificationManager]

    def recreateNotificationChannel(id:            String,
                                    nameId:        Int,
                                    descriptionId: Int,
                                    importance:    Int = NotificationManager.IMPORTANCE_DEFAULT,
                                    vibration:     Boolean = false,
                                    showBadge:     Boolean = false,
                                    sound:         Option[Uri] = None,
                                    visibility:    Int = Notification.VISIBILITY_PUBLIC): Unit = {
      val ch = new NotificationChannel(id, getString(nameId), importance)
      ch.setDescription(getString(descriptionId))
      ch.enableVibration(vibration)
      ch.setShowBadge(showBadge)
      ch.setLockscreenVisibility(visibility)
      ch.enableLights(true)
      sound.foreach(uri => ch.setSound(uri, Notification.AUDIO_ATTRIBUTES_DEFAULT))

      notificationManager.deleteNotificationChannel(id)
      notificationManager.createNotificationChannel(ch)
    }

    val recreateChannelsPref = prefs.preference(GlobalPreferences.RecreateChannels)

    for {
      true      <- recreateChannelsPref()
      _         <- recreateChannelsPref := false
      vibration <- prefs.preference(GlobalPreferences.VibrateEnabled).apply()
      msgSound  <- getSound(GlobalPreferences.TextTone, R.raw.new_message_gcm)
      pingSound <- getSound(GlobalPreferences.PingTone, R.raw.ping_from_them)
    } yield {
      recreateNotificationChannel(
        id            = OngoingNotificationsChannelId,
        nameId        = R.string.ongoing_channel_name,
        descriptionId = R.string.ongoing_channel_description,
        importance    = NotificationManager.IMPORTANCE_LOW,
        vibration     = vibration
      )

      recreateNotificationChannel(
        id            = IncomingCallNotificationsChannelId,
        nameId        = R.string.incoming_call_notifications_channel_name,
        descriptionId = R.string.incoming_call_notifications_channel_name,
        importance    = NotificationManager.IMPORTANCE_MAX,
        vibration     = vibration
      )

      recreateNotificationChannel(
        id            = MessageNotificationsChannelId,
        nameId        = R.string.message_notifications_channel_name,
        descriptionId = R.string.message_notifications_channel_description,
        importance    = NotificationManager.IMPORTANCE_MAX,
        vibration     = vibration,
        sound         = Some(msgSound),
        visibility    = Notification.VISIBILITY_PRIVATE,
        showBadge     = true
      )

      recreateNotificationChannel(
        id            = PingNotificationsChannelId,
        nameId        = R.string.ping_notifications_channel_name,
        descriptionId = R.string.ping_notifications_channel_description,
        importance    = NotificationManager.IMPORTANCE_MAX,
        vibration     = vibration,
        sound         = Some(pingSound),
        visibility    = Notification.VISIBILITY_PRIVATE,
        showBadge     = true
      )
    }
  }

  lazy val messageNotificationsController: MessageNotificationsController = inject[MessageNotificationsController]

  private def checkForPlayServices(prefs: GlobalPreferences, googleApi: GoogleApi): Unit =
    prefs(GlobalPreferences.CheckedForPlayServices).apply().foreach {
      case false =>
        verbose(l"never checked for play services")
        googleApi.isGooglePlayServicesAvailable.head.foreach { gpsAvailable =>
          for {
            _ <- if (!gpsAvailable) prefs(GlobalPreferences.WsForegroundKey) := true
                 else updateWebSocketForBuildConfig(prefs)
            _ <- prefs(GlobalPreferences.CheckedForPlayServices) := true
          } yield ()
        }
      case true => updateWebSocketForBuildConfig(prefs)
    }

  private def updateWebSocketForBuildConfig(prefs: GlobalPreferences): Future[Unit] =
    for {
      checked <- prefs(GlobalPreferences.CheckedWebSocketConfig).apply()
      _       <- if (!checked && BuildConfig.KEEP_WEBSOCKET_ON) prefs(GlobalPreferences.WsForegroundKey) := true
                 else Future.successful(())
      _       <- prefs(GlobalPreferences.CheckedWebSocketConfig) := true
    } yield ()

  override def onTerminate(): Unit = {
    controllerFactory.tearDown()
    controllerFactory = null
    RenderScript.releaseAllContexts()
    InternalLog.flush()

    super.onTerminate()
  }
}


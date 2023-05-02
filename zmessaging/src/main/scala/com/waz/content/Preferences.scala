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
package com.waz.content

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.{Context, SharedPreferences}
import com.waz.content.Preferences.Preference.PrefCodec
import com.waz.content.Preferences.{PrefKey, Preference}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.media.manager.context.IntensityLevel
import com.waz.model.ConversationData.LegalHoldStatus
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.AccountManager.ClientRegistrationState
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.{CachedStorageImpl, CirceJSONSupport, JsonDecoder, JsonEncoder, TrimmingLruCache, returning}
import com.waz.zms.BuildConfig
import com.wire.signals.{DispatchQueue, SerialDispatchQueue, Signal, SourceSignal}
import org.json.JSONObject
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Preferences {

  implicit protected val dispatcher: ExecutionContext
  implicit protected val logTag: LogTag

  //protected for test prefs
  protected var cache = Map.empty[PrefKey[_], Preference[_]]

  final def apply[A: PrefCodec](key: PrefKey[A]): Preference[A] = preference(key)

  final def preference[A: PrefCodec](key: PrefKey[A]): Preference[A] =
    cache.getOrElse(key, returning(buildPreference(key))(cache += key -> _)).asInstanceOf[Preference[A]]

  protected def buildPreference[A: PrefCodec](key: PrefKey[A]): Preference[A] =
    Preference[A](this, key)

  protected def getValue[A: PrefCodec](key: PrefKey[A]): Future[A]

  def setValue[A: PrefCodec](key: PrefKey[A], value: A): Future[Unit]
}

object Preferences {

  case class Preference[A: PrefCodec](private val prefs: Preferences, key: PrefKey[A]) extends DerivedLogTag {

    import Threading.Implicits.Background

    def apply(): Future[A] = prefs.getValue(key)

    def update(value: A): Future[Unit] = prefs.setValue(key, value)
      .map { _ => signal.publish(value, Threading.Background) }
      .recoverWith { case exception =>
        error(l"Error while updating signal preference $key to value $value. Exception is: $exception")
        Future.failed(exception)
      }

    def :=(value: A): Future[Unit] = update(value)

    def mutate(f: A => A): Future[Unit] = apply().flatMap(cur => update(f(cur)))

    def flatMutate(f: A => Future[A]) = apply().flatMap(cur => f(cur).flatMap(n => update(n)))

    lazy val signal: SourceSignal[A] = {
      returning(Signal[A]()) { s =>
        apply().map { v =>
          s.publish(v, Threading.Background)
        }.recoverWith { case exception =>
          error(l"Error while getting signal with preference key $key. Exception is: $exception")
          Future.failed(exception)
        }
      }
    }
  }

  object Preference {

    trait PrefCodec[A] {
      def encode(v: A): String

      def decode(str: String): A

      val default: A
    }

    //TODO maybe we can use JSON codecs at some point...
    object PrefCodec {
      def apply[A](enc: A => String, dec: String => A, defaultVal: A): PrefCodec[A] = new PrefCodec[A] {
        override def encode(v: A): String = enc(v)

        override def decode(str: String): A = dec(str)

        override val default = defaultVal
      }

      implicit lazy val StrCodec = apply[String](identity, identity, "")
      implicit lazy val IntCodec = apply[Int](String.valueOf, java.lang.Integer.parseInt, 0)
      implicit lazy val LongCodec = apply[Long](String.valueOf, java.lang.Long.parseLong, 0)
      implicit lazy val BooleanCodec = apply[Boolean](String.valueOf, java.lang.Boolean.parseBoolean, false)

      implicit def idCodec[A: Id]: PrefCodec[A] = apply[A](implicitly[Id[A]].encode, implicitly[Id[A]].decode, implicitly[Id[A]].empty)

      implicit def optCodec[A: PrefCodec]: PrefCodec[Option[A]] = apply[Option[A]](_.fold("")(implicitly[PrefCodec[A]].encode), { str => if (str == "") None else Some(implicitly[PrefCodec[A]].decode(str)) }, None)

      implicit lazy val InstantCodec = apply[Instant](d => String.valueOf(d.toEpochMilli), s => Instant.ofEpochMilli(java.lang.Long.parseLong(s)), Instant.EPOCH)
      implicit lazy val DurationCodec = apply[Duration](d => String.valueOf(d.toMillis), s => Duration.ofMillis(java.lang.Long.parseLong(s)), Duration.ZERO)

      private def parseFiniteDurationOpt(s: String): Option[FiniteDuration] = java.lang.Long.parseLong(s) match {
        case 0 => None
        case e => Some(e.millis)
      }

      implicit lazy val SetCodec = apply[Set[Int]](s => s.mkString(","), s => if (s.isEmpty) Set.empty[Int] else s.split(",").map(java.lang.Integer.parseInt).toSet, Set.empty[Int])

      implicit lazy val FiniteDurationCodec = apply[Option[FiniteDuration]](d => String.valueOf(d.getOrElse(0.millis).toMillis), parseFiniteDurationOpt, None)

      implicit lazy val AuthTokenCodec = apply[Option[AccessToken]](
        { t => optCodec[String].encode(t map AccessToken.Encoder.apply map (_.toString)) },
        { s => optCodec[String].decode(s) map (new JSONObject(_)) map (AccessToken.Decoder.apply(_)) },
        None)
      implicit lazy val AuthCookieCodec = apply[Cookie](_.str, Cookie, Cookie(""))

      //TODO use an enumcodec somehow
      implicit lazy val IntensityLevelCodec = apply[IntensityLevel](_.toString, IntensityLevel.valueOf, IntensityLevel.FULL)

      import com.waz.service.AccountManager.ClientRegistrationState._

      implicit lazy val SelfClientIdCodec = apply[ClientRegistrationState]({
        case Unregistered => "Unregistered"
        case PasswordMissing => "PasswordMissing"
        case LimitReached => "LimitReached"
        case Registered(id) => id.str
      }, {
        case "Unregistered" => Unregistered
        case "PasswordMissing" => PasswordMissing
        case "LimitReached" => LimitReached
        case id => Registered(ClientId(id))
      }, Unregistered)

      implicit lazy val EmailAddressCodec = apply[EmailAddress](_.str, EmailAddress(_), EmailAddress(""))
      implicit lazy val PhoneNumberCodec = apply[PhoneNumber](_.str, PhoneNumber(_), PhoneNumber(""))

      implicit lazy val UserInfoCodec = apply[UserInfo](JsonEncoder.encode(_).toString, JsonDecoder.decode[UserInfo], null.asInstanceOf[UserInfo])

      implicit lazy val ConversationFoldersUiStateCodec = apply[Map[FolderId, Boolean]](ConversationFoldersUiState.encode, ConversationFoldersUiState.decode, Map.empty)

      object ConversationFoldersUiState extends CirceJSONSupport {

        import io.circe.{Decoder, Encoder, ObjectEncoder, parser}

        lazy val encoder: ObjectEncoder[Map[FolderId, Boolean]] = Encoder.encodeMap[FolderId, Boolean]
        lazy val decoder: Decoder[Map[FolderId, Boolean]] = Decoder.decodeMap[FolderId, Boolean]

        def encode(o: Map[FolderId, Boolean]): String = encoder(o).toString()

        def decode(json: String): Map[FolderId, Boolean] =
          parser.decode(json)(decoder).right.toOption.getOrElse(Map.empty)
      }

      implicit lazy val LegalHoldRequestCodec = apply[Option[LegalHoldRequest]](
        { d =>  d.fold("")(r => LegalHoldRequest.Encoder(r).toString) },
        { s => if (s.isEmpty) None else Some(LegalHoldRequest.Decoder(new JSONObject(s))) },
        defaultVal = None
      )

      implicit lazy val LegalHoldStatusCodec = apply[Option[LegalHoldStatus]](
        { d =>  d.fold("")(r => r.value.toString) },
        { s => if (s.isEmpty) None else Try(Integer.parseInt(s)).map(LegalHoldStatus(_)).toOption },
        defaultVal = None
      )
    }

  }

  case class PrefKey[A: PrefCodec](str: String, customDefault: A = null.asInstanceOf[A]) {
    val default = Option(customDefault).getOrElse(implicitly[PrefCodec[A]].default)

    override def toString = s"$str (def: $default)"
  }

}


/**
  * Global preference based on Android SharedPreferences. Note, here we need to save preferences to the correct primitive
  * type in SharedPreferences, as the Android system uses these types by default (e.g., a CheckBoxPreference defined in XML
  * will store a boolean preference in the shared prefs document
  */
class GlobalPreferences(context: Context, val prefs: SharedPreferences) extends Preferences {

  override protected implicit val dispatcher = SerialDispatchQueue(name = "GlobalPreferencesDispatcher")
  override protected implicit val logTag = LogTag[GlobalPreferences]

  def v31AssetsEnabled = false

  //TODO would be nice to hide this, but for now it's fine
  def getFromPref[A: PrefCodec](key: PrefKey[A]) = {
    val codec = implicitly[PrefCodec[A]]
    import PrefCodec._
    (codec match {
      case IntCodec => prefs.getInt(key.str, key.default.asInstanceOf[Int])
      case BooleanCodec => prefs.getBoolean(key.str, key.default.asInstanceOf[Boolean])
      case LongCodec => prefs.getLong(key.str, key.default.asInstanceOf[Long])
      case _ => Option(prefs.getString(key.str, null)).map(codec.decode).getOrElse(key.default)
    }).asInstanceOf[A]
  }

  override protected def buildPreference[A: PrefCodec](key: PrefKey[A]): Preference[A] =
    new Preference[A](this, key) {

      //No need to update the signal. The SharedPreferences Listener will do this for us.
      override def update(value: A) = {
        setValue[A](key, value)
      }

      private def load = getFromPref(key)

      override lazy val signal = new SourceSignal[A](Some(load)) {

        private val listener = new OnSharedPreferenceChangeListener {
          override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, k: String): Unit =
            if (key.str == k) set(Some(load), Some(Threading.Background))
        }

        override def onWire(): Unit = {
          prefs.registerOnSharedPreferenceChangeListener(listener)
          value = Some(load)
        }

        override def onUnwire(): Unit = prefs.unregisterOnSharedPreferenceChangeListener(listener)
      }
    }

  override protected def getValue[A: PrefCodec](key: PrefKey[A]): Future[A] =
    dispatcher(getFromPref[A](key))

  override def setValue[A: PrefCodec](key: PrefKey[A], value: A): Future[Unit] =
    dispatcher {
      import PrefCodec._
      val codec = implicitly[PrefCodec[A]]
      val editor = prefs.edit()
      codec match {
        case IntCodec => editor.putInt(key.str, value.asInstanceOf[Int])
        case BooleanCodec => editor.putBoolean(key.str, value.asInstanceOf[Boolean])
        case LongCodec => editor.putLong(key.str, value.asInstanceOf[Long])
        case _ => editor.putString(key.str, codec.encode(value))
      }
      editor.apply()
    }
}

/**
  * Per-user preference storage in user db.
  */
class UserPreferences(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[String, KeyValueData](
    new TrimmingLruCache(context, Fixed(128)), storage)(KeyValueDataDao, LogTag("KeyValueStorage_Cached")
  ) with Preferences {

  override protected implicit val dispatcher: DispatchQueue = Threading.Background
  override implicit val logTag: LogTag = LogTag[UserPreferences]

  override protected def getValue[A: PrefCodec](key: PrefKey[A]) = {
    get(key.str).map(_.map(_.value)).map(_.map(implicitly[PrefCodec[A]].decode).getOrElse(key.default))
  }

  override def setValue[A: PrefCodec](key: PrefKey[A], value: A) =
    put(key.str, KeyValueData(key.str, implicitly[PrefCodec[A]].encode(value))).map(_ => {})
}

object GlobalPreferences {

  val MigrationKey = "PreferenceMigration"
  val PreferencesName = "com.wire.preferences"

  def apply(context: Context): GlobalPreferences =
    new GlobalPreferences(context, context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE))

  lazy val LoggingInUser = PrefKey[Option[UserInfo]]("logging_in_user") //only to be used during DB import

  lazy val ActiveAccountPref = PrefKey[Option[UserId]]("active_account")
  lazy val CurrentAccountPrefOld = PrefKey[Option[AccountId]]("CurrentUserPref")

  lazy val BackendDrift = PrefKey[Duration]("backend_drift")

  //TODO think of a nicer way of ensuring that these key values are used in UI - right now, we need to manually check they're correct
  lazy val AutoAnswerCallPrefKey = PrefKey[Boolean]("PREF_KEY_AUTO_ANSWER_ENABLED")
  lazy val V31AssetsEnabledKey = PrefKey[Boolean]("PREF_V31_ASSETS_ENABLED")
  lazy val WsForegroundKey = PrefKey[Boolean]("websocket_foreground_service_enabled_1", customDefault = false)
  lazy val CheckedForPlayServices = PrefKey[Boolean]("checked_for_google_play_services", customDefault = false)
  lazy val CheckedWebSocketConfig = PrefKey[Boolean]("checked_web_socket_config", customDefault = false)
  lazy val SkipTerminatingState = PrefKey[Boolean]("skip_terminating_state") //for calling

  lazy val PushEnabledKey = PrefKey[Boolean]("PUSH_ENABLED", customDefault = true)
  lazy val PushToken = PrefKey[Option[PushToken]]("PUSH_TOKEN")

  lazy val LastUpToDateSyncTime = PrefKey[Long]("LastUpToDateSync")
  lazy val LastCheckedVersion = PrefKey[Int]("UpToDateVersion")
  lazy val VersionUpToDate = PrefKey[Boolean]("UpToDate", customDefault = true)

  lazy val LastCacheCleanup = PrefKey[Long]("LastCacheCleanup")

  lazy val GPSErrorDialogShowCount = PrefKey[Int]("PREF_PLAY_SERVICES_ERROR_SHOW_COUNT")

  lazy val SendAnonymousDataEnabled = PrefKey[Boolean]("PREF_KEY_PRIVACY_ANALYTICS_ENABLED", customDefault = false)
  lazy val ShowMarketingConsentDialog = PrefKey[Boolean]("show_marketing_consent_dialog", customDefault = true) //can be set to false by automation

  lazy val LastEphemeralValue = PrefKey[Option[FiniteDuration]]("last_ephemeral_value", customDefault = None)

  lazy val ShouldCreateFullConversation = PrefKey[Boolean]("should_create_full_conv", customDefault = false)

  lazy val LogsEnabled: PrefKey[Boolean] = PrefKey[Boolean]("save_local_logs", customDefault = true)

  lazy val RootDetected: PrefKey[Boolean] = PrefKey[Boolean]("root_detected", customDefault = false)

  lazy val IncognitoKeyboardEnabled: PrefKey[Boolean] = PrefKey[Boolean]("incognito_keyboard_enabled", customDefault = false)

  // TODO: Remove after everyone migrates to UserPreferences.AppLockEnabled
  lazy val AppLockEnabled: PrefKey[Boolean] = PrefKey[Boolean]("app_lock_enabled", customDefault = false)
  lazy val GlobalAppLockDeprecated: PrefKey[Boolean] = PrefKey[Boolean]("global_app_lock_deprecated", customDefault = false)

  lazy val VibrateEnabled = PrefKey[Boolean]("vibrate_enabled", customDefault = false)
  lazy val RecreateChannels = PrefKey[Boolean]("recreate_channels_3", customDefault = false)
  lazy val RingTone = PrefKey[String]("ringtone_key")
  lazy val TextTone = PrefKey[String]("text_key")
  lazy val PingTone = PrefKey[String]("ping_key")
}

object UserPreferences {

  def apply(context: Context, storage: ZmsDatabase, globalPreferences: GlobalPreferences): UserPreferences =
    new UserPreferences(context, storage)

  lazy val IsLogin = PrefKey[Boolean]("is_login")
  lazy val IsNewClient = PrefKey[Boolean]("is_new_client")

  lazy val CrashesAndAnalyticsRequestShown = PrefKey[Boolean]("usage_data_permissions_shown", customDefault = true) //true to avoid harassing existing users
  lazy val AskMarketingConsentAgain = PrefKey[Boolean]("ask_marketing_consent_again") //used if the user views privacy policy instead of giving consent
  lazy val CurrentTrackingId = PrefKey[Option[TrackingId]]("tracking_id", customDefault = None)
  lazy val TrackingEnabled = PrefKey[Boolean]("countly_analytics_enabled", customDefault = false)
  lazy val TrackingEnabledOneTimeCheckPerformed = PrefKey[Boolean]("analytics_enabled_one_time_check", customDefault = false)
  lazy val ShouldShareTrackingId = PrefKey[Boolean]("should_share_tracking_id_1", customDefault = true)

  lazy val SelfClient = PrefKey[ClientRegistrationState]("self_client")
  lazy val PrivateMode = PrefKey[Boolean]("private_mode")
  lazy val SelfPermissions = PrefKey[Long]("self_permissions")
  lazy val CopyPermissions = PrefKey[Long]("copy_permissions")

  lazy val PendingEmail = PrefKey[Option[EmailAddress]]("pending_email")
  lazy val PendingPassword = PrefKey[Boolean]("pending_password") //true if the user needs to set a password
  lazy val PendingPhone = PrefKey[Option[PhoneNumber]]("pending_phone")

  lazy val DarkTheme = PrefKey[Boolean]("dark_theme")
  lazy val Sounds = PrefKey[IntensityLevel]("sounds")
  lazy val DownloadImagesAlways = PrefKey[Boolean]("download_images_always", customDefault = true)

  lazy val SelectedConvId = PrefKey[Option[ConvId]]("selected_conv_id")

  lazy val OtrLastPrekey = PrefKey[Int]("otr_last_prekey_id")
  lazy val LastStableNotification = PrefKey[Option[Uid]]("last_notification_id")

  lazy val LastSelfClientsSyncRequestedTime = PrefKey[Long]("last_self_clients_sync_requested")

  lazy val LastReceivedConvEvent = PrefKey[Instant]("last_received_conv_event_time")
  lazy val LastFetchedConvEvent = PrefKey[Instant]("last_fetched_conv_event_time", customDefault = Instant.ofEpochMilli(1))
  lazy val LastFetchedConvEventLocal = PrefKey[Instant]("last_fetched_local_time")
  lazy val GcmRegistrationTime = PrefKey[Instant]("gcm_registration_time")
  lazy val GcmRegistrationRetry = PrefKey[Int]("gcm_registration_retry_count")

  lazy val VBREnabled = PrefKey[Boolean]("variable_bit_rate_enabled", customDefault = true)
  lazy val SendButtonEnabled = PrefKey[Boolean]("send_button_enabled", customDefault = true)
  lazy val MessagePreview = PrefKey[Boolean]("message_previews_enabled", customDefault = true)
  lazy val HideScreenContent = PrefKey[Boolean]("hide_screen_content", customDefault = false)

  //increment number to perform slow sync on particular type
  lazy val ShouldSyncConversations = PrefKey[Boolean]("should_sync_conversations_2", customDefault = true)
  lazy val shouldSyncAllOnUpdate = PrefKey[Boolean]("should_sync_all_on_update", customDefault = true)
  lazy val ShouldSyncUsers = PrefKey[Boolean]("should_sync_users_1", customDefault = true)
  lazy val ShouldSyncTeam = PrefKey[Boolean]("should_sync_team_1", customDefault = true)
  lazy val ShouldSyncFolders = PrefKey[Boolean]("should_sync_folders", customDefault = true)
  lazy val LastTeamUpdate = PrefKey[Instant]("last_team_update", customDefault = Instant.EPOCH)
  lazy val RemoveUncontactedTeamMembers = PrefKey[Boolean]("remove_uncontacted_team_members", customDefault = true)

  // fix for duplicated entries in the database, left there by a bug from an old version of the app
  lazy val FixDuplicatedConversations = PrefKey[Boolean]("fix_duplicated_conversations", customDefault = true)

  lazy val CheckMutedStatus = PrefKey[Boolean]("check_muted_status", customDefault = true)

  lazy val ReadReceiptsRemotelyChanged = PrefKey[Boolean]("read_receipts_remotely_changed", customDefault = false)

  lazy val StatusNotificationsBitmask = PrefKey[Int]("status_notifications_bitmask", customDefault = 0)
  lazy val ShouldWarnStatusNotifications = PrefKey[Boolean]("should_warn_status_notifications", customDefault = true)

  lazy val AskedForLocationPermission = PrefKey[Boolean]("asked_for_location_permission", customDefault = false)

  lazy val ConversationListType = PrefKey[Int]("conversation_list_type", customDefault = -1)
  lazy val ConversationFoldersUiState = PrefKey[Map[FolderId, Boolean]]("conversation_folders_ui_state", customDefault = Map.empty)

  lazy val FailedPasswordAttempts = PrefKey[Int]("failed_password_attempts", customDefault = 0)

  lazy val ShouldWarnAVSUpgrade = PrefKey[Boolean]("should_warn_avs_upgrade", customDefault = false)

  lazy val CustomPassword = PrefKey[Option[String]]("sso_password", customDefault = None)
  lazy val CustomPasswordIv = PrefKey[Option[String]]("sso_password_iv", customDefault = None)

  lazy val AppLockEnabled: PrefKey[Boolean]                = PrefKey[Boolean]("app_lock_enabled", customDefault = false)
  lazy val AppLockFeatureEnabled: PrefKey[Boolean]         = PrefKey[Boolean]("app_lock_feature_enabled", customDefault = true)
  lazy val AppLockForced:  PrefKey[Boolean]                = PrefKey[Boolean]("app_lock_forced", customDefault = false)
  lazy val AppLockTimeout: PrefKey[Option[FiniteDuration]] = PrefKey[Option[FiniteDuration]]("app_lock_timeout", customDefault = None)

  lazy val LegalHoldRequest: PrefKey[Option[LegalHoldRequest]] = PrefKey[Option[LegalHoldRequest]]("legal_hold_request", customDefault = None)
  lazy val LegalHoldDisclosureType: PrefKey[Option[LegalHoldStatus]] =
    PrefKey[Option[LegalHoldStatus]]("legal_hold_disclosure_type", customDefault = None)

  lazy val ShouldPostClientCapabilities: PrefKey[Boolean] = PrefKey[Boolean]("should_post_client_capabilities", customDefault = true)

  lazy val FileSharingFeatureEnabled: PrefKey[Boolean] = PrefKey[Boolean]("file_sharing_feature_enabled", customDefault = true)
  lazy val ShouldInformFileSharingRestriction: PrefKey[Boolean] = PrefKey[Boolean]("should_inform_file_sharing_restriction", customDefault = false)
  lazy val ClassifiedDomains: PrefKey[Option[String]] = PrefKey[Option[String]]("classified_domains", customDefault = None)
  lazy val GuestLinks: PrefKey[Boolean] = PrefKey[Boolean]("guest_links", customDefault = true)

  lazy val AreSelfDeletingMessagesEnabled: PrefKey[Boolean] = PrefKey[Boolean]("self_deleting_messages_enabled", customDefault = true)
  lazy val SelfDeletingMessagesEnforcedTimeout: PrefKey[Int] = PrefKey[Int]("self_deleting_messages_enforced_timeout", customDefault = 0)
  lazy val ShouldInformSelfDeletingMessagesChanged: PrefKey[Boolean] = PrefKey[Boolean]("self_deleting_messages_enforced_changed", customDefault = false)

  lazy val ConferenceCallingFeatureEnabled: PrefKey[Boolean] = PrefKey[Boolean]("conference_calling_feature_enabled_1", customDefault = true)
  lazy val ShouldInformPlanUpgradedToEnterprise: PrefKey[Boolean] = PrefKey[Boolean]("should_inform_plan_upgraded_to_enterprise", customDefault = false)

  lazy val ShouldMigrateToFederation: PrefKey[Boolean] = PrefKey[Boolean]("should_migrate_to_federation_2", customDefault = true)
}

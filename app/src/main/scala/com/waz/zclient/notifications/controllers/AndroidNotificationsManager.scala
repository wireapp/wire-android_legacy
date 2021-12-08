package com.waz.zclient.notifications.controllers

import android.app.{Notification, NotificationChannel, NotificationChannelGroup, NotificationManager}
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompatExtras
import com.waz.content.GlobalPreferences
import com.waz.content.Preferences.PrefKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.{Injectable, Injector}
import com.waz.zclient.log.LogUI.{error, verbose}
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.{IncomingCallNotificationsChannelId, MessageNotificationsChannelId, NotificationAccountId, NotificationConvId, OngoingNotificationsChannelId, PingNotificationsChannelId, WireNotificationsChannelGroupId, WireNotificationsChannelGroupName}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.RingtoneUtils
import com.waz.zclient.log.LogUI._
import com.waz.zclient.R

import scala.concurrent.Future
import scala.util.Try

final class AndroidNotificationsManager(notificationManager: NotificationManager)(implicit inj: Injector, cxt: Context)
  extends NotificationManagerWrapper with Injectable with DerivedLogTag {
  private lazy val prefs = inject[GlobalPreferences]

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) buildNotificationChannels()

  def showNotification(id: Int, notificationProps: NotificationProps): Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getNotificationChannel(notificationProps.channelId).isEmpty)
      buildNotificationChannels(enforce = true)
    notificationManager.notify(id, notificationProps.build())
  }

  def getNotificationChannel(channelId: String): Option[NotificationChannel] =
    Option(notificationManager.getNotificationChannel(channelId))

  override def cancelNotifications(accountId: UserId, convs: Set[ConvId]): Unit = {
    verbose(l"cancelNotifications($accountId, $convs)")
    def isSummaryNotification(n: StatusBarNotification): Boolean =
      n.getNotification.extras.getBoolean(NotificationCompatExtras.EXTRA_GROUP_SUMMARY)

    def isInAccount(accountId: UserId, n: StatusBarNotification): Boolean =
      n.getNotification.extras.getString(NotificationAccountId) == accountId.str

    def isInConv(convId: ConvId, n: StatusBarNotification): Boolean =
      n.getNotification.extras.getString(NotificationConvId) == convId.str

    val (summaryNots, convNots) =
      notificationManager.getActiveNotifications.toSeq.partition(isSummaryNotification)

    val (toCancel, others) =
      if (convs.isEmpty) convNots.partition(isInAccount(accountId, _))
      else convNots.partition(n => convs.exists(convId => isInConv(convId, n)))

    toCancel.foreach(n => notificationManager.cancel(n.getId))

    if (others.isEmpty)
      notificationManager.cancelAll()
    else
      summaryNots
        .filter(summary => others.forall(_.getNotification.getGroup != summary.getNotification.getGroup))
        .foreach(summary => notificationManager.cancel(summary.getId))
  }

  private def getSound(pref: PrefKey[String], default: Int): Future[Option[Uri]] =
    prefs(pref).apply().map {
      case ""  => Try(RingtoneUtils.getUriForRawId(cxt, default)).toOption
      case str => Try(Uri.parse(str)).toOption
    }(Threading.Ui)

  private def createNotificationChannel(id:            String,
                                        nameId:        Int,
                                        descriptionId: Int,
                                        importance:    Int = NotificationManager.IMPORTANCE_DEFAULT,
                                        vibration:     Boolean = false,
                                        showBadge:     Boolean = false,
                                        sound:         Option[Uri] = None,
                                        visibility:    Int = Notification.VISIBILITY_PUBLIC): NotificationChannel =
    returning(new NotificationChannel(id, getString(nameId), importance)) { ch =>
      ch.setDescription(getString(descriptionId))
      ch.enableVibration(vibration)
      ch.setShowBadge(showBadge)
      ch.setLockscreenVisibility(visibility)
      ch.enableLights(true)
      ch.setGroup(WireNotificationsChannelGroupId)
      ch.setBypassDnd(true)
      ch.setAllowBubbles(true)
      sound.foreach(uri => ch.setSound(uri, Notification.AUDIO_ATTRIBUTES_DEFAULT))
    }

  private def buildNotificationChannels(enforce: Boolean = false): Unit = {
    import Threading.Implicits.Background

    verbose(l"buildNotificationChannels")

    val recreateChannelsPref = prefs.preference(GlobalPreferences.RecreateChannels)
    val channels: Future[Seq[NotificationChannel]] =
      for {
        true      <- if (enforce) Future.successful(true) else recreateChannelsPref()
        _         <- recreateChannelsPref := false
        vibration <- prefs.preference(GlobalPreferences.VibrateEnabled).apply()
        _ = verbose(l"trying to get sounds for messages and pings...")
        msgSound  <- getSound(GlobalPreferences.TextTone, R.raw.new_message_gcm)
        _ = verbose(l"msgSound $msgSound")
        pingSound <- getSound(GlobalPreferences.PingTone, R.raw.ping_from_them)
        _ = verbose(l"pingSound $pingSound")
      } yield
        Seq(
          createNotificationChannel(
            id            = OngoingNotificationsChannelId,
            nameId        = R.string.ongoing_channel_name,
            descriptionId = R.string.ongoing_channel_description,
            importance    = NotificationManager.IMPORTANCE_LOW,
            vibration     = vibration
          ),
          createNotificationChannel(
            id            = IncomingCallNotificationsChannelId,
            nameId        = R.string.incoming_call_notifications_channel_name,
            descriptionId = R.string.incoming_call_notifications_channel_name,
            importance    = NotificationManager.IMPORTANCE_MAX,
            vibration     = vibration
          ),
          createNotificationChannel(
            id            = MessageNotificationsChannelId,
            nameId        = R.string.message_notifications_channel_name,
            descriptionId = R.string.message_notifications_channel_description,
            importance    = NotificationManager.IMPORTANCE_MAX,
            vibration     = vibration,
            sound         = msgSound,
            visibility    = Notification.VISIBILITY_PUBLIC,
            showBadge     = true
          ),
          createNotificationChannel(
            id            = PingNotificationsChannelId,
            nameId        = R.string.ping_notifications_channel_name,
            descriptionId = R.string.ping_notifications_channel_description,
            importance    = NotificationManager.IMPORTANCE_MAX,
            vibration     = vibration,
            sound         = pingSound,
            visibility    = Notification.VISIBILITY_PUBLIC,
            showBadge     = true
          )
        )

    channels.foreach { chs =>
      verbose(l"recreating channels")
      try {
        notificationManager.deleteNotificationChannel(OngoingNotificationsChannelId)
        notificationManager.deleteNotificationChannel(IncomingCallNotificationsChannelId)
        notificationManager.deleteNotificationChannel(MessageNotificationsChannelId)
        notificationManager.deleteNotificationChannel(PingNotificationsChannelId)
        notificationManager.deleteNotificationChannelGroup(WireNotificationsChannelGroupId)
        import scala.collection.JavaConverters._
        notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(WireNotificationsChannelGroupId, WireNotificationsChannelGroupName))
        notificationManager.createNotificationChannels(chs.asJava)
        verbose(l"channels recreated")
      } catch {
        case t: Throwable => error(l"can't recreate channels: ${t.getMessage}", t)
      }
    }(Threading.Ui)
  }
}

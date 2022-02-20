package com.waz.zclient.notifications.controllers

import android.app.{Notification, NotificationChannel, NotificationChannelGroup, NotificationManager}
import android.content.Context
import android.net.Uri
import android.os.Build
import com.waz.content.GlobalPreferences
import com.waz.content.Preferences.PrefKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.{Injectable, Injector}
import com.waz.zclient.log.LogUI.{error, verbose}
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper._
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.RingtoneUtils
import com.waz.zclient.log.LogUI._
import com.waz.zclient.R

import scala.concurrent.Future
import scala.util.Try

object AndroidNotificationsManager {
  private var uniqueIds = Set.empty[Int]

  def filterNewNotifications(props: Iterable[NotificationProps]): Seq[NotificationProps] = synchronized {
    val newProps =
      props
        .map(p => toUniqueId(p) -> p)
        .filterNot { case (id, _) => uniqueIds.contains(id) }
    uniqueIds ++= newProps.map(_._1)
    newProps.map(_._2).toVector.sortBy(_.when)
  }

  @inline
  private def toUniqueId(p: NotificationProps): Int =
    (p.accountId.str + p.convId.getOrElse("") + p.when.toString).hashCode

  private var notsIds = Map[UserId, Set[Int]]()

  def toNotificationGroupId(accountId: UserId): Int = accountId.str.hashCode
  def toNotificationConvId(accountId: UserId, convId: ConvId): Int = (accountId.str + convId.str).hashCode
}

final class AndroidNotificationsManager(notificationManager: NotificationManager)(implicit inj: Injector, cxt: Context)
  extends NotificationManagerWrapper with Injectable with DerivedLogTag {
  import AndroidNotificationsManager._

  private lazy val prefs = inject[GlobalPreferences]

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) buildNotificationChannels()

  override def showNotification(props: Iterable[NotificationProps]): Unit =
    filterNewNotifications(props).foreach { p =>
      val id = p.convId.fold(toNotificationGroupId(p.accountId))(toNotificationConvId(p.accountId, _))
      if (p.convId.isDefined)
        notsIds += p.accountId -> (notsIds.getOrElse(p.accountId, Set.empty) + id)

      val notification = p.build()

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getNotificationChannel(p.channelId).isEmpty)
        buildNotificationChannels(enforce = true)
          .foreach(_ => notificationManager.notify(id, notification))(Threading.Ui)
      else
        notificationManager.notify(id, notification)
    }

  override def getNotificationChannel(channelId: String): Option[NotificationChannel] =
    Option(notificationManager.getNotificationChannel(channelId))

  override def cancelNotifications(accountId: UserId, convId: ConvId): Unit = {
    val id = toNotificationConvId(accountId, convId)
    notificationManager.cancel(id)
    notsIds.get(accountId) match {
      case Some(ids) if ids.nonEmpty =>
        val newIds = ids - id
        if (newIds.isEmpty) {
          cancelNotifications(accountId)
        } else {
          notsIds += accountId -> newIds
        }
      case None =>
    }

    if (notsIds.isEmpty) notificationManager.cancelAll()
  }

  override def cancelNotifications(accountId: UserId): Unit = {
    val id = toNotificationGroupId(accountId)
    notificationManager.cancel(id)
    notsIds.get(accountId) match {
      case Some(nIds) =>
        nIds.foreach(notificationManager.cancel)
        notsIds -= accountId
      case None =>
    }

    if (notsIds.isEmpty) notificationManager.cancelAll()
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
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ch.setAllowBubbles(true)
      sound.foreach(uri => ch.setSound(uri, Notification.AUDIO_ATTRIBUTES_DEFAULT))
    }

  private def buildNotificationChannels(enforce: Boolean = false): Future[Unit] = {
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

    channels.map { chs =>
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

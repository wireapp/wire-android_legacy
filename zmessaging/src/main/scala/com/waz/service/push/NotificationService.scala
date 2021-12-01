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
package com.waz.service.push

import com.waz.api.Message
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.content._
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.{MsgDeleted, MsgEdit, MsgRecall, Reaction, Text}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.ZMessaging.accountTag
import com.waz.service._
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals.{EventContext, Signal}
import org.threeten.bp.Clock

import scala.concurrent.Future

/**
  * A trait representing some controller responsible for displaying notifications in the UI. It is expected that this
  * controller is a global singleton
  */
trait NotificationUiController {
  /**
    * A call to the UI telling it that it has notifications to display. This needs to be a future so that we can wait
    * for the displaying of notifications before finishing the event processing pipeline. Upon completion of the future,
    * we can also mark these notifications as displayed.
    * @return a Future that should enclose the display of notifications to the UI
    */
  def onNotificationsChanged(accountId: UserId, ns: Set[NotificationData]): Future[Unit]

  /**
    * To be called by the UI when any conversations in a given account are visible to the user. When visible, the user
    * should see in some way that they have notifications for that conversation, so we can automatically dismiss the
    * notifications for them.
    */
  def notificationsSourceVisible: Signal[Map[UserId, Set[ConvId]]]
}

trait NotificationService {
  def dismissNotifications(forConvs: Option[Set[ConvId]] = None): Future[Unit]
  def displayNotificationForDeletingConversation(from: UserId, time: RemoteInstant, convData: ConversationData): Future[Unit]
  val messageNotificationEventsStage : EventScheduler.Stage
  val connectionNotificationEventStage : EventScheduler.Stage
}

class NotificationServiceImpl(selfUserId:      UserId,
                              messages:        MessagesStorage,
                              storage:         NotificationStorage,
                              convs:           ConversationStorage,
                              pushService:     PushService,
                              uiController:    NotificationUiController,
                              userService:     UserService,
                              clock:           Clock) extends NotificationService {
  import Threading.Implicits.Background
  implicit lazy val logTag: LogTag = accountTag[NotificationService](selfUserId)

  private val schedulePushNotificationsToUi = Signal(false)

  Signal.zip(schedulePushNotificationsToUi, pushService.processing).onChanged.foreach {
    case (true, false) =>
      pushNotificationsToUi().map { _ => schedulePushNotificationsToUi ! false }
    case _ =>
  }

  uiController.notificationsSourceVisible.foreach { sources =>
    sources.get(selfUserId).map(Some(_)).foreach(dismissNotifications)
  }

  /**
    * Removes from storage all notifications that are being displayed for the given set of input conversations.
    * The notifications in UI are cleared separately, in MessageNotificationsController.
    *
    * @param forConvs the conversations for which to remove notifications, or None if all notifications should be cleared.
    */
  def dismissNotifications(forConvs: Option[Set[ConvId]] = None): Future[Unit] = {
    verbose(l"dismissNotifications: $forConvs")
    for {
      nots <- storage.values.map(_.toSet)
      toRemove = forConvs match {
        case None        => nots
        case Some(convs) => nots.filter(n => convs.contains(n.conv))
      }
      _ <- storage.removeAll(toRemove.map(_.id))
    } yield {}
  }

  override val messageNotificationEventsStage = EventScheduler.Stage[ConversationEvent]({ (c, events) =>
    object Deleted {
      def unapply(event: GenericMessageEvent): Option[NotId] = event.content.unpackContent match {
        case d: MsgDeleted => Some(d.unpack._2.toNotificationId)
        case r: MsgRecall  => Some(r.unpack.toNotificationId)
        case _             => None
      }
    }

    if (events.nonEmpty) {
      for {
        (undoneLikes, likes) <- getReactionChanges(events)
        currentNotifications <- storage.values.map(_.toSet)
        msgNotifications     <- getMessageNotifications(c, events)

        (afterEditsApplied, beforeEditsApplied) = applyEdits(currentNotifications ++ msgNotifications, events)

        deleted = events.collect { case Deleted(notId) => notId }.toSet

        toShow = (afterEditsApplied ++ likes).filterNot(n => (undoneLikes ++ deleted).contains(n.id))
        toRemove = undoneLikes ++ beforeEditsApplied ++ deleted
        _ <- storage.removeAll(toRemove)
        _ <- storage.insertAll(toShow)
        _ =  if (toShow.nonEmpty || toRemove.nonEmpty) schedulePushNotificationsToUi ! true
      } yield {}
    } else Future.successful({})
  })

  override val connectionNotificationEventStage = EventScheduler.Stage[Event]({ (_, events) =>
    val toShow = events.collect {
      case UserConnectionEvent(_, _, _, userId, _, msg, ConnectionStatus.PendingFromOther, time, _) =>
        NotificationData(NotId(CONNECT_REQUEST, userId), msg.getOrElse(""), ConvId(userId.str), userId, CONNECT_REQUEST, time)
      case UserConnectionEvent(_, _, _, userId, _, _, ConnectionStatus.Accepted, time, _) =>
        NotificationData(NotId(CONNECT_ACCEPTED, userId), "", ConvId(userId.str), userId, CONNECT_ACCEPTED, time)
    }

    if (toShow.nonEmpty)
      storage.insertAll(toShow).map { _ => schedulePushNotificationsToUi ! true }
    else
      Future.successful(())
  })

  override def displayNotificationForDeletingConversation(from: UserId, remoteTime: RemoteInstant, convData: ConversationData): Future[Unit] = {
    val notData = NotificationData(
      conv    = convData.id,
      user    = from,
      msgType = NotificationType.CONVERSATION_DELETED,
      time    = remoteTime
    )
    (for {
      Some(self)                <- userService.getSelfUser
      notificationSourceVisible <- uiController.notificationsSourceVisible.head
      true                      <- Future.successful(shouldShowNotification(self, notData, convData, notificationSourceVisible))
      _                         <- uiController.onNotificationsChanged(self.id, Set(notData))
      _                         <- storage.insert(notData.copy(hasBeenDisplayed = true))
    } yield {}).recoverWith {
      case ex: Exception =>
        error(l"error @ displayNotificationForDeletingConversation $ex")
        Future.successful(())
    }
  }

  private def allowWhileSourceIsDisplayed(notificationData: NotificationData): Boolean =
    notificationData.isConvDeleted

  private def shouldShowNotification(self: UserData,
                                     n: NotificationData,
                                     conv: ConversationData,
                                     notificationSourceVisible: Map[UserId, Set[ConvId]]): Boolean = {

    val fromSelf = n.user == self.id
    val notReadYet = conv.lastRead.isBefore(n.time)
    val notCleared = conv.cleared.forall(_.isBefore(n.time))
    val isConvOnDisplay = !notificationSourceVisible.get(self.id).exists(_.contains(n.conv))

    val allowedForDisplay = !fromSelf && notReadYet && notCleared &&
      (allowWhileSourceIsDisplayed(n) || isConvOnDisplay)

    if (!allowedForDisplay) {
      false
    } else {
      if (n.msgType == NotificationType.COMPOSITE) {
        return true
      }

      val isReplyOrMention = n.isSelfMentioned || n.isReply
      if (self.availability == Availability.Away) {
        false
      } else if (self.availability == Availability.Busy) {
        if (!isReplyOrMention) {
          false
        } else {
          conv.muted.isAllAllowed || conv.muted.onlyMentionsAllowed
        }
      } else {
        conv.muted.isAllAllowed || (conv.muted.onlyMentionsAllowed && isReplyOrMention)
      }
    }
  }

  private def pushNotificationsToUi(): Future[Unit] = storage.values.map {
    case v if v.isEmpty => Future.successful(())
    case toShow =>
      verbose(l"pushNotificationsToUi, toShow: ${toShow.size}")
      (for {
        notificationSourceVisible <- uiController.notificationsSourceVisible.head
        Some(self)                <- userService.getSelfUser
        convsWithNots             <- Future.sequence(toShow.map(n => convs.get(n.conv).map(c => (n, c))))
        (show, ignore)            =  convsWithNots.partition {
                                       case (n, Some(c))                 => shouldShowNotification(self, n, c, notificationSourceVisible)
                                       case (n, None) if n.isConvDeleted => true
                                       case (_, None)                    => false
                                      }
        showNotifications         =  show.map(_._1).toSet
        ignoreNotifications       =  ignore.map(_._1.id).toSet
        _                         <- uiController.onNotificationsChanged(self.id, showNotifications)
        _                         <- storage.insertAll(showNotifications.map(_.copy(hasBeenDisplayed = true)))
        _                         <- storage.removeAll(ignoreNotifications)
      } yield {}).recoverWith {
        case exception: Exception =>
          error(l"error while displaying notifications to ui $exception")
          Future.successful(())
      }
  }

  private def getMessageNotifications(rConvId: RConvId, events: Vector[Event]) = {
    val eventTimes = events.collect { case e: ConversationEvent => e.time }
    if (eventTimes.nonEmpty) {
      for {
        Some(conv) <- convs.getByRemoteId(rConvId)
        drift      <- pushService.beDrift.head
        msgs       <- messages.findMessagesFrom(conv.id, eventTimes.min).map(_.filterNot(_.userId == selfUserId))
        quoteIds   <- messages
          .getAll(msgs.filter(!_.hasMentionOf(selfUserId)).flatMap(_.quote.map(_.message)).toSet)
          .map(_.flatten.filter(_.userId == selfUserId).map(_.id).toSet)
      } yield {
        msgs.flatMap { msg =>

          import Message.Type._

          val tpe = msg.msgType match {
            case TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA => Some(NotificationType.TEXT)
            case KNOCK        => Some(NotificationType.KNOCK)
            case IMAGE_ASSET  => Some(NotificationType.IMAGE_ASSET)
            case LOCATION     => Some(NotificationType.LOCATION)
            case RENAME       => Some(NotificationType.RENAME)
            case MISSED_CALL  => Some(NotificationType.MISSED_CALL)
            case ANY_ASSET    => Some(NotificationType.ANY_ASSET)
            case AUDIO_ASSET  => Some(NotificationType.AUDIO_ASSET)
            case VIDEO_ASSET  => Some(NotificationType.VIDEO_ASSET)
            case COMPOSITE    => Some(NotificationType.COMPOSITE)
            case _ => None
          }

          tpe.map { tp =>
            verbose(l"quoteIds: $quoteIds, message: $msg")
            NotificationData(
              id              = msg.id.toNotificationId,
              msg             = if (msg.isEphemeral) "" else msg.contentString, msg.convId,
              user            = msg.userId,
              msgType         = tp,
              //TODO do we ever get RemoteInstant.Epoch?
              time            = if (msg.time == RemoteInstant.Epoch) msg.localTime.toRemote(drift) else msg.time,
              ephemeral       = msg.isEphemeral,
              isSelfMentioned = msg.mentions.flatMap(_.userId).contains(selfUserId),
              isReply         = msg.quote.map(_.message).exists(quoteIds(_))
            )
          }
        }.toSet
      }
    } else Future.successful(Set.empty[NotificationData])
  }

  private def applyEdits(currentNotifications: Set[NotificationData], events: Vector[Event]) = {
    object Edited {
      def unapply(event: GenericMessageEvent): Option[(MessageId, MessageId, String)] = event.content.unpack match {
        case (newId, edit: MsgEdit) =>
          edit.unpack.map { case (id, text) => (id, MessageId(newId.str), text.unpack._1) }
        case _ => None
      }
    }

    val edits = events.collect { case Edited(id, newId, msg) => (id, (newId, msg)) }.toMap

    val (afterEditsApplied, beforeEditsApplied) = edits.foldLeft((currentNotifications.map(n => (n.id, n)).toMap, Set.empty[NotId])) {
      case ((edited, toRemove), (oldId, (newId, newContent))) =>
        edited.get(NotId(oldId.str)) match {
          case Some(toBeEdited) =>
            val newNotId = NotId(newId.str)
            val updated = toBeEdited.copy(id = newNotId, msg = newContent)

            ((edited - toBeEdited.id) + (newNotId -> updated), toRemove + toBeEdited.id)

          case _ => (edited, toRemove)
        }
    }

    (afterEditsApplied.values.toSet, beforeEditsApplied)
  }

  private def getReactionChanges(events: Vector[Event]) = {
    object Reacted {
      def unapply(event: GenericMessageEvent): Option[Liking] = event match {
        case GenericMessageEvent(_, _, time, from, _, gm: GenericMessage) if from != selfUserId =>
          gm.unpackContent match {
            case r: Reaction =>
              val (msg, action) = r.unpack
              Some(Liking(msg, from, time, action))
            case _ => None
          }
        case _ => None
      }
    }

    val reactions = events.collect { case Reacted(liking) => liking }

    messages.getAll(reactions.map(_.message).toSet).map(_.flatten).map { msgs =>
      val msgsById = msgs.toIdMap
      val convsByMsg = msgs.iterator.by[MessageId, Map](_.id).mapValues(_.convId)
      val myMsgs = msgs.collect { case m if m.userId == selfUserId => m.id }.toSet

      val (toRemove, toAdd) =
        reactions
          .groupBy(r => (r.message, r.user))
          .collect { case ((messageId, _), likeUnlikeEvents) if myMsgs.contains(messageId) && likeUnlikeEvents.nonEmpty => likeUnlikeEvents.maxBy(_.timestamp) }
          .toSet
          .partition(_.action == Liking.Action.Unlike)

      (toRemove.map(r => NotId(r.id)), toAdd.map { r =>
        val msg = msgsById(r.message)
        NotificationData(
          id        = NotId(r.id),
          msg       = msg.contentString,
          conv      = convsByMsg(r.message),
          user      = r.user,
          msgType   = LIKE,
          time      = r.timestamp,
          likedContent = Some(msg.msgType match {
            case Message.Type.IMAGE_ASSET     => LikedContent.PICTURE
            case Message.Type.TEXT |
                 Message.Type.TEXT_EMOJI_ONLY => LikedContent.TEXT_OR_URL
            case _                            => LikedContent.OTHER
          })
        )
      })
    }
  }
}

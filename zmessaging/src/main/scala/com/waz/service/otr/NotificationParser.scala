package com.waz.service.otr

import com.waz.api.Message
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType.LikedContent
import com.waz.content.{ConversationStorage, MessageAndLikesStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.GenericContent.{Asset, Composite, Ephemeral, Knock, Location, Reaction, Text}
import com.waz.model._
import com.waz.utils._
import com.waz.threading.Threading

import scala.concurrent.Future
import com.waz.model.UserData.ConnectionStatus
import com.waz.service.call.CallingService

import com.waz.log.LogSE._

trait NotificationParser {
  def parse(events: Iterable[Event]): Future[Set[NotificationData]]
  def createMissedCallNotification(msgId: MessageId,
                                   conv:  ConversationData,
                                   from:  UserId,
                                   time:  RemoteInstant): Future[Option[NotificationData]]
}

final class NotificationParserImpl(selfId:       UserId,
                                   convStorage:  ConversationStorage,
                                   usersStorage: UsersStorage,
                                   mlStorage:    => MessageAndLikesStorage,
                                   calling:      => CallingService)
  extends NotificationParser with DerivedLogTag {
  import scala.language.existentials
  import Threading.Implicits.Background

  private lazy val selfUser = usersStorage.get(selfId)

  override def parse(events: Iterable[Event]): Future[Set[NotificationData]] =
    Future.traverse(events){
      case ev: GenericMessageEvent     => parse(ev)
      case ev: CallMessageEvent        => parse(ev)
      case ev: UserConnectionEvent     => parse(ev)
      case ev: RenameConversationEvent => parse(ev)
      case _                           => Future.successful(None)
    }.map(_.flatten.toSet)

  override def createMissedCallNotification(msgId: MessageId,
                                            conv:  ConversationData,
                                            from:  UserId,
                                            time:  RemoteInstant): Future[Option[NotificationData]] =
    selfUser.map {
      case Some(self) if shouldShowNotification(self, conv, from, time) =>
        Some(NotificationData(
          id      = NotId(msgId.str),
          conv    = conv.id,
          user    = from,
          msgType = NotificationType.MISSED_CALL,
          time    = time
        ))
      case _ =>
        None
    }

  private def parse(event: GenericMessageEvent): Future[Option[NotificationData]] =
    (for {
      Some(self)        <- selfUser
      Some(conv)        <- convStorage.getByRemoteId(event.convId)
      (uid, msgContent) =  event.content.unpack
      notification      <- createNotification(uid, self, conv, event, msgContent)
    } yield notification
  ).recover { case _ => None }

  private def parse(event: CallMessageEvent) = Future.successful {
    calling.receiveCallEvent(event.content, event.time, event.convId, event.from, event.sender)
    None
  }

  private def parse(event: UserConnectionEvent): Future[Option[NotificationData]] =
    selfUser.map(_.flatMap { self =>
      event match {
        case ev@UserConnectionEvent(_, _, _, from, _, msg, ConnectionStatus.PendingFromOther, time, _)
          if shouldShowNotification(self, from) =>
            verbose(l"FCM UserConnectionEvent pending from other: $ev")
            Some(NotificationData(
              NotId(NotificationType.CONNECT_REQUEST, from),
              msg.getOrElse(""),
              ConvId(from.str),
              from,
              NotificationType.CONNECT_REQUEST,
              time))
        case ev@UserConnectionEvent(_, _, _, from, _, _, ConnectionStatus.Accepted, time, _)
          if shouldShowNotification(self, from) =>
          verbose(l"FCM UserConnectionEvent accepted: $ev")
            Some(NotificationData(
              NotId(NotificationType.CONNECT_ACCEPTED, from),
              "",
              ConvId(from.str),
              from,
              NotificationType.CONNECT_ACCEPTED,
              time))
        case other =>
          verbose(l"FCM UserConnectionEvent other: $other")
          None
      }
    })

  private def parse(event: RenameConversationEvent): Future[Option[NotificationData]] = {
    for {
      Some(self) <- selfUser
      Some(conv) <- convStorage.getByRemoteId(event.convId)
    } yield
      if (shouldShowNotification(self, conv, event.from, event.time))
        Some(NotificationData(
          id      = NotId(),
          msg     = event.name.str,
          conv    = conv.id,
          user    = event.from,
          msgType = NotificationType.RENAME,
          time    = event.time
        ))
      else None
  }.recover { case _ => None }

  @scala.annotation.tailrec
  private def createNotification(uid:         Uid,
                                 self:        UserData,
                                 conv:        ConversationData,
                                 event:       GenericMessageEvent,
                                 msgContent:  GenericContent[_],
                                 isEphemeral: Boolean = false): Future[Option[NotificationData]] =
    msgContent match {
      case t: Text      => createTextNotification(uid, self, conv, event, t, isEphemeral)
      case a: Asset     => createAssetNotification(uid, self, conv, event, a, isEphemeral)
      case k: Knock     => createPingNotification(uid, self, conv, event, k, isEphemeral)
      case r: Reaction  => createLikeNotification(uid, self, conv, event, r, isEphemeral)
      case l: Location  => createLocationNotification(uid, self, conv, event, l, isEphemeral)
      case e: Ephemeral => createNotification(uid, self, conv, event, e.unpackContent, isEphemeral = true)
      case c: Composite => createCompositeNotification(uid, self, conv, event, c, isEphemeral)
      case _            => Future.successful(None)
    }

  private def createTextNotification(uid:         Uid,
                                     self:        UserData,
                                     conv:        ConversationData,
                                     event:       GenericMessageEvent,
                                     t:           Text,
                                     isEphemeral: Boolean) = {
    val (text, mentions, _, quote, _) = t.unpack
    val isSelfMentioned = mentions.flatMap(_.userId).contains(selfId)
    val isReply = quote.isDefined
    if (!shouldShowNotification(self, conv, event.from, event.time, isReplyOrMention = isSelfMentioned || isReply))
      Future.successful(None)
    else
      Future.successful {
        Some(NotificationData(
          id              = NotId(uid.str),
          msg             = if (isEphemeral) "" else text,
          conv            = conv.id,
          user            = event.from,
          msgType         = NotificationType.TEXT,
          time            = event.time,
          ephemeral       = isEphemeral,
          isSelfMentioned = isSelfMentioned,
          isReply         = isReply
        ))
      }
  }

  private def createAssetNotification(uid:         Uid,
                                      self:        UserData,
                                      conv:        ConversationData,
                                      event:       GenericMessageEvent,
                                      a:           Asset,
                                      isEphemeral: Boolean) =
    if (!shouldShowNotification(self, conv, event.from, event.time))
      Future.successful(None)
    else {
      val (asset, _) = a.unpack
      val msgType =
        if (Mime.Video.supported.contains(asset.mime)) NotificationType.VIDEO_ASSET
        else if (Mime.Audio.supported.contains(asset.mime)) NotificationType.AUDIO_ASSET
        else if (Mime.Image.supported.contains(asset.mime)) NotificationType.IMAGE_ASSET
        else NotificationType.ANY_ASSET
      Future.successful {
        Some(NotificationData(
          id        = NotId(uid.str),
          conv      = conv.id,
          user      = event.from,
          msgType   = msgType,
          time      = event.time,
          ephemeral = isEphemeral
        ))
      }
    }

  private def createPingNotification(uid:         Uid,
                                     self:        UserData,
                                     conv:        ConversationData,
                                     event:       GenericMessageEvent,
                                     k:           Knock,
                                     isEphemeral: Boolean) =
    if (!shouldShowNotification(self, conv, event.from, event.time))
      Future.successful(None)
    else
      Future.successful {
          Some(NotificationData(
            id        = NotId(uid.str),
            conv      = conv.id,
            user      = event.from,
            msgType   = NotificationType.KNOCK,
            time      = event.time,
            ephemeral = isEphemeral
          ))
      }

  private def createLikeNotification(uid:         Uid,
                                     self:        UserData,
                                     conv:        ConversationData,
                                     event:       GenericMessageEvent,
                                     r:           Reaction,
                                     isEphemeral: Boolean) =
    if (!shouldShowNotification(self, conv, event.from, event.time))
      Future.successful(None)
    else {
      val (mId, action) = r.unpack
      if (action != Liking.Action.Like)
        Future.successful(None)
      else
        mlStorage.getMessageAndLikes(mId).map {
          case Some(ml) if ml.message.userId == selfId =>
            val likedContent = ml.message.msgType match {
              case Message.Type.IMAGE_ASSET => LikedContent.PICTURE
              case Message.Type.TEXT |
                   Message.Type.TEXT_EMOJI_ONLY => LikedContent.TEXT_OR_URL
              case _ => LikedContent.OTHER
            }
            Some(NotificationData(
              id           = NotId(uid.str),
              msg          = if (ml.message.isEphemeral) "" else ml.message.contentString,
              conv         = conv.id,
              user         = event.from,
              msgType      = NotificationType.LIKE,
              time         = event.time,
              ephemeral    = isEphemeral,
              likedContent = Some(likedContent)
            ))
          case _ => None
        }
    }

  private def createLocationNotification(uid:         Uid,
                                         self:        UserData,
                                         conv:        ConversationData,
                                         event:       GenericMessageEvent,
                                         l:           Location,
                                         isEphemeral: Boolean) =
    if (!shouldShowNotification(self, conv, event.from, event.time))
      Future.successful(None)
    else
      Future.successful {
        Some(NotificationData(
          id        = NotId(uid.str),
          conv      = conv.id,
          user      = event.from,
          msgType   = NotificationType.LOCATION,
          time      = event.time,
          ephemeral = isEphemeral
        ))
      }

  private def createCompositeNotification(uid:         Uid,
                                          self:        UserData,
                                          conv:        ConversationData,
                                          event:       GenericMessageEvent,
                                          c:           Composite,
                                          isEphemeral: Boolean) = {
    if (!shouldShowNotification(self, conv, event.from, event.time, isComposite = true))
      Future.successful(None)
    else {
      lazy val text = c.unpack.items.collectFirst {
        case TextItem(text) => text.unpack._1
      }
      Future.successful {
        Some(NotificationData(
          id        = NotId(uid.str),
          msg       = if (isEphemeral) "" else text.getOrElse(""),
          conv      = conv.id,
          user      = event.from,
          msgType   = NotificationType.COMPOSITE,
          time      = event.time,
          ephemeral = isEphemeral
        ))
      }
    }
  }

  private def shouldShowNotification(self:             UserData,
                                     conv:             ConversationData,
                                     from:             UserId,
                                     time:             RemoteInstant,
                                     isReplyOrMention: Boolean = false,
                                     isComposite:      Boolean = false): Boolean = {
    val fromSelf          = from == self.id
    val notReadYet        = conv.lastRead.isBefore(time)
    val notCleared        = conv.cleared.forall(_.isBefore(time))
    val allowedForDisplay = !fromSelf && notReadYet && notCleared

    if (!allowedForDisplay) {
      false
    } else if (isComposite) {
        true
    } else if (self.availability == Availability.Away) {
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

  private def shouldShowNotification(self: UserData, from: UserId): Boolean =
    from != self.id &&
      self.availability != Availability.Away &&
      self.availability != Availability.Busy
}

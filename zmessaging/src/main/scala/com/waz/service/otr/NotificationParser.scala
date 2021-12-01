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

import com.waz.log.LogSE._

trait NotificationParser {
  def parse(events: Iterable[Event]): Future[Set[NotificationData]]
}

final class NotificationParserImpl(selfId:       UserId,
                                   convStorage:  ConversationStorage,
                                   usersStorage: UsersStorage,
                                   mlStorage:    () => MessageAndLikesStorage)
  extends NotificationParser with DerivedLogTag {
  import scala.language.existentials
  import Threading.Implicits.Background

  private val selfUser = usersStorage.signal(selfId)

  override def parse(events: Iterable[Event]): Future[Set[NotificationData]] =
    Future.traverse(events){
      case ev: GenericMessageEvent     => parse(ev)
      case ev: UserConnectionEvent     => parse(ev)
      case ev: RenameConversationEvent => parse(ev)
      case ev: DeleteConversationEvent => parse(ev)
      case _                           => Future.successful(None)
    }.map(_.flatten.toSet)

  private def parse(event: GenericMessageEvent) =
    (for {
      self              <- selfUser.head
      Some(conv)        <- convStorage.getByRemoteId(event.convId)
      (uid, msgContent) =  event.content.unpack
      notification      <- createNotification(uid, self, conv, event, msgContent)
    } yield notification)
      .recover { case ex: Throwable =>
        error(l"error while parsing $event, ${ex.getMessage}", ex)
        None
      }

  private def parse(event: UserConnectionEvent) =
    selfUser.head.map { self =>
      event match {
        case UserConnectionEvent(_, _, _, from, fromDomain, msg, ConnectionStatus.PendingFromOther, time, _)
          if shouldShowNotification(self, from) =>
            Some(NotificationData(
              conv       = ConvId(from.str),
              convDomain = fromDomain,
              user       = from,
              userDomain = fromDomain,
              msg        = msg.getOrElse(""),
              msgType    = NotificationType.CONNECT_REQUEST,
              time       = time
            ))
        case UserConnectionEvent(_, _, _, from, fromDomain, _, ConnectionStatus.Accepted, time, _)
          if shouldShowNotification(self, from) =>
            Some(NotificationData(
              conv       = ConvId(from.str),
              convDomain = fromDomain,
              user       = from,
              userDomain = fromDomain,
              msgType    = NotificationType.CONNECT_ACCEPTED,
              time       = time
            ))
        case _ => None
      }
    }

  private def parse(event: RenameConversationEvent): Future[Option[NotificationData]] =
    (for {
      self       <- selfUser.head
      Some(conv) <- convStorage.getByRemoteId(event.convId)
    } yield
      if (shouldShowNotification(self, conv, event.from, event.time))
        Some(NotificationData(
          msg     = event.name.str,
          conv    = conv.id,
          user    = event.from,
          msgType = NotificationType.RENAME,
          time    = event.time
        ))
      else None
    ).recover { case ex: Throwable =>
      error(l"error while parsing $event, ${ex.getMessage}", ex)
      None
    }

  private def parse(event: DeleteConversationEvent): Future[Option[NotificationData]] =
    (for {
      self       <- selfUser.head
      Some(conv) <- convStorage.getByRemoteId(event.convId)
    } yield
      if (shouldShowNotification(self, conv, event.from, event.time))
        Some(NotificationData(
          conv    = conv.id,
          user    = event.from,
          msgType = NotificationType.CONVERSATION_DELETED,
          time    = event.time
        ))
      else None
  ).recover { case ex: Throwable =>
    error(l"error while parsing $event, ${ex.getMessage}", ex)
    None
  }

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
        mlStorage().getMessageAndLikes(mId).map {
          case Some(ml) if ml.message.userId == selfId =>
            val likedContent = ml.message.msgType match {
              case Message.Type.IMAGE_ASSET => LikedContent.PICTURE
              case Message.Type.TEXT |
                   Message.Type.TEXT_EMOJI_ONLY => LikedContent.TEXT_OR_URL
              case _ => LikedContent.OTHER
            }
            Some(NotificationData(
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
                                     isComposite:      Boolean = false) = {
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

object NotificationParser {
  def apply(selfId:       UserId,
            convStorage:  ConversationStorage,
            usersStorage: UsersStorage,
            mlStorage:    () => MessageAndLikesStorage): NotificationParser =
    new NotificationParserImpl(selfId, convStorage, usersStorage, mlStorage)
}

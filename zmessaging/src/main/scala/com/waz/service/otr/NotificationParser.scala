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
import com.waz.log.LogSE._
import com.waz.service.call.CallingService

trait NotificationParser {
  def parse(events: Iterable[Event]): Future[Set[NotificationData]]
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
      case ev: GenericMessageEvent => verbose(l"FCM 1 for $ev"); parse(ev)
      case ev: CallMessageEvent    => verbose(l"FCM 2 for $ev"); parse(ev)
      case ev: UserConnectionEvent => verbose(l"FCM 3 for $ev");Future.successful(None)
      case ev                       => verbose(l"FCM 4 for $ev");Future.successful(None)
    }.map(_.flatten.toSet)

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
    if (!shouldShowNotification(self, conv, event, isReplyOrMention = isSelfMentioned || isReply))
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
    if (!shouldShowNotification(self, conv, event))
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
    if (!shouldShowNotification(self, conv, event))
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
    if (!shouldShowNotification(self, conv, event))
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
    if (!shouldShowNotification(self, conv, event))
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
    if (!shouldShowNotification(self, conv, event, isComposite = true))
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
                                     event:            GenericMessageEvent,
                                     isReplyOrMention: Boolean = false,
                                     isComposite:      Boolean = false): Boolean = {
    val fromSelf          = event.from == self.id
    val notReadYet        = conv.lastRead.isBefore(event.time)
    val notCleared        = conv.cleared.forall(_.isBefore(event.time))
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
}

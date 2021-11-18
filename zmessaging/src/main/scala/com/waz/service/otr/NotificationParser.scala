package com.waz.service.otr

import com.waz.api.Message
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType.LikedContent
import com.waz.content.{ConversationStorage, MessageAndLikesStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.{Asset, Knock, Reaction, Text}
import com.waz.model._
import com.waz.utils._
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.util.{Failure, Try}

trait NotificationParser {
  def parse(events: Seq[Event]): Future[Set[NotificationData]]
}

final class NotificationParserImpl(selfId:       UserId,
                                   decoder:      OtrEventDecoder,
                                   convStorage:  ConversationStorage,
                                   usersStorage: UsersStorage,
                                   mlStorage:    MessageAndLikesStorage
                                  )
  extends NotificationParser with DerivedLogTag {
  import scala.language.existentials
  import Threading.Implicits.Background

  private lazy val selfUser = usersStorage.get(selfId)

  override def parse(events: Seq[Event]): Future[Set[NotificationData]] =
    Future.traverse(events){
      case ev: GenericMessageEvent => parse(ev)
      case ev: CallMessageEvent    => Future.successful(None)
      case ev: UserConnectionEvent => Future.successful(None)
      case _                       => Future.successful(None)
    }.map(_.flatten.toSet)

  private def parse(event: GenericMessageEvent): Future[Option[NotificationData]] =
    (for {
      Some(self)        <- selfUser
      Some(conv)        <- convStorage.getByRemoteId(event.convId)
      (uid, msgContent) =  event.content.unpack
      notification      <- msgContent match {
                             case t: Text     => createTextNotification(uid, self, conv, event, t)
                             case a: Asset    => createAssetNotification(uid, self, conv, event, a)
                             case k: Knock    => createPingNotification(uid, self, conv, event, k)
                             case r: Reaction => createLikeNotification(uid, self, conv, event, r)
                             case _           => Future.successful(None)
                           }
    } yield notification
  ).recover { case _ => None }

  private def createTextNotification(uid:   Uid,
                                     self:  UserData,
                                     conv:  ConversationData,
                                     event: GenericMessageEvent,
                                     t:     Text) = {
    val (text, mentions, _, quote, _) = t.unpack
    val isSelfMentioned = mentions.flatMap(_.userId).contains(selfId)
    val isReply = quote.isDefined
    Future.successful {
      if (shouldShowNotification(self, conv, event, isReplyOrMention = isSelfMentioned || isReply))
        Some(NotificationData(
          id              = NotId(uid.str),
          msg             = text,
          conv            = conv.id,
          user            = event.from,
          msgType         = NotificationType.TEXT,
          time            = event.time,
          isSelfMentioned = isSelfMentioned,
          isReply         = isReply
        ))
      else None
    }
  }

  private def createAssetNotification(uid:   Uid,
                                      self:  UserData,
                                      conv:  ConversationData,
                                      event: GenericMessageEvent,
                                      a:     Asset) = {
    val (asset, _) = a.unpack
    val msgType =
      if (Mime.Video.supported.contains(asset.mime)) NotificationType.VIDEO_ASSET
      else if (Mime.Audio.supported.contains(asset.mime)) NotificationType.AUDIO_ASSET
      else if (Mime.Image.supported.contains(asset.mime)) NotificationType.IMAGE_ASSET
      else NotificationType.ANY_ASSET
    Future.successful {
      if (shouldShowNotification(self, conv, event)) {
        Some(NotificationData(
          id = NotId(uid.str),
          conv = conv.id,
          user = event.from,
          msgType = msgType,
          time = event.time
        ))
      } else None
    }
  }

  private def createPingNotification(uid:   Uid,
                                     self:  UserData,
                                     conv:  ConversationData,
                                     event: GenericMessageEvent,
                                     k:     Knock) =
    Future.successful {
      if (shouldShowNotification(self, conv, event))
        Some(NotificationData(
          id      = NotId(uid.str),
          conv    = conv.id,
          user    = event.from,
          msgType = NotificationType.KNOCK,
          time    = event.time
        ))
      else None
    }

  private def createLikeNotification(uid:   Uid,
                                     self:  UserData,
                                     conv:  ConversationData,
                                     event: GenericMessageEvent,
                                     r:     Reaction) = {
    val (mId, action) = r.unpack
    if (action != Liking.Action.Like || !shouldShowNotification(self, conv, event))
      Future.successful(None)
    else
      mlStorage.getMessageAndLikes(mId).map {
        case Some(ml) if ml.message.userId == selfId =>
          val likedContent = ml.message.msgType match {
            case Message.Type.IMAGE_ASSET     => LikedContent.PICTURE
            case Message.Type.TEXT |
                 Message.Type.TEXT_EMOJI_ONLY => LikedContent.TEXT_OR_URL
            case _                            => LikedContent.OTHER
          }
          Some(NotificationData(
            id           = NotId(uid.str),
            msg          = if (ml.message.isEphemeral) "" else ml.message.contentString,
            conv         = conv.id,
            user         = event.from,
            msgType      = NotificationType.LIKE,
            time         = event.time,
            likedContent = Some(likedContent)
          ))
        case _ => None
      }
  }

  private def shouldShowNotification(self:             UserData,
                                     conv:             ConversationData,
                                     event:            GenericMessageEvent,
                                     isReplyOrMention: Boolean = false,
                                     isComposite:      Boolean = false): Boolean = {
    val fromSelf = event.from == self.id
    val notReadYet = conv.lastRead.isBefore(event.time)
    val notCleared = conv.cleared.forall(_.isBefore(event.time))
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

object NotificationParser extends DerivedLogTag {
  def apply(selfId:       UserId,
            decoder:      OtrEventDecoder,
            convStorage:  ConversationStorage,
            usersStorage: UsersStorage,
            mlStorage:    MessageAndLikesStorage): NotificationParser =
    new NotificationParserImpl(selfId, decoder, convStorage, usersStorage, mlStorage)

  def decodeOtrMessageAdd(event: PushNotificationEvent): Option[OtrMessageEvent] =
    Try(ConversationEvent.ConversationEventDecoder(event.event.toJson).asInstanceOf[OtrMessageEvent])
      .recoverWith {
        case err: Throwable =>
          error(l"Unable to decode an OtrMessageAdd event: $event", err)
          Failure(err)
      }
      .toOption
}

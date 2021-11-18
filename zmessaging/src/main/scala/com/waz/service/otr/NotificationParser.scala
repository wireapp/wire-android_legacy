package com.waz.service.otr

import com.waz.api.NotificationsHandler.NotificationType
import com.waz.content.{ConversationStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.{Text, Asset, Knock}
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
                                   usersStorage: UsersStorage)
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

  private def parse(event: GenericMessageEvent): Future[Option[NotificationData]] = {
    (for {
      Some(self)        <- selfUser
      Some(conv)        <- convStorage.getByRemoteId(event.convId)
      (uid, msgContent) =  event.content.unpack
    } yield
        msgContent match {
          case t: Text  => createTextNotification(uid, self, conv, event, t)
          case a: Asset => createAssetNotification(uid, self, conv, event, a)
          case k: Knock => createKnockNotification(uid, self, conv, event, k)
          case _        => None
        }).recover { case _ => None }
  }

  private def createTextNotification(uid: Uid,
                                     self: UserData,
                                     conv: ConversationData,
                                     event: GenericMessageEvent,
                                     t: Text) = {
    val (text, mentions, _, quote, _) = t.unpack
    val isSelfMentioned = mentions.flatMap(_.userId).contains(selfId)
    val isReply = quote.isDefined
    if (shouldShowNotification(self, conv, event, isReplyOrMention = isSelfMentioned || isReply, isComposite = false)) {
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
    } else None
  }

  private def createAssetNotification(uid: Uid,
                                      self: UserData,
                                      conv: ConversationData,
                                      event: GenericMessageEvent,
                                      a: Asset) = {
    val (asset, _) = a.unpack
    val msgType =
      if (Mime.Video.supported.contains(asset.mime)) NotificationType.VIDEO_ASSET
      else if (Mime.Audio.supported.contains(asset.mime)) NotificationType.AUDIO_ASSET
      else if (Mime.Image.supported.contains(asset.mime)) NotificationType.IMAGE_ASSET
      else NotificationType.ANY_ASSET
    if (shouldShowNotification(self, conv, event, isReplyOrMention = false, isComposite = false)) {
      Some(NotificationData(
        id      = NotId(uid.str),
        conv    = conv.id,
        user    = event.from,
        msgType = msgType,
        time    = event.time
      ))
    } else None
  }

  private def createKnockNotification(uid: Uid,
                                      self: UserData,
                                      conv: ConversationData,
                                      event: GenericMessageEvent,
                                      k: Knock) =
    if (shouldShowNotification(self, conv, event, isReplyOrMention = false, isComposite = false)) {
      Some(NotificationData(
        id      = NotId(uid.str),
        conv    = conv.id,
        user    = event.from,
        msgType = NotificationType.KNOCK,
        time    = event.time
      ))
    } else None

  private def shouldShowNotification(self:             UserData,
                                     conv:             ConversationData,
                                     event:            GenericMessageEvent,
                                     isReplyOrMention: Boolean,
                                     isComposite:      Boolean): Boolean = {
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
            usersStorage: UsersStorage): NotificationParser =
    new NotificationParserImpl(selfId, decoder, convStorage, usersStorage)

  def decodeOtrMessageAdd(event: PushNotificationEvent): Option[OtrMessageEvent] =
    Try(ConversationEvent.ConversationEventDecoder(event.event.toJson).asInstanceOf[OtrMessageEvent])
      .recoverWith {
        case err: Throwable =>
          error(l"Unable to decode an OtrMessageAdd event: $event", err)
          Failure(err)
      }
      .toOption
}

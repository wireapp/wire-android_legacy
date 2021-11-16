package com.waz.service.otr

import com.waz.api.NotificationsHandler.NotificationType
import com.waz.content.ConversationStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.Text
import com.waz.model._
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.util.{Failure, Try}

trait NotificationParser {
  def parse(events: Seq[Event]): Future[Set[NotificationData]]
}

final class NotificationParserImpl(selfId: UserId,
                                   decoder: OtrEventDecoder,
                                   convStorage: ConversationStorage)
  extends NotificationParser with DerivedLogTag {
  import scala.language.existentials
  import Threading.Implicits.Background

  override def parse(events: Seq[Event]): Future[Set[NotificationData]] =
    Future.traverse(events){
      case ev: GenericMessageEvent => parse(ev)
      case ev: CallMessageEvent => Future.successful(None)
      case ev: UserConnectionEvent => Future.successful(None)
      case _ => Future.successful(None)
    }.map(_.flatten.toSet)

  private def parse(event: GenericMessageEvent): Future[Option[NotificationData]] =
    convStorage.getByRemoteId(event.convId).map {
      case Some(conv) =>
        val (uid, msgContent) = event.content.unpack
        msgContent match {
          case t: Text =>
            val (text, mentions, _, quote, _) = t.unpack
            Some(NotificationData(
              id              = NotId(uid.str),
              msg             = text,
              conv            = conv.id,
              user            = event.from,
              msgType         = NotificationType.TEXT,
              time            = event.time,
              isSelfMentioned = mentions.flatMap(_.userId).contains(selfId),
              isReply         = quote.isDefined
            ))
          case _ => None
        }
      case _ => None
  }
}

object NotificationParser extends DerivedLogTag {
  def apply(selfId: UserId,
            decoder: OtrEventDecoder,
            convStorage: ConversationStorage): NotificationParser =
    new NotificationParserImpl(selfId, decoder, convStorage)

  def decodeOtrMessageAdd(event: PushNotificationEvent): Option[OtrMessageEvent] =
    Try(ConversationEvent.ConversationEventDecoder(event.event.toJson).asInstanceOf[OtrMessageEvent])
      .recoverWith {
        case err: Throwable =>
          error(l"Unable to decode an OtrMessageAdd event: $event", err)
          Failure(err)
      }
      .toOption
}

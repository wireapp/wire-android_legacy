package com.waz.service.otr

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.Event.EventDecoder
import com.waz.model.GenericContent.{Calling, ClientAction, External}
import com.waz.model._
import com.waz.utils.crypto.AESUtils

import scala.util.{Failure, Try}

trait OtrEventDecoder {
  def decode(event: PushNotificationEvent): Option[Event]
}

final class OtrEventDecoderImpl(selfUserId:    UserId, currentDomain: Domain)
  extends OtrEventDecoder with DerivedLogTag {
  import OtrEventDecoder._

  override def decode(event: PushNotificationEvent): Option[Event] =
    event.plain match {
      case Some(bytes) if event.event.isOtrMessageAdd =>
        for {
          ev     <- decodeOtrMessageAdd(event)
          gm     <- decode(bytes)
          result <- parseGenericMessage(ev, gm)
        } yield result
      case _ =>
        Some(EventDecoder(event.event.toJson))
    }

  private def parseGenericMessage(otrMsg: OtrMessageEvent, genericMsg: GenericMessage): Option[MessageEvent] = {
    val conv = otrMsg.convId
    val convDomain = otrMsg.convDomain
    val time = otrMsg.time
    val from = otrMsg.from
    val fromDomain = otrMsg.fromDomain
    val sender = otrMsg.sender
    val extData = otrMsg.externalData
    val localTime = otrMsg.localTime
    if (!(genericMsg.isBroadcastMessage || from == selfUserId) && conv.str == selfUserId.str) {
      warn(l"Received a message to the self-conversation by someone else than self and it's not a broadcast")
      None
    } else {
      genericMsg.unpackContent match {
        case ext: External =>
          val (key, sha) = ext.unpack
          decodeExternal(key, Some(sha), extData) match {
            case None =>
              error(l"External message could not be decoded External($key, $sha), data: $extData")
              Some(OtrErrorEvent(conv, convDomain, time, from, fromDomain, DecryptionError("symmetric decryption failed", Some(OtrError.ERROR_CODE_SYMMETRIC_DECRYPTION_FAILED), from, sender)))
            case Some(msg :GenericMessage) =>
              msg.unpackContent match {
                case calling: Calling =>
                  Some(CallMessageEvent(conv, convDomain, time, from, fromDomain, sender, calling.unpack)) //call messages need sender client id
                case _ =>
                  Some(GenericMessageEvent(conv, convDomain, time, from, fromDomain, msg).withLocalTime(localTime))
              }
          }
        case ClientAction.SessionReset =>
          Some(SessionReset(conv, convDomain, time, from, fromDomain, sender))
        case calling: Calling =>
          Some(CallMessageEvent(conv, convDomain, time, from, fromDomain, sender, calling.unpack)) //call messages need sender client id
        case _ =>
          Some(GenericMessageEvent(conv, convDomain, time, from, fromDomain, genericMsg).withLocalTime(localTime))
      }
    }
  }

  private def decodeExternal(key: AESKey, sha: Option[Sha256], extData: Option[Array[Byte]]) =
    for {
      data  <- extData if sha.forall(_.matches(data))
      plain <- Try(AESUtils.decrypt(key, data)).toOption
      msg   <- decode(plain)
    } yield msg

  private def decode(bytes: Array[Byte]): Option[GenericMessage] =
    Try(com.waz.model.GenericMessage.apply(bytes)).toOption
}

object OtrEventDecoder extends DerivedLogTag {
  def apply(selfUserId: UserId, currentDomain: Domain): OtrEventDecoder =
    new OtrEventDecoderImpl(selfUserId, currentDomain)

  def decodeOtrMessageAdd(event: PushNotificationEvent): Option[OtrMessageEvent] =
    Try(ConversationEvent.ConversationEventDecoder(event.event.toJson).asInstanceOf[OtrMessageEvent])
      .recoverWith {
        case err: Throwable =>
          error(l"Unable to decode an OtrMessageAdd event: $event", err)
          Failure(err)
      }
      .toOption
}

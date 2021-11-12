package com.waz.service.otr

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.{Calling, ClientAction, External}
import com.waz.model.{AESKey, CallMessageEvent, DecryptionError, Domain, Duplicate, GenericMessage, GenericMessageEvent, IdentityChangedError, MessageEvent, OtrError, OtrErrorEvent, OtrEvent, OtrMessageEvent, SessionReset, Sha256, UserId}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.push.PushNotificationEventsStorage.PlainWriter
import com.waz.threading.Threading
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.CryptoException

import scala.concurrent.Future
import scala.util.Try

trait OtrEventDecoder {
  def decryptStoredOtrEvent(ev: OtrEvent, eventWriter: PlainWriter): Future[Either[OtrError, Unit]]
  def parseGenericMessage(otrMsg: OtrMessageEvent, genericMsg: GenericMessage): Option[MessageEvent]
  def decode(bytes: Array[Byte]): Option[GenericMessage]
}

final class OtrEventDecoderImpl(selfUserId:    UserId,
                                currentDomain: Domain,
                                clients:       OtrClientsService,
                                sessions:      CryptoSessionService)
  extends OtrEventDecoder with DerivedLogTag {
  import OtrEventDecoder._
  import Threading.Implicits.Background

  override def decryptStoredOtrEvent(ev: OtrEvent, eventWriter: PlainWriter): Future[Either[OtrError, Unit]] =
    clients.getOrCreateClient(ev.from, ev.sender).flatMap { _ =>
      sessions.decryptMessage(SessionId(ev, currentDomain), ev.ciphertext, eventWriter)
        .map(Right(_))
        .recoverWith {
          case e: CryptoException =>
            import CryptoException.Code._
            e.code match {
              case DUPLICATE_MESSAGE =>
                verbose(l"detected duplicate message for event: $ev")
                Future.successful(Left(Duplicate))
              case OUTDATED_MESSAGE =>
                error(l"detected outdated message for event: $ev")
                Future.successful(Left(Duplicate))
              case REMOTE_IDENTITY_CHANGED =>
                Future.successful(Left(IdentityChangedError(ev.from, ev.sender)))
              case _ =>
                Future.successful(Left(DecryptionError(e.getMessage, Some(e.code.ordinal()), ev.from, ev.sender)))
            }
        }
    }

  override def parseGenericMessage(otrMsg: OtrMessageEvent, genericMsg: GenericMessage): Option[MessageEvent] = {
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

  // This is a utility method. In the codebase we have two classes called GenericMessage, each with
  // a few overloaded constructors. The compiler sometimes get confused. This method can be used to
  // call the most popular of them in a simple way.
  // It also allows for mocking in unit tests
  override def decode(bytes: Array[Byte]): Option[GenericMessage] =
    Try(com.waz.model.GenericMessage.apply(bytes)).toOption
}

object OtrEventDecoder {
  def apply(selfUserId:    UserId,
            currentDomain: Domain,
            clients:       OtrClientsService,
            sessions:      CryptoSessionService): OtrEventDecoder =
    new OtrEventDecoderImpl(selfUserId, currentDomain, clients, sessions)

}

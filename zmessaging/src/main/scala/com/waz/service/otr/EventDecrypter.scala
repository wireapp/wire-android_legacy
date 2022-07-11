package com.waz.service.otr

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{ConversationEvent, DecryptionError, Domain, Duplicate, IdentityChangedError, OtrError, OtrErrorEvent, OtrEvent, PushNotificationEvent, UserId}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.push.PushNotificationEventsStorage
import com.waz.service.push.PushNotificationEventsStorage.PlainWriter
import com.waz.service.tracking.TrackingService
import com.waz.utils.crypto.AESUtils
import com.wire.cryptobox.CryptoException

import java.util.UUID
import scala.concurrent.Future

trait EventDecrypter {
  def processEncryptedEvents(events: Seq[PushNotificationEvent], tag: UUID): Future[Unit]
}

class EventDecrypterImpl(selfId:        UserId,
                         currentDomain: Domain,
                         storage:       PushNotificationEventsStorage,
                         clients:       OtrClientsService,
                         sessions:      CryptoSessionService,
                         tracking:      () => TrackingService) extends EventDecrypter with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background
  import EventDecrypter._

  override def processEncryptedEvents(events: Seq[PushNotificationEvent], tag: UUID): Future[Unit] = {
    verbose(l"Starting to processEncryptedEvents ${tag}: ${events.size}")
    Future.traverse(events) {
      case event @ GetOtrEvent(otrEvent) =>
        verbose(l"$tag: Decrypting event ${event.index}: ${AESUtils.base64(otrEvent.ciphertext)}")
        decrypt(event.index, otrEvent, tag)
      case event                         =>
        verbose(l"$tag: Event is set as already decrypted: ${event.index}: $event")
        storage.setAsDecrypted(event.index)
    }.map(_ => ())
  }

  private def decrypt(index: Int, otrEvent: OtrEvent, tag: UUID): Future[Unit] =
    decryptStoredOtrEvent(otrEvent, storage.writeClosure(index), tag).flatMap {
      case Left(Duplicate) =>
        verbose(l"$tag: Ignoring duplicate message with index $index: ${AESUtils.base64(otrEvent.ciphertext)}")
        storage.removeEncryptedEvent(index)
      case Left(err) =>
        val e = OtrErrorEvent(otrEvent.convId, otrEvent.convDomain, otrEvent.time, otrEvent.from, otrEvent.fromDomain, err)
        error(l"$tag: Got error when decrypting message with index $index: ${AESUtils.base64(otrEvent.ciphertext)}: $e")
        tracking().msgDecryptionFailed(otrEvent.convId, selfId)
        storage.writeError(index, e)
      case Right(_) =>
        Future.successful(())
    }

  private def decryptStoredOtrEvent(ev: OtrEvent, eventWriter: PlainWriter, tag: UUID): Future[Either[OtrError, Unit]] =
    clients.getOrCreateClient(ev.from, ev.sender).flatMap { _ =>
      verbose(l"$tag: after getOrCreateEvent for event ${AESUtils.base64(ev.ciphertext)}")
      sessions.decryptMessage(SessionId(ev, currentDomain), ev.ciphertext, eventWriter)
        .map(Right(_))
        .recoverWith {
          case e: CryptoException =>
            import CryptoException.Code._
            e.code match {
              case DUPLICATE_MESSAGE =>
                verbose(l"$tag: detected duplicate message for event with text: ${AESUtils.base64(ev.ciphertext)}")
                Future.successful(Left(Duplicate))
              case OUTDATED_MESSAGE =>
                error(l"$tag: detected outdated message for event: ${AESUtils.base64(ev.ciphertext)}")
                Future.successful(Left(Duplicate))
              case REMOTE_IDENTITY_CHANGED =>
                error(l"$tag: remove identity changed for event: ${AESUtils.base64(ev.ciphertext)}")
                Future.successful(Left(IdentityChangedError(ev.from, ev.sender)))
              case _ =>
                verbose(l"$tag: error ${e.code.ordinal()} in decrypting event: ${AESUtils.base64(ev.ciphertext)}")
                Future.successful(Left(DecryptionError(e.getMessage, Some(e.code.ordinal()), ev.from, ev.sender)))
            }
        }
    }
}

object EventDecrypter {
  object GetOtrEvent extends DerivedLogTag {
    def unapply(event: PushNotificationEvent): Option[OtrEvent] =
      if (!event.event.isOtrMessageAdd) None
      else ConversationEvent.ConversationEventDecoder(event.event.toJson) match {
        case otrEvent: OtrEvent => Some(otrEvent)
        case _                  => error(l"Unrecognized event: ${event.event}"); None
      }
  }

  def apply(selfId: UserId,
            currentDomain: Domain,
            storage:       PushNotificationEventsStorage,
            clients:       OtrClientsService,
            sessions:      CryptoSessionService,
            tracking:      () => TrackingService): EventDecrypter =
    new EventDecrypterImpl(selfId, currentDomain, storage, clients, sessions, tracking)
}

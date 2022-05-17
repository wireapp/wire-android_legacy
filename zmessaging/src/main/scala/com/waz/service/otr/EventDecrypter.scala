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

import scala.concurrent.Future

trait EventDecrypter {
  def processEncryptedEvents(events: Seq[PushNotificationEvent]): Future[Unit]
}

class EventDecrypterImpl(selfId:        UserId,
                         currentDomain: Domain,
                         storage:       PushNotificationEventsStorage,
                         clients:       OtrClientsService,
                         sessions:      CryptoSessionService,
                         tracking:      () => TrackingService) extends EventDecrypter with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background
  import EventDecrypter._

  override def processEncryptedEvents(events: Seq[PushNotificationEvent]): Future[Unit] =
    Future.traverse(events) {
      case event @ GetOtrEvent(otrEvent) => decrypt(event.index, otrEvent)
      case event                         => storage.setAsDecrypted(event.index)
    }.map(_ => ())

  private def decrypt(index: Int, otrEvent: OtrEvent): Future[Unit] =
    decryptStoredOtrEvent(otrEvent, storage.writeClosure(index)).flatMap {
      case Left(Duplicate) =>
        verbose(l"Ignoring duplicate message")
        storage.remove(index)
      case Left(err) =>
        val e = OtrErrorEvent(otrEvent.convId, otrEvent.convDomain, otrEvent.time, otrEvent.from, otrEvent.fromDomain, err)
        error(l"Got error when decrypting: $e")
        tracking().msgDecryptionFailed(otrEvent.convId, selfId)
        storage.writeError(index, e)
      case Right(_) =>
        Future.successful(())
    }

  private def decryptStoredOtrEvent(ev: OtrEvent, eventWriter: PlainWriter): Future[Either[OtrError, Unit]] =
    clients.getOrCreateClient(ev.from, ev.sender).flatMap { _ =>
      sessions.decryptMessage(SessionId(ev, currentDomain), ev.ciphertext, eventWriter)
        .map(Right(_))
        .recoverWith {
          case e: CryptoException =>
            import CryptoException.Code._
            e.code match {
              case DUPLICATE_MESSAGE =>
                verbose(l"detected duplicate message for event with text: ${AESUtils.base64(ev.ciphertext)}")
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

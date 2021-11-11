package com.waz.service

import com.waz.model.otr.{Client, ClientId}
import com.waz.model.{Domain, OtrMessageEvent, RConvId, RemoteInstant, UserId}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, OtrClientsService, OtrEventDecoder}
import com.waz.service.push.PushNotificationEventsStorage
import com.waz.specs.AndroidFreeSpec
import org.threeten.bp.Instant

import scala.concurrent.Future

class OtrEventDecoderSpec extends AndroidFreeSpec {
  private val selfUserId = UserId("selfUserId")
  private val currentDomain = Domain("staging.zinfra.io")
  private val clients = mock[OtrClientsService]
  private val sessions = mock[CryptoSessionService]

  def decoder: OtrEventDecoder = OtrEventDecoder(selfUserId, currentDomain, clients, sessions)

  scenario("decrypt a valid otr event") {
    val from = UserId()
    val sender = ClientId()
    val event =
      OtrMessageEvent(RConvId(),
        currentDomain,
        RemoteInstant(Instant.now()),
        from,
        currentDomain,
        sender,
        ClientId(),
        "encryptedtext".getBytes,
        None)
    val eventWriter: PushNotificationEventsStorage.PlainWriter = _ => Future.successful(())
    val sessionId = SessionId(event, currentDomain)

    (clients.getOrCreateClient _)
      .expects(from, sender)
      .anyNumberOfTimes()
      .returning {
        Future.successful(Client(sender))
      }

    (sessions.decryptMessage _)
      .expects(sessionId, event.ciphertext, eventWriter)
      .anyNumberOfTimes()
      .returning {
        Future.successful(())
      }

    result(decoder.decryptStoredOtrEvent(event, eventWriter)) shouldEqual Right(())
  }
}

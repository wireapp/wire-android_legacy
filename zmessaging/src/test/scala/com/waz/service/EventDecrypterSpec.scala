package com.waz.service

import com.waz.model.otr.{Client, ClientId}
import com.waz.model._
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr.{CryptoSessionService, EventDecrypter, OtrClientsService}
import com.waz.service.push.PushNotificationEventsStorage
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.EncodedEvent
import org.threeten.bp.Instant

import scala.concurrent.Future

class EventDecrypterSpec extends AndroidFreeSpec {
  import EventDecrypterSpec._

  private val storage = mock[PushNotificationEventsStorage]
  private val clients = mock[OtrClientsService]
  private val sessions = mock[CryptoSessionService]

  def decrypter: EventDecrypter = EventDecrypter(userId, domain, storage, clients, sessions, () => tracking)

  scenario("decrypt a valid otr event") {
    val from = UserId()
    val sender = ClientId()

    val event =
      OtrMessageEvent(
        RConvId(),
        domain,
        RemoteInstant(Instant.now()),
        from,
        domain,
        sender,
        ClientId(),
        emptyBytes,
        None
      )

    val index = 1

    val encryptedEvent =
      PushNotificationEvent(
        Uid(),
        index,
        decrypted = false,
        EncodedEvent(eventJsonStr(from = from, sender = sender, tpe = "conversation.delete")),
        transient = false
      )
    val eventWriter: PushNotificationEventsStorage.PlainWriter = _ => Future.successful(())
    val sessionId = SessionId(event, domain)

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

    (storage.setAsDecrypted _).expects((encryptedEvent.pushId, encryptedEvent.index)).once().returning(Future.successful(()))

    result(decrypter.processEncryptedEvents(Seq(encryptedEvent)))
  }

  // TODO: Write more unit tests
}

object EventDecrypterSpec {
  val convId: ConvId = ConvId()
  val userId: UserId = UserId()
  val clientId: ClientId = ClientId()
  val domain: Domain = Domain("staging.zinfra.io")
  val encryptedBytes: Array[Byte] = "encryptedstring".getBytes
  val decryptedBytes: Array[Byte] = "decryptedstring".getBytes
  val emptyBytes: Array[Byte] = Array.empty[Byte]

  def eventJsonStr(convId: ConvId = this.convId,
                   clientId: ClientId = this.clientId,
                   domain: Domain = this.domain,
                   from: UserId = UserId(),
                   sender: ClientId = ClientId(),
                   tpe: String = "conversation.otr-message-add"
                  ): String =
    s"""
       |      {
       |        "qualified_conversation": {
       |          "domain": "${domain.str}",
       |          "id": "${convId.str}"
       |        },
       |        "conversation": "${convId.str}",
       |        "time": "2021-11-08T16:31:28.872Z",
       |        "data": {
       |          "text": "encoded_string",
       |          "data": "",
       |          "sender": "${sender.str}",
       |          "recipient": "${clientId.str}"
       |        },
       |        "from": "${from.str}",
       |        "qualified_from": {
       |          "domain": "${domain.str}",
       |          "id": "${from.str}"
       |        },
       |        "type": "$tpe"
       |      }
       |""".stripMargin.trim
}

package com.waz.model

import com.waz.model.GenericContent.{Ephemeral, Text}
import com.waz.model.Messages.LegalHoldStatus
import com.waz.specs.AndroidFreeSpec

import scala.concurrent.duration._
import scala.language.existentials

class GenericMessageSpec extends AndroidFreeSpec {

  feature("Updating the legal hold status") {

    scenario("for an ephemeral message ") {
      // Given
      val message = GenericMessage(Uid("messageId"), expiration = Some(10.second), Text("Hello!"))
      message.legalHoldStatus shouldBe LegalHoldStatus.UNKNOWN

      // When
      val result = message.withLegalHoldStatus(LegalHoldStatus.ENABLED)

      // Then
      result.legalHoldStatus shouldBe LegalHoldStatus.ENABLED

      // The rest of the message remains unchanged.
      result.unpack match {
        case (id, content: Ephemeral) =>
          id.str shouldEqual "messageId"

          val (expiration, ephemeralContent) = content.unpack
          expiration shouldEqual Some(10.seconds)

          ephemeralContent match {
            case text: GenericContent.Text => text.unpack._1 shouldEqual "Hello!"
            case _                         => fail("Expected a text message")
          }

        case _ =>
          fail("Expected an ephemeral")
      }
    }

    scenario("for a non-ephemeral message ") {
      // Given
      val message = GenericMessage(Uid("messageId"), expiration = None, Text("Hello!"))
      message.legalHoldStatus shouldBe LegalHoldStatus.UNKNOWN

      // When
      val result = message.withLegalHoldStatus(LegalHoldStatus.ENABLED)

      // Then
      result.legalHoldStatus shouldBe LegalHoldStatus.ENABLED

      // The rest of the message remains unchanged.
      val (id, content) = result.unpack
      id.str shouldEqual "messageId"

      content match {
        case text: GenericContent.Text => text.unpack._1 shouldEqual "Hello!"
        case _                         => fail("Expected a text message")
      }
    }
  }
}

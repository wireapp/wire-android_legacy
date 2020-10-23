/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz

import com.google.protobuf.nano.{CodedInputByteBufferNano, MessageNano}
import com.waz.model.nano.Messages
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{JsonDecoder, JsonEncoder, returning}
import org.json.JSONObject

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

package object model {

  case class Name(str: String) {
    def length = str.length

    override def toString: String = str

    def isEmpty: Boolean = str.isEmpty
    def nonEmpty: Boolean = str.nonEmpty

    def substring(beginIndex: Int, endIndex: Int) =
      Name(str.substring(beginIndex, endIndex))
  }

  object Name extends (String => Name) {
    implicit def toNameString(name: Name): String = name.str
    implicit def fromNameString(str: String): Name = Name(str)
    val Empty = Name("")
  }

  trait ProtoDecoder[A <: MessageNano] {
    def apply(data: Array[Byte]): A
    def apply(in: CodedInputByteBufferNano): A
  }

  val Proto = GenericContent

  type GenericMessage = Messages.GenericMessage
  object GenericMessage {
    import GenericContent._

    def apply[A: GenericContent](id: Uid, content: A): GenericMessage =
      returning(new Messages.GenericMessage()) { msg =>
        msg.messageId = id.str
        implicitly[GenericContent[A]].set(msg)(content)
      }

    def apply[A: EphemeralContent : GenericContent](id: Uid, expiration: Option[FiniteDuration], content: A): GenericMessage =
      returning(new Messages.GenericMessage()) { msg =>
        msg.messageId = id.str
        if (expiration.isEmpty) {
          implicitly[GenericContent[A]].set(msg)(content)
        } else {
          Ephemeral.set(msg)(Ephemeral(expiration, content))
        }
      }

    def apply(bytes: Array[Byte]): GenericMessage = Messages.GenericMessage.parseFrom(bytes)

    def unapply(msg: GenericMessage): Option[(Uid, Any)] = Some((Uid(msg.messageId), content(msg)))

    def toByteArray(msg: GenericMessage) = MessageNano.toByteArray(msg)

    import Messages.{GenericMessage => GM}

    def isBroadcastMessage(msg: GenericMessage): Boolean = msg.getContentCase match {
      case GM.AVAILABILITY_FIELD_NUMBER => true
      case _ => false
    }

    def content(msg: GenericMessage) = msg.getContentCase match {
      case GM.ASSET_FIELD_NUMBER                    => msg.getAsset
      case GM.CALLING_FIELD_NUMBER                  => msg.getCalling
      case GM.CLEARED_FIELD_NUMBER                  => msg.getCleared
      case GM.CLIENTACTION_FIELD_NUMBER             => ClientAction(msg.getClientAction)
      case GM.DELETED_FIELD_NUMBER                  => msg.getDeleted
      case GM.EDITED_FIELD_NUMBER                   => msg.getEdited
      case GM.EXTERNAL_FIELD_NUMBER                 => msg.getExternal
      case GM.HIDDEN_FIELD_NUMBER                   => msg.getHidden
      case GM.IMAGE_FIELD_NUMBER                    => msg.getImage
      case GM.KNOCK_FIELD_NUMBER                    => msg.getKnock
      case GM.LASTREAD_FIELD_NUMBER                 => msg.getLastRead
      case GM.REACTION_FIELD_NUMBER                 => msg.getReaction
      case GM.TEXT_FIELD_NUMBER                     => msg.getText
      case GM.LOCATION_FIELD_NUMBER                 => msg.getLocation
      case GM.CONFIRMATION_FIELD_NUMBER             => msg.getConfirmation
      case GM.EPHEMERAL_FIELD_NUMBER                => msg.getEphemeral
      case GM.AVAILABILITY_FIELD_NUMBER             => msg.getAvailability
      case GM.COMPOSITE_FIELD_NUMBER                => msg.getComposite
      case GM.BUTTONACTION_FIELD_NUMBER             => msg.getButtonAction
      case GM.BUTTONACTIONCONFIRMATION_FIELD_NUMBER => msg.getButtonActionConfirmation
      case _                                        => Unknown
    }

    object TextMessage {

      def apply(text: String): GenericMessage = GenericMessage(Uid(), Text(text, Nil, Nil, expectsReadConfirmation = false))

      def apply(text: String, mentions: Seq[com.waz.model.Mention], expectsReadConfirmation: Boolean): GenericMessage = GenericMessage(Uid(), Text(text, mentions, Nil, expectsReadConfirmation))

      def apply(text: String, mentions: Seq[com.waz.model.Mention], links: Seq[LinkPreview], expectsReadConfirmation: Boolean): GenericMessage = GenericMessage(Uid(), Text(text, mentions, links, expectsReadConfirmation))

      def apply(text: String, mentions: Seq[com.waz.model.Mention], links: Seq[LinkPreview], quote: Option[Quote], expectsReadConfirmation: Boolean): GenericMessage = GenericMessage(Uid(), Text(text, mentions, links, quote, expectsReadConfirmation))

      def apply(msg: MessageData): GenericMessage = GenericMessage(msg.id.uid, msg.ephemeral, Text(msg.contentString, msg.content.flatMap(_.mentions), Nil, msg.protoQuote, msg.expectsRead.getOrElse(false)))

      def unapply(msg: GenericMessage): Option[(String, Seq[com.waz.model.Mention], Seq[LinkPreview], Option[Quote], Boolean)] = msg match {
        case GenericMessage(_, t @ Text(content, mentions, links, quote)) =>
          Some((content, mentions, links, quote, t.expectsReadConfirmation))
        case GenericMessage(_, Ephemeral(_, t @ Text(content, mentions, links, quote))) =>
          Some((content, mentions, links, quote, t.expectsReadConfirmation))
        case GenericMessage(_, MsgEdit(_, t @ Text(content, mentions, links, quote))) =>
          Some((content, mentions, links, quote, t.expectsReadConfirmation))
        case _ =>
          None
      }

      def updateMentions(msg: GenericMessage, newMentions: Seq[com.waz.model.Mention]): GenericMessage = msg match {
        case GenericMessage(uid, t @ Text(text, mentions, links, quote)) if mentions != newMentions =>
          GenericMessage(uid, Text(text, newMentions, links, quote, t.expectsReadConfirmation))
        case _ =>
          msg
      }
    }

    //TODO Dean: this can lead to some very tricky problems - try to get around the Any...
    object GenericMessageContent {
      def unapply(msg: GenericMessage): Option[Any] = msg match {
        case GenericMessage(_, Ephemeral(_, content)) => Some(content)
        case GenericMessage(_, content)               => Some(content)
      }
    }

    implicit object JsDecoder extends JsonDecoder[GenericMessage] {
      override def apply(implicit js: JSONObject): GenericMessage = GenericMessage(AESUtils.base64(js.getString("proto")))
    }

    implicit object JsEncoder extends JsonEncoder[GenericMessage] {
      override def apply(v: GenericMessage): JSONObject = JsonEncoder { o =>
        o.put("proto", AESUtils.base64(MessageNano.toByteArray(v)))
      }
    }

    implicit object MessageDecoder extends ProtoDecoder[GenericMessage] {
      override def apply(data: Array[Byte]): GenericMessage = GenericMessage(data)
      override def apply(in: CodedInputByteBufferNano): GenericMessage = Messages.GenericMessage.parseFrom(in)
    }
  }
}

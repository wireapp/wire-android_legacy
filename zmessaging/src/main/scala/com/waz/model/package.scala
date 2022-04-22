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

import com.google.protobuf.{CodedInputStream, MessageLite}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.{Ephemeral, Unknown}
import com.waz.model.Messages.LegalHoldStatus
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

import scala.concurrent.duration.FiniteDuration
import scala.language.existentials
import scala.language.implicitConversions

package object model {

  final case class Name(str: String) extends AnyVal {
    def length: Int = str.length

    override def toString: String = str

    def isEmpty: Boolean = str.isEmpty
    def nonEmpty: Boolean = str.nonEmpty

    def substring(beginIndex: Int, endIndex: Int): Name =
      Name(str.substring(beginIndex, endIndex))
  }

  object Name extends (String => Name) {
    implicit def toNameString(name: Name): String = name.str
    implicit def fromNameString(str: String): Name = Name(str)
    val Empty: Name = Name("")
  }

  trait ProtoDecoder[A] {
    def apply(data: Array[Byte]): A
    def apply(in: CodedInputStream): A
  }

  final case class GenericMessage(proto: Messages.GenericMessage) extends DerivedLogTag {
    lazy val unpack: (Uid, GenericContent[_]) = (Uid(proto.getMessageId), content)

    def toByteArray: Array[Byte] = proto.toByteArray

    lazy val isBroadcastMessage: Boolean = proto.getContentCase.getNumber match {
      case Messages.GenericMessage.AVAILABILITY_FIELD_NUMBER => true
      case _ => false
    }

    def legalHoldStatus: LegalHoldStatus = unpackContent match {
      case reaction: GenericContent.Reaction   => reaction.proto.getLegalHoldStatus
      case knock: GenericContent.Knock         => knock.proto.getLegalHoldStatus
      case text: GenericContent.Text           => text.proto.getLegalHoldStatus
      case location: GenericContent.Location   => location.proto.getLegalHoldStatus
      case asset: GenericContent.Asset         => asset.proto.getLegalHoldStatus
      case composite: GenericContent.Composite => composite.proto.getLegalHoldStatus
      case _                                   => LegalHoldStatus.UNKNOWN
    }

    def unpackContent: GenericContent[_] = unpack match {
      case (_, eph: Ephemeral) => eph.unpack._2
      case (_, content)        => content
    }

    private def content: GenericContent[_] = proto.getContentCase.getNumber match {
      case Messages.GenericMessage.ASSET_FIELD_NUMBER                    => GenericContent.Asset(proto.getAsset)
      case Messages.GenericMessage.CALLING_FIELD_NUMBER                  => GenericContent.Calling(proto.getCalling)
      case Messages.GenericMessage.CLEARED_FIELD_NUMBER                  => GenericContent.Cleared(proto.getCleared)
      case Messages.GenericMessage.CLIENTACTION_FIELD_NUMBER             => GenericContent.ClientAction(proto.getClientAction.getNumber)
      case Messages.GenericMessage.DELETED_FIELD_NUMBER                  => GenericContent.MsgRecall(proto.getDeleted)
      case Messages.GenericMessage.EDITED_FIELD_NUMBER                   => GenericContent.MsgEdit(proto.getEdited)
      case Messages.GenericMessage.EXTERNAL_FIELD_NUMBER                 => GenericContent.External(proto.getExternal)
      case Messages.GenericMessage.HIDDEN_FIELD_NUMBER                   => GenericContent.MsgDeleted(proto.getHidden)
      case Messages.GenericMessage.IMAGE_FIELD_NUMBER                    => GenericContent.ImageAsset(proto.getImage)
      case Messages.GenericMessage.KNOCK_FIELD_NUMBER                    => GenericContent.Knock(proto.getKnock)
      case Messages.GenericMessage.LASTREAD_FIELD_NUMBER                 => GenericContent.LastRead(proto.getLastRead)
      case Messages.GenericMessage.REACTION_FIELD_NUMBER                 => GenericContent.Reaction(proto.getReaction)
      case Messages.GenericMessage.TEXT_FIELD_NUMBER                     => GenericContent.Text(proto.getText)
      case Messages.GenericMessage.LOCATION_FIELD_NUMBER                 => GenericContent.Location(proto.getLocation)
      case Messages.GenericMessage.CONFIRMATION_FIELD_NUMBER             =>
        proto.getConfirmation match {
          case c if c.getType.getNumber == Messages.Confirmation.Type.DELIVERED_VALUE => GenericContent.DeliveryReceipt(c)
          case c if c.getType.getNumber == Messages.Confirmation.Type.READ_VALUE      => GenericContent.ReadReceipt(c)
          case unknown =>
            warn(l"Unknown confirmation content: $unknown")
            Unknown
        }
      case Messages.GenericMessage.EPHEMERAL_FIELD_NUMBER                => GenericContent.Ephemeral(proto.getEphemeral)
      case Messages.GenericMessage.AVAILABILITY_FIELD_NUMBER             => GenericContent.AvailabilityStatus(proto.getAvailability)
      case Messages.GenericMessage.COMPOSITE_FIELD_NUMBER                => GenericContent.Composite(proto.getComposite)
      case Messages.GenericMessage.BUTTONACTION_FIELD_NUMBER             => GenericContent.ButtonAction(proto.getButtonAction)
      case Messages.GenericMessage.BUTTONACTIONCONFIRMATION_FIELD_NUMBER => GenericContent.ButtonActionConfirmation(proto.getButtonActionConfirmation)
      case Messages.GenericMessage.DATATRANSFER_FIELD_NUMBER             => GenericContent.DataTransfer(proto.getDataTransfer)
      case unknown =>
        warn(l"Unknown content: $unknown")
        Unknown
    }
  }

  object GenericMessage {
    import GenericContent._

    def apply[Proto](id: Uid, content: GenericContent[Proto]): GenericMessage = GenericMessage {
      val builder =
        Messages.GenericMessage.newBuilder
          .setMessageId(id.str)
      content.set(builder)
      builder.build()
    }

    def apply[Proto](id: Uid, expiration: Option[FiniteDuration], content: GenericContent[Proto]): GenericMessage = GenericMessage {
      val builder =
        Messages.GenericMessage.newBuilder
          .setMessageId(id.str)
      expiration match {
        case Some(expiry) =>
          val ephemeralContent: Option[EphemeralContent] = content match {
            case Asset(proto)      => Some(EphemeralAsset(proto, expiry))
            case ImageAsset(proto) => Some(EphemeralImageAsset(proto, expiry))
            case Text(proto)       => Some(EphemeralText(proto, expiry))
            case Location(proto)   => Some(EphemeralLocation(proto, expiry))
            case Knock(proto)      => Some(EphemeralKnock(proto, expiry))
            case _ =>
              error(l"Unable to create ephemeral content - the generic content cannot be turned into ephemeral: $content")(LogTag("GenericMessage"))
              None
          }
          ephemeralContent.foreach(eph => Ephemeral(expiration, eph).set(builder))
        case None => content.set(builder)
      }

      builder.build
    }

    def apply(bytes: Array[Byte]): GenericMessage =
      GenericMessage(Messages.GenericMessage.parseFrom(bytes))

    object TextMessage {

      implicit val log: LogTag = LogTag("TextMessage")

      def apply(text: String,
                legalHoldStatus: Messages.LegalHoldStatus = Messages.LegalHoldStatus.UNKNOWN): GenericMessage =
        GenericMessage(Uid(), Text(text, Nil, Option.empty, expectsReadConfirmation = false, legalHoldStatus))

      def apply(text: String,
                mentions: Seq[com.waz.model.Mention],
                expectsReadConfirmation: Boolean,
                legalHoldStatus: Messages.LegalHoldStatus): GenericMessage =
        GenericMessage(Uid(), Text(text, mentions, Option.empty, expectsReadConfirmation, legalHoldStatus))

      def apply(text: String,
                mentions: Seq[com.waz.model.Mention],
                links: Option[LinkPreview],
                expectsReadConfirmation: Boolean,
                legalHoldStatus: Messages.LegalHoldStatus): GenericMessage =
        GenericMessage(Uid(), Text(text, mentions, links, expectsReadConfirmation, legalHoldStatus))

      def apply(text: String,
                mentions: Seq[com.waz.model.Mention],
                links: Option[LinkPreview],
                quote: Option[Quote],
                expectsReadConfirmation: Boolean,
                legalHoldStatus: Messages.LegalHoldStatus): GenericMessage =
        GenericMessage(Uid(), Text(text, mentions, links, quote, expectsReadConfirmation, legalHoldStatus))

      def apply(msg: MessageData): GenericMessage =
        GenericMessage(
          msg.id.uid,
          msg.ephemeral,
          Text(msg.contentString, msg.content.flatMap(_.mentions), Option.empty, msg.protoQuote, msg.expectsRead.getOrElse(false), msg.protoLegalHoldStatus)
        )

      def unapply(msg: GenericMessage): Option[(String, Seq[com.waz.model.Mention], Option[LinkPreview], Option[Quote], Boolean)] = msg.unpackContent match {
        case text: Text     => Some(text.unpack)
        case eph: Ephemeral => eph.unpackContent match {
          case text: Text => Some(text.unpack)
          case _          => None
        }
        case edit: MsgEdit  => edit.unpack.map(_._2.unpack)
        case _              => None
      }

      def updateMentions(msg: GenericMessage, newMentions: Seq[com.waz.model.Mention]): GenericMessage = msg.unpack match {
        case (uid, t: Text) => GenericMessage(uid, Text.newMentions(t, newMentions))
        case _ => msg
      }
    }

    implicit object JsDecoder extends JsonDecoder[GenericMessage] {
      override def apply(implicit js: JSONObject): GenericMessage = GenericMessage(AESUtils.base64(js.getString("proto")))
    }

    implicit object JsEncoder extends JsonEncoder[GenericMessage] {
      override def apply(v: GenericMessage): JSONObject = JsonEncoder { o =>
        o.put("proto", AESUtils.base64(v.proto.toByteArray))
      }
    }

    implicit object MessageDecoder extends ProtoDecoder[GenericMessage] {
      override def apply(data: Array[Byte]): GenericMessage = GenericMessage(data)
      override def apply(in: CodedInputStream): GenericMessage = GenericMessage(Messages.GenericMessage.parseFrom(in))
    }

    implicit object GMessageDecoder extends ProtoDecoder[Messages.GenericMessage] {
      override def apply(data: Array[Byte]): Messages.GenericMessage = Messages.GenericMessage.parseFrom(data)
      override def apply(in: CodedInputStream): Messages.GenericMessage = Messages.GenericMessage.parseFrom(in)
    }
  }
}

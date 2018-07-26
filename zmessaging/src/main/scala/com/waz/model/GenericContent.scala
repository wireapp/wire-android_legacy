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
package com.waz.model


import android.util.Base64
import com.google.protobuf.nano.MessageNano
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetMetaData.Loudness
import com.waz.model.AssetStatus.{DownloadFailed, UploadCancelled, UploadDone, UploadFailed, UploadInProgress, UploadNotStarted}
import com.waz.model.nano.Messages
import com.waz.model.nano.Messages.MessageEdit
import com.waz.utils._
import com.waz.utils.wrappers.URI
import org.json.JSONObject
import org.threeten.bp.{Duration => Dur, Instant}

import scala.collection.breakOut
import scala.concurrent.duration._

trait GenericContent[-T] {
  def set(msg: GenericMessage): T => GenericMessage
}

object GenericContent {

  trait EphemeralContent[-T] {
    def set(eph: Ephemeral): T => Ephemeral
  }

  type Asset = Messages.Asset

  implicit object Asset extends GenericContent[Asset] {

    type Original = Messages.Asset.Original
    object Original {

      def apply(asset: AssetData): Original =
        returning(new Messages.Asset.Original) { o =>
          o.mimeType = asset.mime.str
          o.size = asset.size
          asset.name foreach {o.name = _}
          asset.metaData match {
            case Some(video: AssetMetaData.Video) => o.setVideo(VideoMetaData(video))
            case Some(image: AssetMetaData.Image) => o.setImage(ImageMetaData(image))
            case Some(audio: AssetMetaData.Audio) => o.setAudio(AudioMetaData(audio))
            case _ =>
          }
          //TODO Dean giphy source and caption
        }

      def unapply(proto: Original): Option[(Mime, Long, Option[String], Option[AssetMetaData])] = Option(proto) map { orig =>
        (
          Option(orig.mimeType).filter(_.nonEmpty).map(Mime(_)).getOrElse(Mime.Unknown),
          orig.size,
          Option(orig.name).filter(_.nonEmpty),
          orig.getMetaDataCase match {
            case Messages.Asset.Original.IMAGE_FIELD_NUMBER => ImageMetaData.unapply(orig.getImage)
            case Messages.Asset.Original.VIDEO_FIELD_NUMBER => VideoMetaData.unapply(orig.getVideo)
            case Messages.Asset.Original.AUDIO_FIELD_NUMBER => AudioMetaData.unapply(orig.getAudio)
            case _ => None
          })
      }
    }

    type ImageMetaData = Messages.Asset.ImageMetaData
    object ImageMetaData {
      def apply(md: AssetMetaData.Image): ImageMetaData = returning(new Messages.Asset.ImageMetaData) { p =>
        p.tag = md.tag.toString
        p.width = md.dimensions.width
        p.height = md.dimensions.height
      }

      def unapply(proto: ImageMetaData): Option[AssetMetaData.Image] =
        Some(AssetMetaData.Image(Dim2(proto.width, proto.height), Tag(proto.tag)))
    }

    type VideoMetaData = Messages.Asset.VideoMetaData
    object VideoMetaData {
      def apply(md: AssetMetaData.Video): VideoMetaData = returning(new Messages.Asset.VideoMetaData) { p =>
        p.width = md.dimensions.width
        p.height = md.dimensions.height
        p.durationInMillis = md.duration.toMillis
      }

      def unapply(proto: VideoMetaData): Option[AssetMetaData.Video] =
        Some(AssetMetaData.Video(Dim2(proto.width, proto.height), Dur.ofMillis(proto.durationInMillis)))
    }

    type AudioMetaData = Messages.Asset.AudioMetaData
    object AudioMetaData {
      def apply(md: AssetMetaData.Audio): AudioMetaData = returning(new Messages.Asset.AudioMetaData) { p =>
        p.durationInMillis = md.duration.toMillis
        md.loudness.foreach(l => p.normalizedLoudness = bytify(l.levels))
      }

      def unapply(p: AudioMetaData): Option[AssetMetaData.Audio] =
        Some(AssetMetaData.Audio(Dur.ofMillis(p.durationInMillis), Some(Loudness(floatify(p.normalizedLoudness)))))

      def bytify(ls: Iterable[Float]): Array[Byte] = ls.map(l => (l * 255f).toByte)(breakOut)
      def floatify(bs: Array[Byte]): Vector[Float] = bs.map(b => (b & 255) / 255f)(breakOut)
    }

    type Preview = Messages.Asset.Preview
    object Preview {
      def apply(preview: AssetData): Preview = returning(new Messages.Asset.Preview()) { p =>
        p.mimeType = preview.mime.str
        p.size = preview.size

        //remote
        preview.remoteData.foreach(ak => p.remote = RemoteData.apply(ak))

        //image meta
        preview.metaData.foreach {
          case meta@AssetMetaData.Image(_, _) => p.setImage(ImageMetaData(meta))
          case _ => //other meta data types not supported
        }
      }

      def unapply(preview: Preview): Option[AssetData] = Option(preview) map { prev =>
        val remoteData = RemoteData.unapply(prev.remote)
        AssetData(
          mime        = Mime(prev.mimeType),
          sizeInBytes = prev.size,
          status      = remoteData.map(_ => UploadDone).getOrElse(UploadNotStarted),
          remoteId    = remoteData.flatMap(_.remoteId),
          token       = remoteData.flatMap(_.token),
          otrKey      = remoteData.flatMap(_.otrKey),
          sha         = remoteData.flatMap(_.sha256),
          metaData    = Option(prev.getImage).flatMap(ImageMetaData.unapply)
        )
      }
    }

    type RemoteData = Messages.Asset.RemoteData
    object RemoteData {
      def apply(ak: AssetData.RemoteData): RemoteData =
        returning(new Messages.Asset.RemoteData) { rData =>
          ak.remoteId.foreach(v => rData.assetId = v.str)
          ak.token.foreach(v => rData.assetToken = v.str)
          ak.otrKey.foreach(v => rData.otrKey = v.bytes)
          ak.sha256.foreach(v => rData.sha256 = v.bytes)
          ak.encryption.foreach(v => rData.encryption = v.value)
        }

      def unapply(remoteData: RemoteData): Option[AssetData.RemoteData] = Option(remoteData) map { rData =>
        AssetData.RemoteData(
          Option(rData.assetId).filter(_.nonEmpty).map(RAssetId),
          Option(rData.assetToken).filter(_.nonEmpty).map(AssetToken),
          Some(AESKey(rData.otrKey)).filter(_ != AESKey.Empty),
          Some(Sha256(rData.sha256)).filter(_ != Sha256.Empty),
          Some(EncryptionAlgorithm(rData.encryption)))
      }
    }

    override def set(msg: GenericMessage) = msg.setAsset

    def apply(asset: AssetData, preview: Option[AssetData] = None): Asset = returning(new Messages.Asset) { proto =>
      proto.original = Original(asset)
      preview.foreach(p => proto.preview = Preview(p))
      (asset.status, asset.remoteData) match {
        case (UploadCancelled, _) => proto.setNotUploaded(Messages.Asset.CANCELLED)
        case (UploadFailed, _) => proto.setNotUploaded(Messages.Asset.FAILED)
        case (UploadDone, Some(data)) => proto.setUploaded(RemoteData(data))
        case (DownloadFailed, Some(data)) => proto.setUploaded(RemoteData(data))
        case _ =>
      }
    }

    def unapply(a: Asset): Option[(AssetData, Option[AssetData])] = {
      //TODO Dean - think of better way to handle when only one part of asset proto appears without original
      val (mime, size, name, meta) = Original.unapply(a.original).getOrElse(Mime.Unknown, 0L, None, None)
      val preview = Preview.unapply(a.preview)
      val remoteData = RemoteData.unapply(a.getUploaded)
      val status = a.getStatusCase match {
        case Messages.Asset.UPLOADED_FIELD_NUMBER => remoteData.map(_ => UploadDone).getOrElse(UploadFailed)
        case Messages.Asset.NOT_UPLOADED_FIELD_NUMBER =>
          a.getNotUploaded match {
            case Messages.Asset.CANCELLED => UploadCancelled
            case Messages.Asset.FAILED => UploadFailed
            case _ => UploadInProgress
          }
        case _ => UploadInProgress
      }

      val asset = AssetData(
        mime = mime,
        sizeInBytes = size,
        name = name,
        metaData = meta,
        status = status,
        remoteId    = remoteData.flatMap(_.remoteId),
        token       = remoteData.flatMap(_.token),
        otrKey      = remoteData.flatMap(_.otrKey),
        sha         = remoteData.flatMap(_.sha256),
        previewId   = preview.map(_.id)
      )
      Some((asset, preview))
    }

  }

  implicit object EphemeralAsset extends EphemeralContent[Asset] {
    override def set(eph: Ephemeral): (Asset) => Ephemeral = eph.setAsset
  }

  type ImageAsset = Messages.ImageAsset
  implicit object ImageAsset extends GenericContent[ImageAsset] {
    override def set(msg: GenericMessage) = msg.setImage

    def unapply(proto: ImageAsset): Option[AssetData] = {
      Some(AssetData(
        status = UploadDone,
        otrKey = Option(proto.otrKey).map(AESKey(_)),
        sha = Option(proto.sha256).map(Sha256(_)),
        sizeInBytes = proto.size,
        mime = Mime(proto.mimeType),
        metaData = Some(AssetMetaData.Image(Dim2(proto.width, proto.height), Tag(proto.tag)))
      ))
    }

    def apply(asset: AssetData): ImageAsset = returning(new Messages.ImageAsset) { proto =>
      asset.metaData.foreach {
        case AssetMetaData.Image(Dim2(w, h), tag) =>
          proto.tag = tag.toString
          proto.width = w
          proto.height = h
          proto.originalWidth = w
          proto.originalHeight = h
        case _ => throw new Exception("Trying to create image proto from non image asset data")
      }
      proto.mimeType = asset.mime.str
      proto.size = asset.size.toInt
      asset.otrKey.foreach(v => proto.otrKey = v.bytes)
      asset.sha.foreach(v => proto.sha256 = v.bytes)
    }
  }

  implicit object EphemeralImageAsset extends EphemeralContent[ImageAsset] {
    override def set(eph: Ephemeral): (ImageAsset) => Ephemeral = eph.setImage
  }

  type Mention = Messages.Mention

  object Mention {
    def apply(userId: Option[UserId], start: Int, length: Int) = returning(new Messages.Mention) { m =>
      userId.map(id => m.setUserId(id.str))
      m.start = start
      m.length = length
    }
  }

  type LinkPreview = Messages.LinkPreview

  object LinkPreview {

    trait PreviewMeta[A] {
      def apply(preview: LinkPreview, meta: A): LinkPreview
    }

    implicit object TweetMeta extends PreviewMeta[Tweet] {
      override def apply(preview: LinkPreview, meta: Tweet): LinkPreview = returning(preview) {_.setTweet(meta)}
    }

    def apply(uri: URI, offset: Int): LinkPreview = returning(new Messages.LinkPreview) { p =>
      p.url = uri.toString
      p.urlOffset = offset
    }

    def apply(uri: URI, offset: Int, title: String, summary: String, image: Option[Asset], permanentUrl: Option[URI]): LinkPreview =
      returning(new Messages.LinkPreview) { p =>
        p.url = uri.toString
        p.urlOffset = offset
        p.title = title
        p.summary = summary
        permanentUrl foreach { u => p.permanentUrl = u.toString }
        image foreach {p.image = _}

        // set article for backward compatibility, we will stop sending it once all platforms switch to using LinkPreview properties
        p.setArticle(article(title, summary, image, permanentUrl))
      }

    def apply[Meta: PreviewMeta](uri: URI, offset: Int, title: String, summary: String, image: Option[Asset], permanentUrl: Option[URI], meta: Meta): LinkPreview =
      returning(apply(uri, offset, title, summary, image, permanentUrl)) { p =>
        implicitly[PreviewMeta[Meta]].apply(p, meta)
      }

    type Tweet = Messages.Tweet

    object Tweet {

    }

    private def article(title: String, summary: String, image: Option[Asset], uri: Option[URI]) = returning(new Messages.Article) { p =>
      p.title = title
      p.summary = summary
      uri foreach { u => p.permanentUrl = u.toString }
      image foreach {p.image = _}
    }

    implicit object JsDecoder extends JsonDecoder[LinkPreview] {
      override def apply(implicit js: JSONObject): LinkPreview = Messages.LinkPreview.parseFrom(Base64.decode(js.getString("proto"), Base64.DEFAULT))
    }

    implicit object JsEncoder extends JsonEncoder[LinkPreview] {
      override def apply(v: LinkPreview): JSONObject = JsonEncoder { o =>
        o.put("proto", Base64.encodeToString(MessageNano.toByteArray(v), Base64.NO_WRAP))
      }
    }

    object WithAsset {
      def unapply(lp: LinkPreview): Option[AssetData] =
        (Option(lp.image) orElse {if (lp.hasArticle) Option(lp.getArticle.image) else None}).flatMap { a => Asset.unapply(a).map { case (asset, _) => asset}}
    }

    object WithDescription {
      def unapply(lp: LinkPreview): Option[(String, String)] =
        if (lp.hasArticle) Some((lp.getArticle.title, lp.getArticle.summary))
        else Some((lp.title, lp.summary))
    }

  }

  type Reaction = Messages.Reaction

  implicit object Reaction extends GenericContent[Reaction] {

    override def set(msg: GenericMessage) = msg.setReaction

    val HeavyBlackHeart = "\u2764\uFE0F"

    def apply(msg: MessageId, action: Liking.Action): Reaction = returning(new Messages.Reaction) { proto =>
      proto.emoji = action match {
        case Liking.Action.Like => HeavyBlackHeart
        case Liking.Action.Unlike => ""
      }
      proto.messageId = msg.str
    }

    def unapply(proto: Messages.Reaction): Option[(MessageId, Liking.Action)] = Some((MessageId(proto.messageId), proto.emoji match {
      case HeavyBlackHeart => Liking.Action.Like
      case _ => Liking.Action.Unlike
    }))
  }

  type Knock = Messages.Knock

  implicit object Knock extends GenericContent[Knock] {
    override def set(msg: GenericMessage) = msg.setKnock
    def apply() = new Messages.Knock()
    def unapply(arg: Knock): Boolean = true
  }

  implicit object EphemeralKnock extends EphemeralContent[Knock] {
    override def set(eph: Ephemeral): (Knock) => Ephemeral = eph.setKnock
  }

  type Text = Messages.Text

  implicit object Text extends GenericContent[Text] {
    override def set(msg: GenericMessage) = msg.setText

    def apply(content: String): Text = apply(content, Nil, Nil)

    def apply(content: String, links: Seq[LinkPreview]): Text = apply(content, Nil, links)

    def apply(content: String, mentions: Seq[com.waz.model.Mention], links: Seq[LinkPreview]): Text = returning(new Messages.Text()) { t =>
      t.content = content
      t.mentions = mentions.map { case com.waz.model.Mention(userId, start, length) => GenericContent.Mention(userId, start, length) }(breakOut).toArray
      t.linkPreview = links.toArray
    }

    def unapply(proto: Text): Option[(String, Seq[com.waz.model.Mention], Seq[LinkPreview])] = {
      val mentions = proto.mentions.map { m =>
        val userId = m.getUserId match {
          case id: String if id.nonEmpty => Option(UserId(id))
          case _ => None
        }
        com.waz.model.Mention(userId, m.start, m.length)
      }.toSeq
      Option((proto.content, mentions, proto.linkPreview.toSeq))
    }
  }

  implicit object EphemeralText extends EphemeralContent[Text] {
    override def set(eph: Ephemeral): (Text) => Ephemeral = eph.setText
  }

  type MsgEdit = Messages.MessageEdit

  implicit object MsgEdit extends GenericContent[MsgEdit] {
    override def set(msg: GenericMessage) = msg.setEdited

    def apply(ref: MessageId, content: Text) = returning(new MessageEdit) { c =>
      c.replacingMessageId = ref.str
      c.setText(content)
    }

    def unapply(arg: MsgEdit): Option[(MessageId, Text)] =
      arg.getContentCase match {
        case Messages.MessageEdit.TEXT_FIELD_NUMBER =>
          Some((MessageId(arg.replacingMessageId), arg.getText))
        case _ =>
          None
      }
  }

  type Cleared = Messages.Cleared

  implicit object Cleared extends GenericContent[Cleared] {
    override def set(msg: GenericMessage) = msg.setCleared

    def apply(conv: RConvId, time: RemoteInstant) = returning(new Messages.Cleared) { c =>
      c.conversationId = conv.str
      c.clearedTimestamp = time.toEpochMilli
    }

    def unapply(arg: Cleared): Option[(RConvId, RemoteInstant)] =
      for {
        conv <- Option(arg.conversationId)
        time <- Option(arg.clearedTimestamp)
      } yield (RConvId(conv), RemoteInstant.ofEpochMilli(time))
  }

  type LastRead = Messages.LastRead

  implicit object LastRead extends GenericContent[LastRead] {
    override def set(msg: GenericMessage) = msg.setLastRead

    def apply(conv: RConvId, time: RemoteInstant) = returning(new Messages.LastRead) { l =>
      l.conversationId = conv.str
      l.lastReadTimestamp = time.toEpochMilli
    }

    def unapply(arg: LastRead): Option[(RConvId, RemoteInstant)] =
      Some((RConvId(arg.conversationId), RemoteInstant.ofEpochMilli(arg.lastReadTimestamp)))
  }

  type MsgDeleted = Messages.MessageHide

  implicit object MsgDeleted extends GenericContent[MsgDeleted] {
    override def set(msg: GenericMessage) = msg.setHidden

    def apply(conv: RConvId, msg: MessageId) = returning(new Messages.MessageHide) { d =>
      d.conversationId = conv.str
      d.messageId = msg.str
    }

    def unapply(proto: MsgDeleted): Option[(RConvId, MessageId)] =
      Some((RConvId(proto.conversationId), MessageId(proto.messageId)))
  }

  type MsgRecall = Messages.MessageDelete

  implicit object MsgRecall extends GenericContent[MsgRecall] {
    override def set(msg: GenericMessage) = msg.setDeleted

    def apply(msg: MessageId) = returning(new Messages.MessageDelete) { d =>
      d.messageId = msg.str
    }

    def unapply(proto: MsgRecall): Option[MessageId] = Some(MessageId(proto.messageId))
  }

  type Location = Messages.Location

  implicit object Location extends GenericContent[Location] {
    override def set(msg: GenericMessage): (Location) => GenericMessage = msg.setLocation

    def apply(lon: Float, lat: Float, name: String, zoom: Int) = returning(new Messages.Location) { p =>
      p.longitude = lon
      p.latitude = lat
      p.name = name
      p.zoom = zoom
    }

    def unapply(l: Location): Option[(Float, Float, Option[String], Option[Int])] =
      Some((l.longitude, l.latitude, Option(l.name).filter(_.nonEmpty), Option(l.zoom).filter(_ != 0)))
  }

  implicit object EphemeralLocation extends EphemeralContent[Location] {
    override def set(eph: Ephemeral): (Location) => Ephemeral = eph.setLocation
  }

  type Receipt = Messages.Confirmation

  implicit object Receipt extends GenericContent[Receipt] {
    override def set(msg: GenericMessage) = msg.setConfirmation

    def apply(msg: MessageId) = returning(new Messages.Confirmation) { c =>
      c.firstMessageId = msg.str
      c.`type` = Messages.Confirmation.DELIVERED
    }

    def unapply(proto: Receipt): Option[MessageId] = if (proto.`type` == Messages.Confirmation.DELIVERED) Some(MessageId(proto.firstMessageId)) else None
  }

  type External = Messages.External

  implicit object External extends GenericContent[External] {
    override def set(msg: GenericMessage) = msg.setExternal

    def apply(key: AESKey, sha: Sha256) = returning(new Messages.External) { e =>
      e.otrKey = key.bytes
      e.sha256 = sha.bytes
    }

    def unapply(e: External): Option[(AESKey, Sha256)] =
      for {
        key <- Option(e.otrKey)
        sha <- Option(e.sha256)
      } yield (AESKey(key), Sha256(sha))
  }

  type Ephemeral = Messages.Ephemeral

  implicit object Ephemeral extends GenericContent[Ephemeral] {

    import scala.concurrent.duration.DurationInt

    override def set(msg: GenericMessage): (Ephemeral) => GenericMessage = msg.setEphemeral

    def apply[Content: EphemeralContent](expiry: Option[FiniteDuration], content: Content) = returning(new Messages.Ephemeral) { proto =>
      proto.expireAfterMillis = expiry.getOrElse(Duration.Zero).toMillis
      implicitly[EphemeralContent[Content]].set(proto)(content)
    }

    def unapply(proto: Ephemeral): Option[(Option[FiniteDuration], Any)] = proto.expireAfterMillis match {
      case 0 => Some((None, content(proto)))
      case _ => Some(Some(EphemeralDuration(proto.expireAfterMillis)), content(proto))
    }

    def content(e: Ephemeral) = e.getContentCase match {
      case Messages.Ephemeral.TEXT_FIELD_NUMBER => e.getText
      case Messages.Ephemeral.ASSET_FIELD_NUMBER => e.getAsset
      case Messages.Ephemeral.IMAGE_FIELD_NUMBER => e.getImage
      case Messages.Ephemeral.KNOCK_FIELD_NUMBER => e.getKnock
      case Messages.Ephemeral.LOCATION_FIELD_NUMBER => e.getLocation
      case _ => Unknown
    }
  }

  type AvailabilityStatus = Messages.Availability

  implicit object AvailabilityStatus extends GenericContent[AvailabilityStatus] {
    import Messages.Availability._

    override def set(msg: GenericMessage): (AvailabilityStatus) => GenericMessage = msg.setAvailability

    def apply(activity: Availability): AvailabilityStatus = returning(new Messages.Availability) {
      _.`type` = activity match {
        case Availability.None      => NONE
        case Availability.Available => AVAILABLE
        case Availability.Away      => AWAY
        case Availability.Busy      => BUSY
      }
    }

    def unapply(availability: AvailabilityStatus): Option[Availability] = availability.`type` match {
      case NONE      => Some(Availability.None)
      case AVAILABLE => Some(Availability.Available)
      case AWAY      => Some(Availability.Away)
      case BUSY      => Some(Availability.Busy)
      case _         => None
    }
  }

  case object Unknown

  implicit object UnknownContent extends GenericContent[Unknown.type] {
    override def set(msg: GenericMessage) = { _ => msg }
  }

  sealed trait ClientAction {
    val value: Int
  }

  implicit object ClientAction extends GenericContent[ClientAction] {

    case object SessionReset extends ClientAction {
      override val value: Int = Messages.RESET_SESSION
    }

    case class UnknownAction(value: Int) extends ClientAction

    def apply(v: Int) = v match {
      case Messages.RESET_SESSION => SessionReset
      case other => UnknownAction(other)
    }

    override def set(msg: GenericMessage) = { action => msg.setClientAction(action.value) }
  }

  type Calling = Messages.Calling

  implicit object Calling extends GenericContent[Calling] {
    override def set(msg: GenericMessage): (Calling) => GenericMessage = msg.setCalling

    def apply(content: String): Calling = returning(new Calling) { c =>
      c.content = content
    }

    def unapply(calling: Calling): Option[String] = Option(calling.content)
  }

  sealed trait EncryptionAlgorithm {
    val value: Int
  }

  implicit object EncryptionAlgorithm {

    case object AES_CBC extends EncryptionAlgorithm {
      override val value: Int = Messages.AES_CBC
    }

    case object AES_GCM extends EncryptionAlgorithm {
      override val value: Int = Messages.AES_GCM
    }

    def apply(v: Int) = v match {
      case Messages.AES_GCM => AES_GCM
      case other => AES_CBC
    }

    def unapply(encryption: EncryptionAlgorithm): Option[Int] = encryption match {
      case AES_GCM => Some(Messages.AES_GCM)
      case _ => Some(Messages.AES_CBC)
    }
  }

  sealed trait EncryptionAlgorithm2
  object EncryptionAlgorithm2 {
    case object AES_CBC extends EncryptionAlgorithm2
    case object AES_GCM extends EncryptionAlgorithm2
  }

}

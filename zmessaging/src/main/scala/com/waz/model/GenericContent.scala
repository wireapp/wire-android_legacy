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

import com.google.protobuf.ByteString
import com.waz.log.BasicLogging.LogTag
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetMetaData.Loudness
import com.waz.model.AssetStatus.{DownloadFailed, UploadCancelled, UploadDone, UploadFailed, UploadInProgress, UploadNotStarted}
import com.waz.service.assets
import com.waz.service.assets.{AES_CBC_Encryption, AudioDetails, ImageDetails, UploadAsset, UploadAssetStatus, VideoDetails}
import com.waz.utils._
import com.waz.utils.crypto.AESUtils
import com.waz.utils.wrappers.URI
import org.json.JSONObject
import org.threeten.bp.{Duration => Dur}

import scala.collection.breakOut
import scala.concurrent.duration._
import com.waz.log.LogSE._

import scala.collection.JavaConverters._

sealed trait GenericContent[Proto] {
  val proto: Proto
  def set(builder: Messages.GenericMessage.Builder): Unit = {}
}

sealed trait EphemeralContent extends GenericContent[Messages.Ephemeral] {
  def set(builder: Messages.Ephemeral.Builder): Unit
}

object GenericContent {
  final case class Asset(override val proto: Messages.Asset) extends GenericContent[Messages.Asset] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setAsset(proto)

    lazy val unpack: (AssetData, Option[AssetData]) = {
      val (mime, size, name, meta) = Asset.Original(proto.getOriginal).unpack.getOrElse(Mime.Unknown, 0L, None, None)
      val preview = Asset.Preview(proto.getPreview).unpack
      val remoteData = Asset.RemoteData(proto.getUploaded).unpack
      val status = proto.getStatusCase.getNumber match {
        case Messages.Asset.UPLOADED_FIELD_NUMBER => remoteData.map(_ => UploadDone).getOrElse(UploadFailed)
        case Messages.Asset.NOT_UPLOADED_FIELD_NUMBER =>
          proto.getNotUploaded match {
            case Messages.Asset.NotUploaded.CANCELLED => UploadCancelled
            case Messages.Asset.NotUploaded.FAILED    => UploadFailed
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
      (asset, preview)
    }
  }

  object Asset {

    def apply(asset: AssetData, preview: Option[AssetData] = None, expectsReadConfirmation: Boolean): Asset = {
      val builder = Messages.Asset.newBuilder()
      builder.setOriginal(Original(asset).proto)
      preview.foreach(p => builder.setPreview(Preview(p).proto))
      (asset.status, asset.remoteData) match {
        case (UploadCancelled, _)         => builder.setNotUploaded(Messages.Asset.NotUploaded.CANCELLED)
        case (UploadFailed, _)            => builder.setNotUploaded(Messages.Asset.NotUploaded.FAILED)
        case (UploadDone, Some(data))     => builder.setUploaded(RemoteData(data).proto)
        case (DownloadFailed, Some(data)) => builder.setUploaded(RemoteData(data).proto)
        case _ =>
      }
      builder.setExpectsReadConfirmation(expectsReadConfirmation)
      Asset(builder.build())
    }

    def apply(asset: UploadAsset, preview: Option[assets.Asset], expectsReadConfirmation: Boolean): Asset = {
      val builder = Messages.Asset.newBuilder()
      builder.setOriginal(Original(asset).proto)
      preview.foreach(p => builder.setPreview(Preview(p).proto))
      asset.status match {
        case UploadAssetStatus.Cancelled => builder.setNotUploaded(Messages.Asset.NotUploaded.CANCELLED)
        case UploadAssetStatus.Failed    => builder.setNotUploaded(Messages.Asset.NotUploaded.FAILED)
        case _ =>
      }
      builder.setExpectsReadConfirmation(expectsReadConfirmation)
      Asset(builder.build())
    }

    def apply(asset: assets.Asset, preview: Option[assets.Asset], expectsReadConfirmation: Boolean): Asset = {
      val builder = Messages.Asset.newBuilder()
      builder.setOriginal(Original(asset).proto)
      preview.foreach(p => builder.setPreview(Preview(p).proto))
      builder.setUploaded(RemoteData(asset).proto)
      builder.setExpectsReadConfirmation(expectsReadConfirmation)
      Asset(builder.build())
    }

    final case class Original(override val proto: Messages.Asset.Original) extends GenericContent[Messages.Asset.Original]{
      override def set(builder: Messages.GenericMessage.Builder): Unit = {
        val ab = Messages.Asset.newBuilder()
        ab.setOriginal(proto)
        builder.setAsset(ab)
      }

      lazy val unpack: Option[(Mime, Long, Option[String], Option[AssetMetaData])] = Option(proto).map { orig =>
        (
          if (orig.hasMimeType) Mime(orig.getMimeType) else Mime.Unknown,
          orig.getSize,
          if (orig.hasName) Option(orig.getName) else None,
          orig.getMetaDataCase.getNumber match {
            case Messages.Asset.Original.IMAGE_FIELD_NUMBER => Some(ImageMetaData(orig.getImage).unpack)
            case Messages.Asset.Original.VIDEO_FIELD_NUMBER => Some(VideoMetaData(orig.getVideo).unpack)
            case Messages.Asset.Original.AUDIO_FIELD_NUMBER => Some(AudioMetaData(orig.getAudio).unpack)
            case _ => None
          })
      }
    }

    object Original {
      def apply(asset: AssetData): Original = {
        val builder = Messages.Asset.Original.newBuilder()
        builder.setMimeType(asset.mime.str)
        builder.setSize(asset.size)
        asset.name.foreach(builder.setName)
        asset.metaData match {
          case Some(video: AssetMetaData.Video) => builder.setVideo(VideoMetaData(video).proto)
          case Some(image: AssetMetaData.Image) => builder.setImage(ImageMetaData(image).proto)
          case Some(audio: AssetMetaData.Audio) => builder.setAudio(AudioMetaData(audio).proto)
          case _ =>
        }
        Original(builder.build())
      }

      def apply(asset: UploadAsset): Original = {
        val builder = Messages.Asset.Original.newBuilder()
        builder.setMimeType(asset.mime.str)
        builder.setSize(asset.size)
        builder.setName(asset.name)
        asset.details match {
          case image: ImageDetails => builder.setImage(ImageMetaData(image).proto)
          case video: VideoDetails => builder.setVideo(VideoMetaData(video).proto)
          case audio: AudioDetails => builder.setAudio(AudioMetaData(audio).proto)
          case _ =>
        }
        Original(builder.build())
      }

      def apply(asset: assets.Asset): Original = {
        val builder = Messages.Asset.Original.newBuilder()
        builder.setMimeType(asset.mime.str)
        builder.setSize(asset.size)
        builder.setName(asset.name)
        asset.details match {
          case image: ImageDetails => builder.setImage(ImageMetaData(image).proto)
          case video: VideoDetails => builder.setVideo(VideoMetaData(video).proto)
          case audio: AudioDetails => builder.setAudio(AudioMetaData(audio).proto)
          case _ =>
        }
        Original(builder.build())
      }
    }

    final case class ImageMetaData(override val proto: Messages.Asset.ImageMetaData) extends GenericContent[Messages.Asset.ImageMetaData] {
      lazy val unpack: AssetMetaData.Image =
        AssetMetaData.Image(Dim2(proto.getWidth, proto.getHeight), Tag(proto.getTag))
    }

    object ImageMetaData {
      def apply(image: AssetMetaData.Image): ImageMetaData = {
        val builder = Messages.Asset.ImageMetaData.newBuilder()
        builder.setTag(image.tag.toString)
        builder.setWidth(image.dimensions.width)
        builder.setHeight(image.dimensions.height)
        ImageMetaData(builder.build())
      }

      def apply(details: ImageDetails): ImageMetaData = {
        val builder = Messages.Asset.ImageMetaData.newBuilder()
        builder.setWidth(details.dimensions.width)
        builder.setHeight(details.dimensions.height)
        ImageMetaData(builder.build())
      }
    }

    final case class VideoMetaData(override val proto: Messages.Asset.VideoMetaData) extends GenericContent[Messages.Asset.VideoMetaData] {
      lazy val unpack: AssetMetaData.Video = {
        val dim = if (!proto.hasWidth || !proto.hasHeight) Dim2(0, 0) else Dim2(proto.getWidth, proto.getHeight)
        val dur = if (!proto.hasDurationInMillis) Dur.ZERO else Dur.ofMillis(proto.getDurationInMillis)
        AssetMetaData.Video(dim, dur)
      }
    }

    object VideoMetaData {
      def apply(video: AssetMetaData.Video): VideoMetaData = {
        val builder = Messages.Asset.VideoMetaData.newBuilder()
        builder.setWidth(video.dimensions.width)
        builder.setHeight(video.dimensions.height)
        builder.setDurationInMillis(video.duration.toMillis)
        VideoMetaData(builder.build())
      }

      def apply(details: VideoDetails): VideoMetaData = {
        val builder = Messages.Asset.VideoMetaData.newBuilder()
        builder.setWidth(details.dimensions.width)
        builder.setHeight(details.dimensions.height)
        builder.setDurationInMillis(details.duration.toMillis)
        VideoMetaData(builder.build())
      }
    }

    final case class AudioMetaData(override val proto: Messages.Asset.AudioMetaData) extends GenericContent[Messages.Asset.AudioMetaData] {
      lazy val unpack: AssetMetaData.Audio = {
        val dur = if (!proto.hasDurationInMillis) Dur.ZERO else Dur.ofMillis(proto.getDurationInMillis)
        val loudness = if(!proto.hasNormalizedLoudness) None else Some(Loudness(AudioMetaData.floatify(proto.getNormalizedLoudness.toByteArray)))
        AssetMetaData.Audio(dur, loudness)
      }
    }

    object AudioMetaData {
      def apply(audio: AssetMetaData.Audio): AudioMetaData = {
        val builder = Messages.Asset.AudioMetaData.newBuilder()
        builder.setDurationInMillis(audio.duration.toMillis)
        audio.loudness.foreach(l => builder.setNormalizedLoudness(bytify(l.levels)))
        AudioMetaData(builder.build())
      }

      def apply(details: AudioDetails): AudioMetaData = {
        val builder = Messages.Asset.AudioMetaData.newBuilder()
        builder.setDurationInMillis(details.duration.toMillis)
        builder.setNormalizedLoudness(bytify(details.loudness.levels.map(_.toFloat)))
        AudioMetaData(builder.build())
      }

      private def bytify(ls: Iterable[Float]): ByteString = ByteString.copyFrom(ls.map(l => (l * 255f).toByte)(breakOut).toArray)

      private def floatify(bs: Array[Byte]): Vector[Float] = bs.map(b => (b & 255) / 255f)(breakOut)
    }

    final case class Preview(override val proto: Messages.Asset.Preview) extends GenericContent[Messages.Asset.Preview] {
      override def set(builder: Messages.GenericMessage.Builder): Unit = {
        val pb = Messages.Asset.newBuilder()
        pb.setPreview(proto)
        builder.setAsset(pb.build())
      }

      lazy val unpack: Option[AssetData] = Option(proto).map { prev =>
        val remoteData = if (prev.hasRemote) RemoteData(prev.getRemote).unpack else None
        AssetData(
          mime = Mime(prev.getMimeType),
          sizeInBytes = prev.getSize,
          status = remoteData.map(_ => UploadDone).getOrElse(UploadNotStarted),
          remoteId = remoteData.flatMap(_.remoteId),
          token = remoteData.flatMap(_.token),
          otrKey = remoteData.flatMap(_.otrKey),
          sha = remoteData.flatMap(_.sha256),
          metaData = if (prev.hasImage) Option(prev.getImage).map(image => ImageMetaData(image).unpack) else None
        )
      }
    }

    object Preview {
      def apply(preview: AssetData): Preview = {
        val builder = Messages.Asset.Preview.newBuilder()
        builder.setMimeType(preview.mime.str)
        builder.setSize(preview.size)

        // remote
        preview.remoteData.foreach(data => builder.setRemote(RemoteData(data).proto))

        //image meta
        preview.metaData.foreach {
          case meta@AssetMetaData.Image(_, _) => builder.setImage(ImageMetaData(meta).proto)
          case _ => //other meta data types not supported
        }

        Preview(builder.build())
      }

      def apply(asset: assets.Asset): Preview = {
        val builder = Messages.Asset.Preview.newBuilder()
        builder.setMimeType(asset.mime.str)
        builder.setSize(asset.size)
        builder.setRemote(RemoteData(asset).proto)

        //image meta
        asset.details match {
          case image: ImageDetails => builder.setImage(ImageMetaData(image).proto)
          case _ =>
        }

        Preview(builder.build())
      }
    }

    sealed trait EncryptionAlgorithm {
      val value: Int
    }

    case object AES_CBC extends EncryptionAlgorithm {
      override val value: Int = Messages.EncryptionAlgorithm.AES_CBC.getNumber
    }

    case object AES_GCM extends EncryptionAlgorithm {
      override val value: Int = Messages.EncryptionAlgorithm.AES_GCM.getNumber
    }

    object EncryptionAlgorithm {
      def apply(v: Int): EncryptionAlgorithm = if (v == AES_GCM.value) AES_GCM else AES_CBC

      def unapply(encryption: EncryptionAlgorithm): Option[Int] = encryption match {
        case AES_GCM => Some(Messages.EncryptionAlgorithm.AES_GCM.getNumber)
        case _       => Some(Messages.EncryptionAlgorithm.AES_CBC.getNumber)
      }
    }

    final case class RemoteData(override val proto: Messages.Asset.RemoteData) extends GenericContent[Messages.Asset.RemoteData] {
      override def set(builder: Messages.GenericMessage.Builder): Unit = {
        val rdb = Messages.Asset.Preview.newBuilder()
        rdb.setRemote(proto)
        Preview(rdb.build()).set(builder)
      }

      lazy val unpack: Option[AssetData.RemoteData] = Option(proto).map { rData =>
        AssetData.RemoteData(
          if (rData.hasAssetId) Some(RAssetId(rData.getAssetId)) else None,
          if (rData.hasAssetToken) Some(AssetToken(rData.getAssetToken)) else None,
          Some(AESKey(rData.getOtrKey.toByteArray)).filter(_ != AESKey.Empty),
          Some(Sha256(rData.getSha256.toByteArray)).filter(_ != Sha256.Empty),
          if (rData.hasEncryption) Some(EncryptionAlgorithm(rData.getEncryption.getNumber)) else None
        )
      }
    }

    object RemoteData {
      def apply(ak: AssetData.RemoteData): RemoteData = {
        val builder = Messages.Asset.RemoteData.newBuilder()
        ak.remoteId.foreach(id => builder.setAssetId(id.str))
        ak.token.foreach(t => builder.setAssetToken(t.str))
        ak.otrKey.foreach(key => builder.setOtrKey(ByteString.copyFrom(key.bytes)))
        ak.sha256.foreach(sha => builder.setSha256(ByteString.copyFrom(sha.bytes)))
        ak.encryption.foreach(enc => builder.setEncryption(toEncryptionAlg(enc)))
        RemoteData(builder.build())
      }

      private def toEncryptionAlg(enc: EncryptionAlgorithm): Messages.EncryptionAlgorithm = enc match {
        case AES_CBC => Messages.EncryptionAlgorithm.AES_CBC
        case AES_GCM => Messages.EncryptionAlgorithm.AES_GCM
      }

      def apply(asset: assets.Asset): RemoteData = {
        val builder = Messages.Asset.RemoteData.newBuilder()
        builder.setAssetId(asset.id.str)
        asset.token.foreach(token => builder.setAssetToken(token.str))
        builder.setSha256(ByteString.copyFrom(asset.sha.bytes))
        asset.encryption match {
          case AES_CBC_Encryption(key) =>
            builder.setEncryption(Messages.EncryptionAlgorithm.AES_CBC)
            builder.setOtrKey(ByteString.copyFrom(key.bytes))
          case _ =>
        }
        RemoteData(builder.build())
      }
    }
  }

  final case class ImageAsset(override val proto: Messages.ImageAsset) extends GenericContent[Messages.ImageAsset] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setImage(proto)

    lazy val unpack: AssetData =
      AssetData(
        status = UploadDone,
        otrKey = if (proto.hasOtrKey) Option(AESKey(proto.getOtrKey.toByteArray)) else None,
        sha = if (proto.hasSha256) Option(Sha256(proto.getSha256.toByteArray)) else None,
        sizeInBytes = proto.getSize,
        mime = Mime(proto.getMimeType),
        metaData = Some(AssetMetaData.Image(Dim2(proto.getWidth, proto.getHeight), Tag(proto.getTag)))
      )
  }

  object ImageAsset {
    def apply(asset: AssetData): ImageAsset = {
      val builder = Messages.ImageAsset.newBuilder()
        asset.metaData.foreach {
          case AssetMetaData.Image(Dim2(w, h), tag) =>
            builder.setTag(tag.toString)
            builder.setWidth(w)
            builder.setHeight(h)
            builder.setOriginalWidth(w)
            builder.setOriginalHeight(h)
          case _ => error(l"Trying to create image proto from non image asset data: $asset")(LogTag("ImageAsset"))
        }
      builder.setMimeType(asset.mime.str)
      builder.setSize(asset.size.toInt)
      asset.otrKey.foreach(v => builder.setOtrKey(ByteString.copyFrom(v.bytes)))
      asset.sha.foreach(v => builder.setSha256(ByteString.copyFrom(v.bytes)))
      ImageAsset(builder.build())
    }
  }

  final case class Mention(override val proto: Messages.Mention) extends GenericContent[Messages.Mention] {
    lazy val unpack: com.waz.model.Mention =
      com.waz.model.Mention(
        if (proto.hasUserId) Some(UserId(proto.getUserId)) else None,
        proto.getStart,
        proto.getLength
      )
  }

  object Mention {
    def apply(userId: Option[UserId], start: Int, length: Int): Mention = {
      val builder = Messages.Mention.newBuilder()
      userId.map(id => builder.setUserId(id.str))
      builder.setStart(start)
      builder.setLength(length)
      Mention(builder.build())
    }

    def apply(mention: com.waz.model.Mention): Mention =
      apply(mention.userId, mention.start, mention.length)
  }

  final case class Quote(override val proto: Messages.Quote) extends GenericContent[Messages.Quote] {
    lazy val unpack: (MessageId, Option[Sha256]) = {
      val sha =
        if (proto.hasQuotedMessageSha256)
          Option(proto.getQuotedMessageSha256).map(_.toByteArray).collect { case bytes if bytes.nonEmpty => Sha256.calculate(bytes) }
        else None
     (MessageId(proto.getQuotedMessageId), sha)
    }
  }

  object Quote {
    def apply(id: MessageId, sha256: Option[Sha256]): Quote = {
      val builder = Messages.Quote.newBuilder()
      builder.setQuotedMessageId(id.str)
      sha256.foreach(sha => if (sha.bytes.nonEmpty) builder.setQuotedMessageSha256(ByteString.copyFrom(sha.bytes)))
      Quote(builder.build())
    }
  }

  final case class LinkPreview(override val proto: Messages.LinkPreview) extends GenericContent[Messages.LinkPreview] {
    lazy val unpack: (String, String, Option[AssetData]) = {
      val (title, summary) =
        if (proto.hasArticle) (proto.getArticle.getTitle, proto.getArticle.getSummary)
        else (proto.getTitle, proto.getSummary)
      val image =
        if (proto.hasImage) Some(proto.getImage)
        else if (proto.hasArticle && proto.getArticle.hasImage) Some(proto.getArticle.getImage)
        else None
      (title, summary, image.map { proto => Asset(proto).unpack._1 })
    }
  }

  object LinkPreview {
    def apply(linkPreview: LinkPreview, meta: Messages.Tweet): LinkPreview = {
      val builder = linkPreview.proto.toBuilder
      builder.setTweet(meta)
      LinkPreview(builder.build())
    }

    def apply(uri: URI, offset: Int): LinkPreview = {
      val builder = Messages.LinkPreview.newBuilder()
      builder.setUrl(uri.toString)
      builder.setUrlOffset(offset)
      LinkPreview(builder.build())
    }

    def apply(uri: URI, offset: Int, title: String, summary: String, image: Option[Asset], permanentUrl: Option[URI]): LinkPreview = {
      val builder = Messages.LinkPreview.newBuilder()
      builder.setUrl(uri.toString)
      builder.setUrlOffset(offset)
      builder.setTitle(title)
      builder.setSummary(summary)
      permanentUrl.foreach { u => builder.setPermanentUrl(u.toString) }
      image.foreach(im => builder.setImage(im.proto))

      // set article for backward compatibility, we will stop sending it once all platforms switch to using LinkPreview properties
      builder.setArticle(article(title, summary, image, permanentUrl))
      LinkPreview(builder.build())
    }

    private def article(title: String, summary: String, image: Option[Asset], uri: Option[URI]) = {
      val builder = Messages.Article.newBuilder()
      builder.setTitle(title)
      builder.setSummary(summary)
      uri.foreach { u => builder.setPermanentUrl(u.toString) }
      image.foreach(im => builder.setImage(im.proto))
      builder.build()
    }

    implicit object JsDecoder extends JsonDecoder[LinkPreview] {
      override def apply(implicit js: JSONObject): LinkPreview =
        LinkPreview(Messages.LinkPreview.parseFrom(AESUtils.base64(js.getString("proto"))))
    }

    implicit object JsEncoder extends JsonEncoder[LinkPreview] {
      override def apply(v: LinkPreview): JSONObject = JsonEncoder { o =>
        o.put("proto", AESUtils.base64(v.proto.toByteArray))
      }
    }
  }

  final case class Reaction(override val proto: Messages.Reaction) extends GenericContent[Messages.Reaction] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setReaction(proto)

    lazy val unpack: (MessageId, Liking.Action) =
      (MessageId(proto.getMessageId),
       proto.getEmoji match {
         case Reaction.HeavyBlackHeart => Liking.Action.Like
         case _ => Liking.Action.Unlike
       })
  }

  object Reaction {
    val HeavyBlackHeart = "\u2764\uFE0F"

    def apply(msg: MessageId, action: Liking.Action): Reaction = {
      val builder = Messages.Reaction.newBuilder()
      builder.setEmoji(
        action match {
          case Liking.Action.Like => HeavyBlackHeart
          case Liking.Action.Unlike => ""
        }
      )
      builder.setMessageId(msg.str)
      Reaction(builder.build())
    }
  }

  final case class Knock(override val proto: Messages.Knock) extends GenericContent[Messages.Knock] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setKnock(proto)

    def unpack: Boolean = if (proto.hasExpectsReadConfirmation) proto.getExpectsReadConfirmation else false
  }

  object Knock {
    def apply(expectsReadConfirmation: Boolean): Knock = {
      val builder = Messages.Knock.newBuilder()
      builder.setHotKnock(false)
      builder.setExpectsReadConfirmation(expectsReadConfirmation)
      Knock(builder.build())
    }
  }

  final case class Text(override val proto: Messages.Text) extends GenericContent[Messages.Text] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setText(proto)

    lazy val unpack: (String, Seq[com.waz.model.Mention], Seq[LinkPreview], Option[Quote], Boolean) = {
      (
        proto.getContent,
        proto.getMentionsList.asScala.map(m => Mention(m).unpack),
        proto.getLinkPreviewList.asScala.map(LinkPreview(_)),
        if (proto.hasQuote) Some(Quote(proto.getQuote)) else None,
        proto.getExpectsReadConfirmation
      )
    }
  }

  object Text {
    def apply(content: String): Text =
      apply(content, Nil, Nil, None, expectsReadConfirmation = false)

    def apply(content: String, links: Seq[LinkPreview], expectsReadConfirmation: Boolean): Text =
      apply(content, Nil, links, None, expectsReadConfirmation)

    def apply(content: String, mentions: Seq[com.waz.model.Mention], links: Seq[LinkPreview], expectsReadConfirmation: Boolean): Text =
      apply(content, mentions, links, None, expectsReadConfirmation)

    def apply(content: String, mentions: Seq[com.waz.model.Mention], links: Seq[LinkPreview], quote: Option[Quote], expectsReadConfirmation: Boolean): Text = {
      val builder = Messages.Text.newBuilder()
      builder.setContent(content)
      builder.addAllMentions(mentions.map(Mention(_).proto).asJava)
      builder.addAllLinkPreview(links.map(_.proto).asJava)
      builder.setExpectsReadConfirmation(expectsReadConfirmation)
      quote.foreach(q => builder.setQuote(q.proto))
      Text(builder.build())
    }

    def newMentions(text: Text, mentions: Seq[com.waz.model.Mention]): Text = {
      val builder = text.proto.toBuilder
      builder.clearMentions()
      builder.addAllMentions(mentions.map(Mention(_).proto).asJava)
      Text(builder.build())
    }
  }

  //TODO: BUTTONS - add Composite here
  final case class MsgEdit(override val proto: Messages.MessageEdit) extends GenericContent[Messages.MessageEdit] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setEdited(proto)

    lazy val unpack: Option[(MessageId, Text)] =
      proto.getContentCase.getNumber match {
        case Messages.MessageEdit.TEXT_FIELD_NUMBER =>
          Some((MessageId(proto.getReplacingMessageId), Text(proto.getText)))
        case _ =>
          None
      }
  }

  object MsgEdit {
    def apply(ref: MessageId, content: Text): MsgEdit = {
      val builder = Messages.MessageEdit.newBuilder()
      builder.setReplacingMessageId(ref.str)
      builder.setText(content.proto)
      MsgEdit(builder.build())
    }
  }

  final case class Cleared(override val proto: Messages.Cleared) extends GenericContent[Messages.Cleared] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setCleared(proto)

    lazy val unpack: (RConvId, RemoteInstant) =
      (RConvId(proto.getConversationId), RemoteInstant.ofEpochMilli(proto.getClearedTimestamp))
  }

  object Cleared {
    def apply(conv: RConvId, time: RemoteInstant): Cleared  = {
      val builder = Messages.Cleared.newBuilder()
      builder.setConversationId(conv.str)
      builder.setClearedTimestamp(time.toEpochMilli)
      Cleared(builder.build())
    }
  }

  final case class LastRead(override val proto: Messages.LastRead) extends GenericContent[Messages.LastRead] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setLastRead(proto)

    lazy val unpack: (RConvId, RemoteInstant) =
      (RConvId(proto.getConversationId), RemoteInstant.ofEpochMilli(proto.getLastReadTimestamp))
  }

  object LastRead {
    def apply(conv: RConvId, time: RemoteInstant): LastRead  = {
      val builder = Messages.LastRead.newBuilder()
      builder.setConversationId(conv.str)
      builder.setLastReadTimestamp(time.toEpochMilli)
      LastRead(builder.build())
    }
  }

  // not a mistake - we use Messages.MessageHide for MsgDeleted
  final case class MsgDeleted(override val proto: Messages.MessageHide) extends GenericContent[Messages.MessageHide] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setHidden(proto)

    lazy val unpack: (RConvId, MessageId) = (RConvId(proto.getConversationId), MessageId(proto.getMessageId))
  }

  object MsgDeleted {
    def apply(conv: RConvId, msg: MessageId): MsgDeleted  = {
      val builder = Messages.MessageHide.newBuilder()
      builder.setConversationId(conv.str)
      builder.setMessageId(msg.str)
      MsgDeleted(builder.build())
    }
  }

  // not a mistake - we use Messages.MessageDelete for MsgRecall
  final case class MsgRecall(override val proto: Messages.MessageDelete) extends GenericContent[Messages.MessageDelete] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setDeleted(proto)

    lazy val unpack: MessageId = MessageId(proto.getMessageId)
  }

  object MsgRecall {
    def apply(msg: MessageId): MsgRecall  = {
      val builder = Messages.MessageDelete.newBuilder()
      builder.setMessageId(msg.str)
      MsgRecall(builder.build())
    }
  }

  final case class Location(override val proto: Messages.Location) extends GenericContent[Messages.Location] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setLocation(proto)

    lazy val unpack: (Float, Float, Option[String], Option[Int], Boolean) =
      (
        proto.getLongitude,
        proto.getLatitude,
        if (proto.hasName) Option(proto.getName).filter(_.nonEmpty) else None,
        if (proto.hasZoom) Option(proto.getZoom).filter(_ != 0) else None,
        proto.getExpectsReadConfirmation
      )
  }

  object Location {
    def apply(lon: Float, lat: Float, name: String, zoom: Int, expectsReadConfirmation: Boolean): Location = {
      val builder = Messages.Location.newBuilder()
      builder.setLongitude(lon)
      builder.setLatitude(lat)
      builder.setName(name)
      builder.setZoom(zoom)
      builder.setExpectsReadConfirmation(expectsReadConfirmation)
      Location(builder.build())
    }
  }

  final case class DeliveryReceipt(override val proto: Messages.Confirmation) extends GenericContent[Messages.Confirmation] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setConfirmation(proto)

    lazy val unpack: Option[Seq[MessageId]] =
      if (proto.getType == Messages.Confirmation.Type.DELIVERED) {
        val msgs = new scala.collection.mutable.ArrayBuffer[MessageId](proto.getMoreMessageIdsCount + 1)
        msgs += MessageId(proto.getFirstMessageId)
        (0 until proto.getMoreMessageIdsCount).foreach { i =>
          msgs += MessageId(proto.getMoreMessageIds(i))
        }
        Some(msgs.toVector)
      } else None
  }

  object DeliveryReceipt {
    def apply(msg: MessageId): DeliveryReceipt = {
      val builder = Messages.Confirmation.newBuilder()
      builder.setFirstMessageId(msg.str)
      builder.setType(Messages.Confirmation.Type.DELIVERED)
      DeliveryReceipt(builder.build())
    }

    def apply(msgs: Seq[MessageId]): DeliveryReceipt = {
      val builder = Messages.Confirmation.newBuilder()
      builder.setFirstMessageId(msgs.head.str)
      builder.addAllMoreMessageIds(msgs.tail.map(_.str).asJava)
      builder.setType(Messages.Confirmation.Type.DELIVERED)
      DeliveryReceipt(builder.build())
    }
  }

  final case class ReadReceipt(override val proto: Messages.Confirmation) extends GenericContent[Messages.Confirmation] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setConfirmation(proto)

    lazy val unpack: Option[Seq[MessageId]] =
      if (proto.getType == Messages.Confirmation.Type.READ) {
        val msgs = new scala.collection.mutable.ArrayBuffer[MessageId](proto.getMoreMessageIdsCount + 1)
        msgs += MessageId(proto.getFirstMessageId)
        (0 until proto.getMoreMessageIdsCount).foreach { i =>
          msgs += MessageId(proto.getMoreMessageIds(i))
        }
        Some(msgs.toVector)
      } else None
  }

  object ReadReceipt {
    def apply(msg: MessageId): ReadReceipt = {
      val builder = Messages.Confirmation.newBuilder()
      builder.setFirstMessageId(msg.str)
      builder.setType(Messages.Confirmation.Type.READ)
      ReadReceipt(builder.build())
    }

    def apply(msgs: Seq[MessageId]): ReadReceipt = {
      val builder = Messages.Confirmation.newBuilder()
      builder.setFirstMessageId(msgs.head.str)
      builder.addAllMoreMessageIds(msgs.tail.map(_.str).asJava)
      builder.setType(Messages.Confirmation.Type.READ)
      ReadReceipt(builder.build())
    }
  }

  final case class External(override val proto: Messages.External) extends GenericContent[Messages.External] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setExternal(proto)

    lazy val unpack: (AESKey, Sha256) =
      (AESKey(proto.getOtrKey.toByteArray), Sha256(proto.getSha256.toByteArray))
  }

  object External {
    def apply(key: AESKey, sha: Sha256): External = {
      val builder = Messages.External.newBuilder()
      builder.setOtrKey(ByteString.copyFrom(key.bytes))
      builder.setSha256(ByteString.copyFrom(sha.bytes))
      External(builder.build())
    }
  }

  final case class EphemeralAsset(override val proto: Messages.Ephemeral) extends EphemeralContent {
    override def set(builder: Messages.Ephemeral.Builder): Unit = {
      if (proto.hasAsset) builder.setAsset(proto.getAsset)
      if (proto.hasExpireAfterMillis) builder.setExpireAfterMillis(proto.getExpireAfterMillis)
    }
  }

  object EphemeralAsset {
    def apply(asset: Messages.Asset, expiry: FiniteDuration): EphemeralAsset = {
      val builder = Messages.Ephemeral.newBuilder()
      builder.setAsset(asset)
      builder.setExpireAfterMillis(expiry.toMillis)
      EphemeralAsset(builder.build())
    }
  }

  final case class EphemeralImageAsset(override val proto: Messages.Ephemeral) extends EphemeralContent {
    override def set(builder: Messages.Ephemeral.Builder): Unit = {
      if (proto.hasImage) builder.setImage(proto.getImage)
      if (proto.hasExpireAfterMillis) builder.setExpireAfterMillis(proto.getExpireAfterMillis)
    }
  }

  object EphemeralImageAsset {
    def apply(image: Messages.ImageAsset, expiry: FiniteDuration): EphemeralAsset = {
      val builder = Messages.Ephemeral.newBuilder()
      builder.setImage(image)
      builder.setExpireAfterMillis(expiry.toMillis)
      EphemeralAsset(builder.build())
    }
  }

  case class EphemeralLocation(override val proto: Messages.Ephemeral) extends EphemeralContent {
    override def set(builder: Messages.Ephemeral.Builder): Unit = {
      if (proto.hasLocation) builder.setLocation(proto.getLocation)
      if (proto.hasExpireAfterMillis) builder.setExpireAfterMillis(proto.getExpireAfterMillis)
    }
  }

  object EphemeralLocation {
    def apply(location: Messages.Location, expiry: FiniteDuration): EphemeralLocation = {
      val builder = Messages.Ephemeral.newBuilder()
      builder.setLocation(location)
      builder.setExpireAfterMillis(expiry.toMillis)
      EphemeralLocation(builder.build())
    }
  }

  final case class EphemeralText(override val proto: Messages.Ephemeral) extends EphemeralContent {
    override def set(builder: Messages.Ephemeral.Builder): Unit = {
      if (proto.hasText) builder.setText(proto.getText)
      if (proto.hasExpireAfterMillis) builder.setExpireAfterMillis(proto.getExpireAfterMillis)
    }
  }

  object EphemeralText {
    def apply(text: Messages.Text, expiry: FiniteDuration): EphemeralText = {
      val builder = Messages.Ephemeral.newBuilder()
      builder.setText(text)
      builder.setExpireAfterMillis(expiry.toMillis)
      EphemeralText(builder.build())
    }
  }

  final case class EphemeralKnock(override val proto: Messages.Ephemeral) extends EphemeralContent {
    override def set(builder: Messages.Ephemeral.Builder): Unit = {
      if (proto.hasKnock) builder.setKnock(proto.getKnock)
      if (proto.hasExpireAfterMillis) builder.setExpireAfterMillis(proto.getExpireAfterMillis)
    }
  }

  object EphemeralKnock {
    def apply(knock: Messages.Knock, expiry: FiniteDuration): EphemeralKnock = {
      val builder = Messages.Ephemeral.newBuilder()
      builder.setKnock(knock)
      builder.setExpireAfterMillis(expiry.toMillis)
      EphemeralKnock(builder.build())
    }
  }

  final case class Ephemeral(override val proto: Messages.Ephemeral) extends GenericContent[Messages.Ephemeral] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setEphemeral(proto)

    lazy val unpack: (Option[FiniteDuration], GenericContent[_]) =
      if (!proto.hasExpireAfterMillis) (None, content)
      else proto.getExpireAfterMillis match {
        case 0      => (None, content)
        case millis => (Some(EphemeralDuration(millis)), content)
      }

    def unpackContent: GenericContent[_] = unpack._2

    private lazy val content: GenericContent[_] = proto.getContentCase.getNumber match {
      case Messages.Ephemeral.TEXT_FIELD_NUMBER     => Text(proto.getText)
      case Messages.Ephemeral.ASSET_FIELD_NUMBER    => Asset(proto.getAsset)
      case Messages.Ephemeral.IMAGE_FIELD_NUMBER    => ImageAsset(proto.getImage)
      case Messages.Ephemeral.KNOCK_FIELD_NUMBER    => Knock(proto.getKnock)
      case Messages.Ephemeral.LOCATION_FIELD_NUMBER => Location(proto.getLocation)
      case _ => Unknown
    }
  }

  object Ephemeral {
    def apply(expiry: Option[FiniteDuration], content: EphemeralContent): Ephemeral = {
      val builder = Messages.Ephemeral.newBuilder()
      builder.setExpireAfterMillis(expiry.getOrElse(Duration.Zero).toMillis)
      content.set(builder)
      Ephemeral(builder.build())
    }
  }

  final case class AvailabilityStatus(override val proto: Messages.Availability) extends GenericContent[Messages.Availability] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setAvailability(proto)

    lazy val unpack: Option[Availability] = proto.getType match {
      case Messages.Availability.Type.NONE      => Some(Availability.None)
      case Messages.Availability.Type.AVAILABLE => Some(Availability.Available)
      case Messages.Availability.Type.AWAY      => Some(Availability.Away)
      case Messages.Availability.Type.BUSY      => Some(Availability.Busy)
      case _ => None
    }
  }

  object AvailabilityStatus {
    def apply(av: Availability): AvailabilityStatus = {
      val builder = Messages.Availability.newBuilder()
      builder.setType(av match {
        case Availability.None      => Messages.Availability.Type.NONE
        case Availability.Available => Messages.Availability.Type.AVAILABLE
        case Availability.Away      => Messages.Availability.Type.AWAY
        case Availability.Busy      => Messages.Availability.Type.BUSY
      })
      AvailabilityStatus(builder.build())
    }
  }

  final case object Unknown extends GenericContent[Unit] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = {}

    override val proto: Unit = ()
  }

  sealed trait ClientAction extends GenericContent[Int] {
    override val proto: Int
    override def set(builder: Messages.GenericMessage.Builder): Unit =
      if (proto == ClientAction.SessionReset.proto) builder.setClientAction(Messages.ClientAction.RESET_SESSION)
  }

  object ClientAction {
    final case object SessionReset extends ClientAction {
      override val proto: Int = Messages.ClientAction.RESET_SESSION.getNumber
    }

    final case class UnknownAction(override val proto: Int) extends ClientAction

    def apply(v: Int): ClientAction =
      if (v == SessionReset.proto) SessionReset else UnknownAction(v)
  }

  final case class Calling(override val proto: Messages.Calling) extends GenericContent[Messages.Calling] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setCalling(proto)

    lazy val unpack: String = proto.getContent
  }

  object Calling {
    def apply(content: String): Calling = {
      val builder = Messages.Calling.newBuilder()
      builder.setContent(content)
      Calling(builder.build())
    }
  }

  final case class ButtonActionConfirmation(override val proto: Messages.ButtonActionConfirmation)
    extends GenericContent[Messages.ButtonActionConfirmation] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setButtonActionConfirmation(proto)

    lazy val unpack: (MessageId, Option[ButtonId]) =
      (MessageId(proto.getReferenceMessageId), if (proto.hasButtonId) Some(ButtonId(proto.getButtonId)) else None)
  }

  final case class ButtonAction(override val proto: Messages.ButtonAction) extends GenericContent[Messages.ButtonAction] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setButtonAction(proto)
  }

  object ButtonAction {
    def apply(buttonId: String, referenceMsgId: String): ButtonAction = {
      val builder = Messages.ButtonAction.newBuilder()
      builder.setButtonId(buttonId)
      builder.setReferenceMessageId(referenceMsgId)
      ButtonAction(builder.build())
    }
  }

  final case class Button(override val proto: Messages.Button) extends GenericContent[Messages.Button]

  final case class Composite(override val proto: Messages.Composite) extends GenericContent[Messages.Composite] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setComposite(proto)

    lazy val unpack: CompositeData = {
      import scala.collection.JavaConverters._
      val items = proto.getItemsList.asScala.map { protoItem =>
        if (protoItem.hasText) TextItem(Text(protoItem.getText))
        else if (protoItem.hasButton) ButtonItem(Button(protoItem.getButton))
        else UnknownItem
      }
      CompositeData(items, Option(proto.getExpectsReadConfirmation), Option(proto.getLegalHoldStatus.getNumber))
    }
  }

  final case class DataTransfer(override val proto: Messages.DataTransfer) extends GenericContent[Messages.DataTransfer] {
    override def set(builder: Messages.GenericMessage.Builder): Unit = builder.setDataTransfer(proto)

    lazy val unpack: TrackingId = TrackingId(proto.getTrackingIdentifier.getIdentifier)
  }

  object DataTransfer {
    def apply(trackingId: TrackingId): DataTransfer = {
      val trackingBuilder = Messages.TrackingIdentifier.newBuilder()
      trackingBuilder.setIdentifier(trackingId.str)
      val builder = Messages.DataTransfer.newBuilder()
      builder.setTrackingIdentifier(trackingBuilder.build())
      DataTransfer(builder.build())
    }
  }
}

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

import android.database.DatabaseUtils.queryNumEntries
import android.database.sqlite.SQLiteQueryBuilder
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api
import com.waz.api.Message.Type._
import com.waz.api.{Message, TypeFilter}
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model.GenericContent.{Asset, ImageAsset, LinkPreview, Location, MsgEdit, Text}
import com.waz.model.GenericMessage.{GenericMessageContent, TextMessage}
import com.waz.model.MessageData.MessageState
import com.waz.model.messages.media.{MediaAssetData, MediaAssetDataProtocol}
import com.waz.service.ZMessaging.clock
import com.waz.service.media.{MessageContentBuilder, RichMediaContentParser}
import com.waz.sync.client.OpenGraphClient.OpenGraphData
import com.waz.utils.wrappers.{DB, DBCursor, URI}
import com.waz.utils.{EnumCodec, JsonDecoder, JsonEncoder, returning}
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant.now
import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.collection.breakOut
import scala.concurrent.duration._

case class MessageData(id:            MessageId              = MessageId(),
                       convId:        ConvId                 = ConvId(),
                       msgType:       Message.Type           = Message.Type.TEXT,
                       userId:        UserId                 = UserId(),
                       content:       Seq[MessageContent]    = Seq.empty,
                       protos:        Seq[GenericMessage]    = Seq.empty,
                       firstMessage:  Boolean                = false,
                       members:       Set[UserId]            = Set.empty[UserId],
                       recipient:     Option[UserId]         = None,
                       email:         Option[String]         = None,
                       name:          Option[String]         = None,
                       state:         MessageState           = Message.Status.SENT,
                       time:          RemoteInstant          = RemoteInstant(now(clock)), //TODO: now is local...
                       localTime:     LocalInstant           = LocalInstant.Epoch,
                       editTime:      RemoteInstant          = RemoteInstant.Epoch,
                       ephemeral:     Option[FiniteDuration] = None,
                       expiryTime:    Option[LocalInstant]   = None, // local expiration time
                       expired:       Boolean                = false,
                       duration:      Option[FiniteDuration] = None //for successful calls and message_timer changes
                      ) {

  override def toString: String =
    s"""
       |MessageData:
       | id:            $id
       | convId:        $convId
       | msgType:       $msgType
       | userId:        $userId
       | protos:        ${protos.toString().replace("\n", "")}
       | state:         $state
       | time:          $time
       | localTime:     $localTime
       | editTime:      $editTime
       | members:       $members
       | content:       ${content.map(c => (c.content, c.mentions))}
       | other fields:  $firstMessage, $recipient, $email, $name, $ephemeral, $expiryTime, $expired, $duration
    """.stripMargin


  def getContent(index: Int) = {
    if (index == 0) content.headOption.getOrElse(MessageContent.Empty)
    else content.drop(index).headOption.getOrElse(MessageContent.Empty)
  }

  lazy val contentString = protos.lastOption match {
    case Some(TextMessage(ct, _, _)) => ct
    case _ if msgType == api.Message.Type.RICH_MEDIA => content.map(_.content).mkString(" ")
    case _ => content.headOption.fold("")(_.content)
  }

  def assetId = AssetId(id.str)

  def isLocal = state == Message.Status.DEFAULT || state == Message.Status.PENDING || state == Message.Status.FAILED || state == Message.Status.FAILED_READ

  def isDeleted = msgType == Message.Type.RECALLED

  lazy val mentions = content.flatMap(_.mentions)
  lazy val hasMentions = mentions.nonEmpty

  def hasMentionOf(userId: UserId): Boolean = mentions.exists(_.userId.forall(_ == userId)) // a mention with userId == None is a "mention" of everyone, so it counts

  lazy val imageDimensions: Option[Dim2] = {
    val dims = protos.collectFirst {
      case GenericMessageContent(Asset(AssetData.WithDimensions(d), _)) => d
      case GenericMessageContent(ImageAsset(AssetData.WithDimensions(d))) => d
    } orElse content.headOption.collect {
      case MessageContent(_, _, _, _, Some(_), w, h, _, _) => Dim2(w, h)
    }
    verbose(s"dims $dims from protos: $protos")
    dims
  }

  lazy val location =
    protos.collectFirst {
      case GenericMessageContent(Location(lon, lat, descr, zoom)) => new api.MessageContent.Location(lon, lat, descr.getOrElse(""), zoom.getOrElse(14))
    }

  /**
   * System messages are messages generated by backend in response to user actions.
   * Those messages are not encrypted and don't have global message id (nonce).
   *
   */
  def isSystemMessage = msgType match {
    case RENAME | CONNECT_REQUEST | CONNECT_ACCEPTED | MEMBER_JOIN | MEMBER_LEAVE | MISSED_CALL | SUCCESSFUL_CALL | MESSAGE_TIMER => true
    case _ => false
  }

  def canRecall(convId: ConvId, userId: UserId) =
    msgType != RECALLED && this.convId == convId && this.userId == userId && !isSystemMessage

  def isAssetMessage = MessageData.IsAsset(msgType)

  def isEphemeral = ephemeral.isDefined

  def hasSameContentType(m: MessageData) = {
    msgType == m.msgType && content.zip(m.content).forall { case (c, c1) => c.tpe == c1.tpe && c.openGraph.isDefined == c1.openGraph.isDefined } // openGraph may affect message type
  }

  def adjustMentions(forSending: Boolean): Option[MessageData] =
    if (mentions.isEmpty) None
    else {
      verbose(s"adjustMentions(forSending = $forSending)")
      val newContent =
        if (content.size == 1)
          content.map(_.copy(mentions = MessageData.adjustMentions(content.head.content, mentions, forSending)))
        else
          content.foldLeft("", Seq.empty[MessageContent]) { case ((processedText, acc), ct) =>
            val newProcessedText = processedText + ct.content
            val start = processedText.length
            val end   = newProcessedText.length
            val ms    = mentions.filter(m => m.start >= start && m.start + m.length < end) // we assume mentions are not split over many contents
            (
              newProcessedText,
              acc ++ Seq(if (ms.isEmpty) ct else ct.copy(mentions = MessageData.adjustMentions(ct.content, ms, forSending, start)))
            )
          }._2

      val newMentions = newContent.flatMap(_.mentions)

      val newProto = protos.lastOption match {
        case Some(GenericMessage(uid, MsgEdit(ref, Text(_, _, links)))) =>
          GenericMessage(uid, MsgEdit(ref, Text(contentString, newMentions, links)))
        case Some(GenericMessage(uid, Text(_, _, links))) =>
          GenericMessage(uid, ephemeral, Text(contentString, newMentions, links))
        case _ =>
          GenericMessage(id.uid, ephemeral, Text(contentString, newMentions, Nil))
      }

      if (content == newContent && protos.lastOption.contains(newProto)) None
      else Some(copy(content = newContent, protos = Seq(newProto)))
    }
}

case class MessageContent(tpe:        Message.Part.Type,
                          content:    String,
                          richMedia:  Option[MediaAssetData],
                          openGraph:  Option[OpenGraphData],
                          asset:      Option[AssetId],
                          width:      Int,
                          height:     Int,
                          syncNeeded: Boolean,
                          mentions:   Seq[Mention]
                         ) {

  def contentAsUri: URI = RichMediaContentParser.parseUriWithScheme(content)
  override def toString: String = s"MessageContent($tpe, ${content.take(4)}..., $richMedia, $openGraph, $asset, $width, $height, $syncNeeded, $mentions)"
}

object MessageContent extends ((Message.Part.Type, String, Option[MediaAssetData], Option[OpenGraphData], Option[AssetId], Int, Int, Boolean, Seq[Mention]) => MessageContent) {

  import MediaAssetDataProtocol._

  val Empty = apply(Message.Part.Type.TEXT, "")

  def apply(tpe: Message.Part.Type,
            content: String,
            openGraph: Option[OpenGraphData] = None,
            asset: Option[AssetId] = None,
            width: Int = 0, height: Int = 0,
            syncNeeded: Boolean = false,
            mentions: Seq[Mention] = Nil): MessageContent = {
    MessageContent(tpe, content, emptyMediaAsset(tpe), openGraph, asset, width, height, syncNeeded, mentions)
  }

  def emptyMediaAsset(tpe: Message.Part.Type) =
    if (tpe == Message.Part.Type.SPOTIFY || tpe == Message.Part.Type.SOUNDCLOUD || tpe == Message.Part.Type.YOUTUBE) Some(MediaAssetData.empty(tpe)) else None

  implicit lazy val Decoder: JsonDecoder[MessageContent] = new JsonDecoder[MessageContent] {
    import com.waz.utils.JsonDecoder._

    private def decodeMentions(arr: JSONArray) =
      Seq.tabulate(arr.length())(arr.getJSONObject).map { implicit obj =>
        Mention(decodeOptId[UserId]('user_id), decodeInt('start), decodeInt('length))
      }

    override def apply(implicit js: JSONObject): MessageContent = {
      val tpe = ContentTypeCodec.decode('type)
      if (js.has("connections")) array[UserConnectionEvent](js.getJSONArray("connections")).toList else Nil
      val mentions = if (js.has("mentions") && !js.isNull("mentions")) decodeMentions(js.getJSONArray("mentions")) else Nil
      val richMedia = opt[MediaAssetData]('richMedia) orElse { // if there's no media asset for rich media message contents, we create an expired empty one
        if (tpe == Message.Part.Type.SPOTIFY || tpe == Message.Part.Type.SOUNDCLOUD || tpe == Message.Part.Type.YOUTUBE) Some(MediaAssetData.empty(tpe)) else None
      }

      MessageContent(tpe, 'content, richMedia, opt[OpenGraphData]('openGraph), decodeOptId[AssetId]('asset), 'width, 'height, 'syncNeeded, mentions)
    }
  }

  implicit lazy val Encoder: JsonEncoder[MessageContent] = new JsonEncoder[MessageContent] {
    private def encodeMentions(mentions: Seq[Mention]): JSONArray = returning(new JSONArray()){ arr =>
      mentions.map { case Mention(userId, start, length) =>
        JsonEncoder { o =>
          userId.map(id => o.put("user_id", id))
          o.put("start", start)
          o.put("length", length)
        }
      }.foreach(arr.put)
    }

    override def apply(v: MessageContent): JSONObject = JsonEncoder { o =>
      o.put("type", ContentTypeCodec.encode(v.tpe))
      if (v.content != "") o.put("content", v.content)
      v.richMedia foreach (m => o.put("richMedia", MediaAssetEncoder(m)))
      v.asset.foreach { id => o.put("asset", id.str) }
      v.openGraph foreach { og => o.put("openGraph", OpenGraphData.Encoder(og)) }
      if (v.width != 0) o.put("width", v.width)
      if (v.height != 0) o.put("height", v.height)
      if (v.syncNeeded) o.put("syncNeeded", v.syncNeeded)
      if (v.mentions.nonEmpty) o.put("mentions", encodeMentions(v.mentions))
    }
  }


  implicit lazy val ContentTypeCodec: EnumCodec[Message.Part.Type, String] = EnumCodec.injective {
    case Message.Part.Type.TEXT            => "Text"
    case Message.Part.Type.TEXT_EMOJI_ONLY => "TextEmojiOnly"
    case Message.Part.Type.ASSET           => "Asset"
    case Message.Part.Type.ANY_ASSET       => "AnyAsset"
    case Message.Part.Type.YOUTUBE         => "YouTube"
    case Message.Part.Type.SOUNDCLOUD      => "SoundCloud"
    case Message.Part.Type.SPOTIFY         => "Spotify"
    case Message.Part.Type.TWITTER         => "Twitter"
    case Message.Part.Type.WEB_LINK        => "WebLink"
    case Message.Part.Type.GOOGLE_MAPS     => "GoogleMaps"
  }
}

object MessageData extends ((MessageId, ConvId, Message.Type, UserId, Seq[MessageContent], Seq[GenericMessage], Boolean, Set[UserId], Option[UserId], Option[String], Option[String], Message.Status, RemoteInstant, LocalInstant, RemoteInstant, Option[FiniteDuration], Option[LocalInstant], Boolean, Option[FiniteDuration]) => MessageData) {
  val Empty = new MessageData(MessageId(""), ConvId(""), Message.Type.UNKNOWN, UserId(""))
  val Deleted = new MessageData(MessageId(""), ConvId(""), Message.Type.UNKNOWN, UserId(""), state = Message.Status.DELETED)
  val isUserContent = Set(TEXT, TEXT_EMOJI_ONLY, ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, RICH_MEDIA, LOCATION)

  val EphemeralMessageTypes = Set(TEXT, TEXT_EMOJI_ONLY, KNOCK, ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, RICH_MEDIA, LOCATION)

  type MessageState = Message.Status
  import GenericMessage._

  implicit lazy val MessageTypeCodec: EnumCodec[Message.Type, String] = EnumCodec.injective {
    case Message.Type.TEXT                 => "Text"
    case Message.Type.TEXT_EMOJI_ONLY      => "TextEmojiOnly"
    case Message.Type.ASSET                => "Asset"
    case Message.Type.ANY_ASSET            => "AnyAsset"
    case Message.Type.VIDEO_ASSET          => "VideoAsset"
    case Message.Type.AUDIO_ASSET          => "AudioAsset"
    case Message.Type.KNOCK                => "Knock"
    case Message.Type.MEMBER_JOIN          => "MemberJoin"
    case Message.Type.MEMBER_LEAVE         => "MemberLeave"
    case Message.Type.CONNECT_REQUEST      => "ConnectRequest"
    case Message.Type.CONNECT_ACCEPTED     => "ConnectAccepted"
    case Message.Type.RENAME               => "Rename"
    case Message.Type.MISSED_CALL          => "MissedCall"
    case Message.Type.SUCCESSFUL_CALL      => "SuccessfulCall"
    case Message.Type.RICH_MEDIA           => "RichMedia"
    case Message.Type.OTR_ERROR            => "OtrFailed"
    case Message.Type.OTR_IDENTITY_CHANGED => "OtrIdentityChanged"
    case Message.Type.OTR_VERIFIED         => "OtrVerified"
    case Message.Type.OTR_UNVERIFIED       => "OtrUnverified"
    case Message.Type.OTR_DEVICE_ADDED     => "OtrDeviceAdded"
    case Message.Type.OTR_MEMBER_ADDED     => "OtrMemberAdded"
    case Message.Type.STARTED_USING_DEVICE => "StartedUsingDevice"
    case Message.Type.HISTORY_LOST         => "HistoryLost"
    case Message.Type.LOCATION             => "Location"
    case Message.Type.UNKNOWN              => "Unknown"
    case Message.Type.RECALLED             => "Recalled"
    case Message.Type.MESSAGE_TIMER        => "MessageTimer"
  }

  implicit object MessageDataDao extends Dao[MessageData, MessageId]  {
    import com.waz.db._

    val Id = id[MessageId]('_id, "PRIMARY KEY").apply(_.id)
    val Conv = id[ConvId]('conv_id).apply(_.convId)
    val Type = text[Message.Type]('msg_type, MessageTypeCodec.encode, MessageTypeCodec.decode)(_.msgType)
    val User = id[UserId]('user_id).apply(_.userId)
    val Content = jsonArray[MessageContent, Seq, Vector]('content).apply(_.content)
    val Protos = protoSeq[GenericMessage, Seq, Vector]('protos).apply(_.protos)
    val ContentSize = int('content_size)(_.content.size)
    val FirstMessage = bool('first_msg)(_.firstMessage)
    val Members = set[UserId]('members, _.mkString(","), _.split(",").filter(!_.isEmpty).map(UserId(_))(breakOut))(_.members)
    val Recipient = opt(id[UserId]('recipient))(_.recipient)
    val Email = opt(text('email))(_.email)
    val Name = opt(text('name))(_.name)
    val State = text[MessageState]('msg_state, _.name, Message.Status.valueOf)(_.state)
    val Time = remoteTimestamp('time)(_.time)
    val LocalTime = localTimestamp('local_time)(_.localTime)
    val EditTime = remoteTimestamp('edit_time)(_.editTime)
    val Ephemeral = opt(finiteDuration('ephemeral))(_.ephemeral)
    val ExpiryTime = opt(localTimestamp('expiry_time))(_.expiryTime)
    val Expired = bool('expired)(_.expired)
    val Duration = opt(finiteDuration('duration))(_.duration)

    override val idCol = Id

    override val table = Table("Messages", Id, Conv, Type, User, Content, Protos, Time, LocalTime, FirstMessage, Members, Recipient, Email, Name, State, ContentSize, EditTime, Ephemeral, ExpiryTime, Expired, Duration)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Messages_conv_time on Messages ( conv_id, time)")
    }

    override def apply(implicit cursor: DBCursor): MessageData =
      MessageData(Id, Conv, Type, User, Content, Protos, FirstMessage, Members, Recipient, Email, Name, State, Time, LocalTime, EditTime, Ephemeral, ExpiryTime, Expired, Duration)

    def deleteForConv(id: ConvId)(implicit db: DB) = delete(Conv, id)

    def deleteUpTo(id: ConvId, upTo: RemoteInstant)(implicit db: DB) = db.delete(table.name, s"${Conv.name} = '${id.str}' AND ${Time.name} <= ${Time(upTo)}", null)

    def first(conv: ConvId)(implicit db: DB) = single(db.query(table.name, null, s"${Conv.name} = '$conv'", null, null, null, s"${Time.name} ASC", "1"))

    def last(conv: ConvId)(implicit db: DB) = single(db.query(table.name, null, s"${Conv.name} = '$conv'", null, null, null, s"${Time.name} DESC", "1"))

    def lastSent(conv: ConvId)(implicit db: DB) = single(db.query(table.name, null, s"${Conv.name} = '$conv' AND ${State.name} IN ('${Message.Status.SENT.name}', '${Message.Status.DELIVERED.name}')", null, null, null, s"${Time.name} DESC", "1"))

    def lastFromSelf(conv: ConvId, selfUserId: UserId)(implicit db: DB) = single(db.query(table.name, null, s"${Conv.name} = '${Conv(conv)}' AND ${User.name} = '${User(selfUserId)}' AND $userContentPredicate", null, null, null, s"${Time.name} DESC", "1"))

    def lastFromOther(conv: ConvId, selfUserId: UserId)(implicit db: DB) = single(db.query(table.name, null, s"${Conv.name} = '${Conv(conv)}' AND ${User.name} != '${User(selfUserId)}' AND $userContentPredicate", null, null, null, s"${Time.name} DESC", "1"))

    private val userContentPredicate = isUserContent.map(t => s"${Type.name} = '${Type(t)}'").mkString("(", " OR ", ")")

    def lastIncomingKnock(convId: ConvId, selfUser: UserId)(implicit db: DB): Option[MessageData] = single(
      db.query(table.name, null, s"${Conv.name} = ? AND ${Type.name} = ? AND ${User.name} <> ?", Array(convId.toString, Type(Message.Type.KNOCK), selfUser.str), null, null, s"${Time.name} DESC", "1")
    )

    def lastMissedCall(convId: ConvId)(implicit db: DB): Option[MessageData] = single(
      db.query(table.name, null, s"${Conv.name} = ? AND ${Type.name} = ?", Array(convId.toString, Type(Message.Type.MISSED_CALL)), null, null, s"${Time.name} DESC", "1")
    )

    private val MessageEntryColumns = Array(Id.name, User.name, Type.name, State.name, ContentSize.name)
    private val MessageEntryReader = new Reader[MessageEntry] {
      override def apply(implicit c: DBCursor): MessageEntry = MessageEntry(Id, User, Type, State, ContentSize)
    }

    def countMessages(convId: ConvId, p: MessageEntry => Boolean)(implicit db: DB): Int =
      iteratingWithReader(MessageEntryReader)(db.query(table.name, MessageEntryColumns, s"${Conv.name} = ?", Array(convId.toString), null, null, null)).acquire(_ count p)

    def countNewer(convId: ConvId, time: RemoteInstant)(implicit db: DB) =
      queryNumEntries(db, table.name, s"${Conv.name} = '${convId.str}' AND ${Time.name} > ${time.toEpochMilli}")

    def countFailed(convId: ConvId)(implicit db: DB) = queryNumEntries(db, table.name, s"${Conv.name} = '${convId.str}' AND ${State.name} = '${Message.Status.FAILED}'")

    def listLocalMessages(convId: ConvId)(implicit db: DB) = list(db.query(table.name, null, s"${Conv.name} = '$convId' AND ${State.name} in ('${Message.Status.DEFAULT}', '${Message.Status.PENDING}', '${Message.Status.FAILED}')", null, null, null, s"${Time.name} ASC"))

    //TODO: use local instant?
    def findLocalFrom(convId: ConvId, time: RemoteInstant)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Conv.name} = '$convId' AND ${State.name} in ('${Message.Status.DEFAULT}', '${Message.Status.PENDING}', '${Message.Status.FAILED}') AND ${Time.name} >= ${time.toEpochMilli}", null, null, null, s"${Time.name} ASC"))

    def findLatestUpTo(convId: ConvId, time: RemoteInstant)(implicit db: DB) =
      single(db.query(table.name, null, s"${Conv.name} = '$convId' AND ${Time.name} < ${time.toEpochMilli}", null, null, null, s"${Time.name} DESC", "1"))

    def findMessages(conv: ConvId)(implicit db: DB) = db.query(table.name, null, s"${Conv.name} = '$conv'", null, null, null, s"${Time.name} ASC")

    def findMessagesFrom(conv: ConvId, time: RemoteInstant)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Conv.name} = '$conv' and ${Time.name} >= ${time.toEpochMilli}", null, null, null, s"${Time.name} ASC"))

    def findExpired(time: LocalInstant = LocalInstant.Now)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${ExpiryTime.name} IS NOT NULL and ${ExpiryTime.name} <= ${time.toEpochMilli}", null, null, null, s"${ExpiryTime.name} ASC"))

    def findExpiring()(implicit db: DB) =
      iterating(db.query(table.name, null, s"${ExpiryTime.name} IS NOT NULL AND ${Expired.name} = 0", null, null, null, s"${ExpiryTime.name} ASC"))

    def findEphemeral(conv: ConvId)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Conv.name} = '${conv.str}' and ${Ephemeral.name} IS NOT NULL and ${ExpiryTime.name} IS NULL", null, null, null, s"${Time.name} ASC"))

    def findSystemMessage(conv: ConvId, serverTime: RemoteInstant, tpe: Message.Type, sender: UserId)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Conv.name} = '${conv.str}' and ${Time.name} = ${Time(serverTime)} and ${Type.name} = '${Type(tpe)}' and ${User.name} = '${User(sender)}'", null, null, null, s"${Time.name} DESC"))

    private val IndexColumns = Array(Id.name, Time.name)
    def msgIndexCursor(conv: ConvId)(implicit db: DB) = db.query(table.name, IndexColumns, s"${Conv.name} = '$conv'", null, null, null, s"${Time.name} ASC")

    def countAtLeastAsOld(conv: ConvId, time: RemoteInstant)(implicit db: DB) =
      queryNumEntries(db, table.name, s"""${Conv.name} = '${Conv(conv)}' AND ${Time.name} <= ${Time(time)}""")

    def countLaterThan(conv: ConvId, time: RemoteInstant)(implicit db: DB) =
      queryNumEntries(db, table.name, s"""${Conv.name} = '${Conv(conv)}' AND ${Time.name} > ${Time(time)}""")

    def countSentByType(selfUserId: UserId, tpe: Message.Type)(implicit db: DB) = queryNumEntries(db, table.name, s"${User.name} = '${User(selfUserId)}' AND ${Type.name} = '${Type(tpe)}'")


    def findByType(conv: ConvId, tpe: Message.Type)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Conv.name} = '$conv' AND ${Type.name} = '${Type(tpe)}'", null, null, null, s"${Time.name} ASC"))

    def msgIndexCursorFiltered(conv: ConvId, types: Seq[TypeFilter], limit: Option[Int] = None)(implicit db: DB): DBCursor = {
      val builder = new SQLiteQueryBuilder()
      val q = builder.buildUnionQuery(
        types.map(mt =>
          s"SELECT * FROM (" +
            SQLiteQueryBuilder.buildQueryString(false, table.name, IndexColumns, s"${Conv.name} = '$conv' AND ${Type.name} = '${Type(mt.msgType)}' AND ${Expired.name} = 0", null, null, s"${Time.name} DESC", mt.limit.fold[String](null)(_.toString)) +
            s")").toArray,
        null, limit.fold[String](null)(_.toString))
      db.rawQuery(q, null)
    }
  }

  case class MessageEntry(id: MessageId, user: UserId, tpe: Message.Type = Message.Type.TEXT, state: Message.Status = Message.Status.DEFAULT, contentSize: Int = 1)

  def messageContent(message: String, mentions: Seq[Mention], links: Seq[LinkPreview] = Nil, weblinkEnabled: Boolean = false): (Message.Type, Seq[MessageContent]) =
    if (message.trim.isEmpty) (Message.Type.TEXT, textContent(message))
    else {
      if (links.isEmpty) {
        val ct = RichMediaContentParser.splitContent(message, mentions, 0, weblinkEnabled)

        (ct.size, ct.head.tpe) match {
          case (1, Message.Part.Type.TEXT) => (Message.Type.TEXT, ct)
          case (1, Message.Part.Type.TEXT_EMOJI_ONLY) => (Message.Type.TEXT_EMOJI_ONLY, ct)
          case _ => (Message.Type.RICH_MEDIA, ct)
        }
        (ct.size, ct.head.tpe) match {
          case (1, Message.Part.Type.TEXT) => (Message.Type.TEXT, ct)
          case (1, Message.Part.Type.TEXT_EMOJI_ONLY) => (Message.Type.TEXT_EMOJI_ONLY, ct)
          case _ => (Message.Type.RICH_MEDIA, ct)
        }
      } else {
        // apply links
        def linkEnd(offset: Int) = {
          val end = message.indexWhere(_.isWhitespace, offset + 1)
          if (end < 0) message.length else end
        }

        val res = new MessageContentBuilder

        val end = links.sortBy(_.urlOffset).foldLeft(0) { case (prevEnd, link) =>
          if (link.urlOffset > prevEnd) res ++= RichMediaContentParser.splitContent(message.substring(prevEnd, link.urlOffset), mentions, prevEnd)

          returning(linkEnd(link.urlOffset)) { end =>
            if (end > link.urlOffset) {
              val openGraph = Option(link.getArticle).map { a => OpenGraphData(a.title, a.summary, None, "", Option(a.permanentUrl).filter(_.nonEmpty).map(URI.parse)) }
              res += MessageContent(Message.Part.Type.WEB_LINK, message.substring(link.urlOffset, end), openGraph)
            }
          }
        }

        if (end < message.length) res ++= RichMediaContentParser.splitContent(message.substring(end), mentions, end)

        (Message.Type.RICH_MEDIA, res.result())
      }
    }

  def textContent(message: String): Seq[MessageContent] = Seq(RichMediaContentParser.textMessageContent(message))

  object IsAsset {
    def apply(tpe: Message.Type): Boolean = unapply(tpe)
    def unapply(tpe: Message.Type): Boolean = tpe match {
      case ANY_ASSET | VIDEO_ASSET | AUDIO_ASSET | ASSET => true
      case _ => false
    }
  }

  private val UTF_16_CHARSET  = Charset.forName("UTF-16")

  private def encode(text: String) = {
    val bytes = UTF_16_CHARSET.encode(text).array

    if (bytes.length < 3 || bytes.slice(2, bytes.length).forall(_ == 0))
      Array.empty[Byte]
    else if (bytes(2) == 0)
      bytes.slice(2, bytes.lastIndexWhere(_ > 0) + 1)
    else
      Array[Byte](0) ++ bytes.slice(2, bytes.lastIndexWhere(_ > 0) + 1)
  }

  private def decode(array: Array[Byte]) = UTF_16_CHARSET.decode(ByteBuffer.wrap(array)).toString

  def adjustMentions(text: String, mentions: Seq[Mention], forSending: Boolean, offset: Int = 0): Seq[Mention] = {
    lazy val textAsUTF16 = encode(text) // optimization: textAsUTF16 is used only for incoming mentions

    mentions.foldLeft(List.empty[Mention]) { case (acc, m) =>
      val start = m.start - offset
      val end   = m.start + m.length - offset
      // `encode` computes Array[Byte] with each text character encoded in two bytes,
      // so the length of the text converted to UTF-16 is the array's length / 2.
      val (preLength, handleLength) =
        if (forSending)
          (encode(text.substring(0, start)).length / 2, encode(text.substring(start, end)).length / 2)
        else
          (decode(textAsUTF16.slice(0, start * 2)).length, decode(textAsUTF16.slice(start * 2, end * 2)).length)
      Mention(m.userId, offset + preLength, handleLength) :: acc
    }.sortBy(_.start)
  }


}

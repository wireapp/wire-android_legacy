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

import java.lang.System.currentTimeMillis
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.util.{Date, Locale}

import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.ConversationData.{ConversationType, UnreadCount}
import com.waz.model.GenericContent.{EncryptionAlgorithm, Text}
import com.waz.model.Picture
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.UserInfo.ProfilePicture
import com.waz.model._
import com.waz.model.messages.media._
import com.waz.model.otr.ClientId
import com.waz.model.sync.SyncRequest._
import com.waz.model.sync.{SyncJob, SyncRequest}
import com.waz.service.assets.UploadAssetStatus
import com.waz.service.{SearchKey, SearchQuery}
import com.waz.service.messages.MessageAndLikes
import com.waz.sync.client.AuthenticationManager.AccessToken
import com.waz.sync.client.OpenGraphClient.{OpenGraphData, OpenGraphImage}
import com.waz.utils.Locales.bcp47
import com.waz.utils.sha2
import com.waz.utils.wrappers.URI
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, Gen}
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration.{FiniteDuration, _}

object Generators {
  import MediaAssets._

  lazy val alphaNumStr = listOf(alphaNumChar).map(_.mkString)

  implicit lazy val arbUri: Arbitrary[URI] = Arbitrary(for {
    scheme <- oneOf("file", "content", "http")
    path <- alphaNumStr
  } yield URI.parse(s"$scheme://$path"))

  implicit lazy val arbName = Arbitrary(resultOf((Name)))
  implicit lazy val arbUrl: Arbitrary[URL] = Arbitrary(alphaNumStr.map(s => new URL("http://" + s)))

  implicit lazy val arbConversationData: Arbitrary[ConversationData] = Arbitrary(for {
    id            <- arbitrary[ConvId]
    remoteId      <- arbitrary[RConvId]
    name          <- arbitrary[Option[Name]]
    creator       <- arbitrary[UserId]
    convType      <- arbitrary[ConversationType]
    lastEventTime <- arbitrary[RemoteInstant]
    team          <- arbitrary[Option[TeamId]]
    isActive      <- arbitrary[Boolean]
    muted         <- oneOf(MuteSet.AllMuted, MuteSet.OnlyMentionsAllowed, MuteSet.AllAllowed)
    muteTime      <- arbitrary[RemoteInstant]
    archived      <- arbitrary[Boolean]
    archiveTime   <- arbitrary[RemoteInstant]
    cleared       <- arbitrary[Option[RemoteInstant]]
    generatedName <- arbitrary[Name]
    searchKey     = name.map(SearchKey(_))
    unreadCount   <- arbitrary[UnreadCount]
    failedCount   <- posNum[Int]
    missedCall    <- arbitrary[Option[MessageId]]
    incomingKnock <- arbitrary[Option[MessageId]]
    hidden <- arbitrary[Boolean]
  } yield ConversationData(id, remoteId, name, creator, convType, team, lastEventTime, isActive, RemoteInstant.Epoch, muted, muteTime, archived, archiveTime, cleared, generatedName, searchKey, unreadCount, failedCount, missedCall, incomingKnock, hidden))

  implicit lazy val arbUserData: Arbitrary[UserData] = Arbitrary(for {
    id                    <- arbitrary[UserId]
    teamId                <- arbitrary[Option[TeamId]]
    name                  <- arbitrary[Name]
    email                 <- arbitrary[Option[EmailAddress]]
    phone                 <- arbitrary[Option[PhoneNumber]]
    trackingId            <- arbitrary[Option[TrackingId]]
    picture               <- arbitrary[Option[Picture]]
    accent                <- arbitrary[Int]
    searchKey             = SearchKey(name)
    connection            <- arbitrary[ConnectionStatus]
    connectionLastUpdated <- arbitrary[RemoteInstant]
    connectionMessage     <- arbitrary[Option[String]]
    conversation          <- arbitrary[Option[RConvId]]
    relation              <- arbitrary[Relation]
    syncTimestamp         <- arbitrary[Option[LocalInstant]]
    handle                <- arbitrary[Option[Handle]]
  } yield UserData(id, teamId, name, email, phone, trackingId, picture, accent, searchKey, connection, connectionLastUpdated, connectionMessage, conversation, relation, syncTimestamp, handle = handle))

  implicit lazy val arbOpenGraphDataImage: Arbitrary[OpenGraphImage] = Arbitrary(
    for {
      url <- arbitrary[URL]
    } yield OpenGraphImage(url)
  )
  implicit lazy val arbOpenGraphData: Arbitrary[OpenGraphData] = Arbitrary(resultOf(OpenGraphData))

  implicit lazy val arbMessageContent: Arbitrary[MessageContent] = Arbitrary(resultOf(MessageContent))
  implicit lazy val arbGenericMessage: Arbitrary[GenericMessage] = Arbitrary(for {
    id <- arbitrary[Uid]
    content = Text("test") // TODO: implement actual generator
  } yield GenericMessage(id, content))

  implicit lazy val arbMention: Arbitrary[Mention] = Arbitrary(for {
    id     <- optGen(arbitrary[UserId])
    start  <- posNum[Int]
    length <- chooseNum(1,10)
  } yield Mention(id, start, length))

  implicit lazy val arbQuoteContent: Arbitrary[QuoteContent] = Arbitrary(for {
    message <- arbitrary[MessageId]
    validity <- arbitrary[Boolean]
    hash <- optGen(arbitrary[Sha256])
  } yield QuoteContent(message, validity, hash))

  implicit lazy val arbOptQuoteContent: Arbitrary[Option[QuoteContent]] = Arbitrary(optGen(arbitrary[QuoteContent]))

  implicit lazy val arbMessageData: Arbitrary[MessageData] = Arbitrary(resultOf(MessageData))

  implicit lazy val arbAssetData: Arbitrary[AssetData] = Arbitrary(for {
    id            <- arbitrary[AssetId]
    mime          <- oneOf(testutils.knownMimeTypes)
    sizeInBytes   <- posNum[Long]
    status        <- arbitrary[AssetStatus]
    remoteId      <- optGen(arbitrary[RAssetId])
    token         <- optGen(arbitrary[AssetToken])
    otrKey        <- optGen(arbitrary[AESKey])
    sha           <- optGen(arbitrary[Sha256])
    encryption    <- optGen(oneOf(EncryptionAlgorithm.AES_GCM, EncryptionAlgorithm.AES_CBC))
    name          <- optGen(alphaNumStr)
    previewId     <- optGen(arbitrary[AssetId])
    metaData      <- optGen(arbitrary[AssetMetaData])
    source        <- optGen(arbitrary[URI])
    proxyPath     <- optGen(arbitrary[String])
    convId        <- optGen(arbitrary[RConvId])
    data <- optGen(arbitrary[Array[Byte]])
  } yield AssetData(id, mime, sizeInBytes, status, remoteId, token, otrKey, sha, encryption, name, previewId, metaData, source, proxyPath, convId, data))


  implicit lazy val arbAssetStatus: Arbitrary[AssetStatus] = Arbitrary(frequency((2, oneOf[AssetStatus](AssetStatus.UploadNotStarted,
    AssetStatus.UploadInProgress, AssetStatus.UploadCancelled, AssetStatus.UploadFailed, AssetStatus.UploadDone, AssetStatus.DownloadFailed))))
  implicit lazy val arbSyncableAssetStatus: Arbitrary[AssetStatus.Syncable] = Arbitrary(oneOf(AssetStatus.UploadCancelled, AssetStatus.UploadFailed))
  implicit lazy val arbUploadAssetStatus: Arbitrary[UploadAssetStatus] = Arbitrary(oneOf(UploadAssetStatus.Cancelled, UploadAssetStatus.InProgress))
  implicit lazy val arbAssetToken: Arbitrary[AssetToken] = Arbitrary(resultOf(AssetToken))
  implicit lazy val arbOtrKey: Arbitrary[AESKey] = Arbitrary(sideEffect(AESKey()))
  implicit lazy val arbSha256: Arbitrary[Sha256] = Arbitrary(arbitrary[Array[Byte]].map(b => Sha256(sha2(b))))
  implicit lazy val arbUnreadCount: Arbitrary[UnreadCount] = Arbitrary(for (n <- chooseNum(0,1000); c <- chooseNum(0,1000); p <- chooseNum(0,1000); m <- chooseNum(0,1000); q <- chooseNum(0, 1000)) yield UnreadCount(n, c, p, m, q))

  object MediaAssets {
    implicit lazy val arbArtistData: Arbitrary[ArtistData] = Arbitrary(resultOf(ArtistData))
    implicit lazy val arbTrackData: Arbitrary[TrackData] = Arbitrary(resultOf(TrackData))
    implicit lazy val arbPlaylistData: Arbitrary[PlaylistData] = Arbitrary(resultOf(PlaylistData))
    implicit lazy val arbEmptyMediaAssetData: Arbitrary[EmptyMediaAssetData] = Arbitrary(resultOf(EmptyMediaAssetData))

    implicit lazy val arbMediaAssetData: Arbitrary[MediaAssetData] = Arbitrary(oneOf(arbitrary[TrackData], arbitrary[PlaylistData], arbitrary[EmptyMediaAssetData]))
  }

  object SyncRequests {
    implicit lazy val arbSyncJob: Arbitrary[SyncJob] = Arbitrary(for {
      id <- arbitrary[SyncId]
      req <- arbitrary[SyncRequest]
    } yield SyncJob(id, req))

    implicit lazy val arbSyncRequest: Arbitrary[SyncRequest] = Arbitrary(oneOf(
      arbSimpleSyncRequest.arbitrary,
      arbitrary[RequestForUser],
      arbitrary[RequestForConversation],
      arbitrary[SyncUser],
      arbitrary[SyncSearchResults],
      arbitrary[SyncConversation],
      arbitrary[SyncSearchQuery],
      arbitrary[SyncRichMedia],
      arbitrary[PostSelf],
      arbitrary[PostSelfPicture],
      arbitrary[PostConvJoin],
      arbitrary[PostConvLeave],
      arbitrary[DeletePushToken],
      arbitrary[SyncPreKeys]))

    implicit lazy val arbUserBasedSyncRequest: Arbitrary[RequestForUser] = Arbitrary(oneOf(
      arbitrary[PostConnection],
      arbitrary[PostConnectionStatus]))

    implicit lazy val arbConvBasedSyncRequest: Arbitrary[RequestForConversation] = Arbitrary(oneOf(
      arbitrary[PostConv],
      arbitrary[PostConvName],
      arbitrary[PostConvState],
      arbitrary[PostLastRead],
      arbitrary[PostTypingState],
      arbitrary[PostMessage],
      arbitrary[PostDeleted],
      arbitrary[PostLiking],
      arbitrary[PostAssetStatus]))

    lazy val arbSimpleSyncRequest: Arbitrary[SyncRequest] = Arbitrary(oneOf(SyncSelf, DeleteAccount, SyncConversations, SyncConnections))

    implicit lazy val arbUsersSyncRequest: Arbitrary[SyncUser] = Arbitrary(listOf(arbitrary[UserId]) map { u => SyncUser(u.toSet) })
    implicit lazy val arbSearchResultSyncRequest: Arbitrary[SyncSearchResults] = Arbitrary(listOf(arbitrary[UserId]) map { u => SyncSearchResults(u.toSet) })
    implicit lazy val arbConvsSyncRequest: Arbitrary[SyncConversation] = Arbitrary(listOf(arbitrary[ConvId]) map { c => SyncConversation(c.toSet) })
    implicit lazy val arbSearchSyncRequest: Arbitrary[SyncSearchQuery] = Arbitrary(resultOf(SyncSearchQuery))
    implicit lazy val arbSelfPictureSyncRequest: Arbitrary[PostSelfPicture] = Arbitrary(resultOf(PostSelfPicture))
    implicit lazy val arbRichMediaSyncRequest: Arbitrary[SyncRichMedia] = Arbitrary(resultOf(SyncRichMedia))
    implicit lazy val arbGcmSyncRequest: Arbitrary[DeletePushToken] = Arbitrary(resultOf(DeletePushToken))
    implicit lazy val arbPostConvSyncRequest: Arbitrary[PostConv] = Arbitrary(resultOf(PostConv))
    implicit lazy val arbPostLastReadRequest: Arbitrary[PostLastRead] = Arbitrary(resultOf(PostLastRead))
    implicit lazy val arbPostConvNameSyncRequest: Arbitrary[PostConvName] = Arbitrary(resultOf(PostConvName))
    implicit lazy val arbPostSelfSyncRequest: Arbitrary[PostSelf] = Arbitrary(resultOf(PostSelf))
    implicit lazy val arbPostConvStateSyncRequest: Arbitrary[PostConvState] = Arbitrary(resultOf(PostConvState))
    implicit lazy val arbPostTypingStateSyncRequest: Arbitrary[PostTypingState] = Arbitrary(resultOf(PostTypingState))
    implicit lazy val arbPostConnectionStatusSyncRequest: Arbitrary[PostConnectionStatus] = Arbitrary(resultOf(PostConnectionStatus))
    implicit lazy val arbMessageSyncRequest: Arbitrary[PostMessage] = Arbitrary(resultOf(PostMessage))
    implicit lazy val arbMessageDelSyncRequest: Arbitrary[PostDeleted] = Arbitrary(resultOf(PostDeleted))
    implicit lazy val arbPostConvJoinSyncRequest: Arbitrary[PostConvJoin] = Arbitrary(resultOf(PostConvJoin))
    implicit lazy val arbPostConvLeaveSyncRequest: Arbitrary[PostConvLeave] = Arbitrary(resultOf(PostConvLeave))
    implicit lazy val arbConnectionSyncRequest: Arbitrary[PostConnection] = Arbitrary(resultOf(PostConnection))
    implicit lazy val arbPostLiking: Arbitrary[PostLiking] = Arbitrary(resultOf(PostLiking))
    implicit lazy val arbSyncPreKey: Arbitrary[SyncPreKeys] = Arbitrary(resultOf(SyncPreKeys))
    implicit lazy val arbPostAssetStatus: Arbitrary[PostAssetStatus] = Arbitrary(resultOf(PostAssetStatus))
    implicit lazy val arbPostReceipt: Arbitrary[PostReceipt] = Arbitrary(resultOf(PostReceipt))
  }

  implicit lazy val arbUid: Arbitrary[Uid]               = Arbitrary(sideEffect(Uid()))
  implicit lazy val arbConvId: Arbitrary[ConvId]         = Arbitrary(sideEffect(ConvId()))
  implicit lazy val arbRConvId: Arbitrary[RConvId]       = Arbitrary(sideEffect(RConvId()))
  implicit lazy val arbUserId: Arbitrary[UserId]         = Arbitrary(sideEffect(UserId()))
  implicit lazy val arbTeamId: Arbitrary[TeamId]         = Arbitrary(sideEffect(TeamId()))
  implicit lazy val arbRAssetDataId: Arbitrary[RAssetId] = Arbitrary(sideEffect(RAssetId()))
  implicit lazy val arbAssetIdGeneral: Arbitrary[GeneralAssetId] = Arbitrary(sideEffect(AssetId()))
  implicit lazy val arbAssetId: Arbitrary[AssetId]       = Arbitrary(sideEffect(AssetId()))
  implicit lazy val arbUploadAssetId: Arbitrary[UploadAssetId]       = Arbitrary(sideEffect(UploadAssetId()))
  implicit lazy val arbPublicAssetId: Arbitrary[Picture]       = Arbitrary(sideEffect(PictureUploaded(AssetId())))
  implicit lazy val arbSyncId: Arbitrary[SyncId]         = Arbitrary(sideEffect(SyncId()))
  implicit lazy val arbGcmId: Arbitrary[PushToken]           = Arbitrary(sideEffect(PushToken()))
  implicit lazy val arbMessageId: Arbitrary[MessageId]   = Arbitrary(sideEffect(MessageId()))
  implicit lazy val arbTrackingId: Arbitrary[TrackingId] = Arbitrary(sideEffect(TrackingId()))
  implicit lazy val arbCallSessionId: Arbitrary[CallSessionId] = Arbitrary(sideEffect(CallSessionId()))
  implicit lazy val arbClientId: Arbitrary[ClientId] = Arbitrary(sideEffect(ClientId()))

  implicit lazy val arbDate: Arbitrary[Date] = Arbitrary(choose(0L, 999999L).map(i => new Date(currentTimeMillis - 1000000000L + i * 1000L)))
  implicit lazy val arbInstant: Arbitrary[Instant] = Arbitrary(posNum[Long] map Instant.ofEpochMilli)
  implicit lazy val arbRemoteInstant: Arbitrary[RemoteInstant] = Arbitrary(posNum[Long] map RemoteInstant.ofEpochMilli)
  implicit lazy val arbLocalInstant: Arbitrary[LocalInstant] = Arbitrary(posNum[Long] map LocalInstant.ofEpochMilli)
  implicit lazy val arbDuration: Arbitrary[Duration] = Arbitrary(posNum[Long] map Duration.ofMillis)
  implicit lazy val arbFiniteDuration: Arbitrary[FiniteDuration] = Arbitrary(posNum[Long] map(_.millis))

  implicit lazy val arbLiking: Arbitrary[Liking] = Arbitrary(resultOf(Liking.apply _))
  implicit lazy val arbLikingAction: Arbitrary[Liking.Action] = Arbitrary(oneOf(Liking.Action.values.toSeq))
  implicit lazy val arbMessageAndLikes: Arbitrary[MessageAndLikes] = Arbitrary(for {
    msg <- arbitrary[MessageData]
    self <- arbitrary[UserId]
    others <- listOf(arbitrary[UserId])
    includeSelf <- frequency((4, false), (1, true))
    ids = if (includeSelf) self :: others else others
    quote <- optGen(arbitrary[MessageData])
  } yield MessageAndLikes(msg, ids.toVector, includeSelf, quote))

  implicit lazy val arbMetaData: Arbitrary[AssetMetaData] = Arbitrary(oneOf(arbImageMetaData.arbitrary, arbVideoMetaData.arbitrary, arbAudioMetaData.arbitrary))
  implicit lazy val arbImageMetaData: Arbitrary[AssetMetaData.Image] = Arbitrary(for (d <- arbitrary[Dim2]; t <- oneOf(Medium, Preview)) yield AssetMetaData.Image(d, t))
  implicit lazy val arbVideoMetaData: Arbitrary[AssetMetaData.Video] = Arbitrary(resultOf(AssetMetaData.Video(_: Dim2, _: Duration)))
  implicit lazy val arbAudioMetaData: Arbitrary[AssetMetaData.Audio] = Arbitrary(resultOf(AssetMetaData.Audio(_: Duration)))
  implicit lazy val arbDim2: Arbitrary[Dim2] = Arbitrary(for (w <- genDimension; h <- genDimension) yield Dim2(w, h))
  lazy val genDimension = chooseNum(0, 10000)
  implicit lazy val arbSearchQuery: Arbitrary[SearchQuery] = Arbitrary(for (s <- arbitrary[String]; b <- arbitrary[Boolean]) yield SearchQuery(s, b))
  implicit lazy val arbTag: Arbitrary[AssetMetaData.Image.Tag] = Arbitrary(oneOf(AssetMetaData.Image.Tag.Preview, AssetMetaData.Image.Tag.Medium, AssetMetaData.Image.Tag.Empty))
  implicit lazy val profilePicture: Arbitrary[ProfilePicture] = Arbitrary(resultOf(ProfilePicture))

  implicit def optGen[T](implicit gen: Gen[T]): Gen[Option[T]] = frequency((1, Gen.const(None)), (2, gen.map(Some(_))))

  implicit lazy val arbUserInfo: Arbitrary[UserInfo] = Arbitrary(for {
    userId <- arbitrary[UserId]
    name <- optGen(alphaNumStr)
    email <- arbitrary[Option[EmailAddress]]
    phone <- arbitrary[Option[PhoneNumber]]
    picture <- arbitrary[Option[ProfilePicture]]
    trackingId <- arbitrary[Option[TrackingId]]
    accent <- arbitrary[Option[Int]]
  } yield UserInfo(userId, name.map(Name), accent, email, phone, Some(picture.toSeq), trackingId))

  implicit lazy val arbContactData: Arbitrary[AddressBook.ContactHashes] = Arbitrary(for {
    id     <- arbitrary[String] map sha2
    hashes <- arbitrary[Set[String]] map (_ map sha2)
  } yield AddressBook.ContactHashes(ContactId(id), hashes))

  implicit lazy val arbConvState: Arbitrary[ConversationState] = Arbitrary(resultOf(
    ConversationState(_: Option[Boolean], _: Option[RemoteInstant], _: Option[Boolean], _: Option[RemoteInstant], _: Option[Int])))

  lazy val serialCounter: AtomicLong = new AtomicLong()

  implicit lazy val arbToken: Arbitrary[AccessToken] = Arbitrary(resultOf(AccessToken))

  implicit lazy val arbEmailAddress: Arbitrary[EmailAddress] = Arbitrary(resultOf(EmailAddress))
  implicit lazy val arbPhoneNumber: Arbitrary[PhoneNumber] = Arbitrary(resultOf(PhoneNumber))
  implicit lazy val ArbLocale: Arbitrary[Locale] = Arbitrary(oneOf(availableLocales))

  implicit lazy val arbHandle: Arbitrary[Option[Handle]] = Arbitrary(sideEffect(Some(Handle.random)))

  def sideEffect[A](f: => A): Gen[A] = resultOf[Unit, A](_ => f)

  lazy val availableLocales: Vector[Locale] = Locale.getAvailableLocales.flatMap(l => bcp47.localeFor(bcp47.languageTagOf(l))).distinct.toVector

  implicit lazy val arbConversationRole: Arbitrary[ConversationRole] = Arbitrary(oneOf(ConversationRole.AdminRole, ConversationRole.MemberRole))
}

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
package com.waz.log

import java.io.File
import java.net.URI

import android.net.Uri
import com.waz.api.impl.ErrorResponse
import com.waz.api.{MessageContent => _, _}
import com.waz.content.Preferences.PrefKey
import com.waz.log.LogSE._
import com.waz.model.AccountData.Password
import com.waz.model.ManagedBy.ManagedBy
import com.waz.model._
import com.waz.model.messages.media.{ArtistData, TrackData}
import com.waz.model.otr.{Client, UserClients}
import com.waz.model.sync.{ReceiptType, SyncCommand, SyncJob, SyncRequest}
import com.waz.service.assets.GlobalRecordAndPlayService._
import com.waz.service.assets.{Asset => Asset2, _}
import com.waz.service.call.Avs.AvsClosedReason.reasonString
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.{PropertyKey, SearchResults, assets}
import com.waz.sync.SyncResult
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.sync.client.TeamsClient.TeamMember
import com.waz.utils.{sha2, wrappers}
import org.threeten.bp
import org.threeten.bp.Instant

import scala.concurrent.duration.{Duration, FiniteDuration}

trait LogShowInstancesSE {
  import LogShow._

  implicit val RemoteInstantShow: LogShow[RemoteInstant] = logShowWithToString
  implicit val LocalInstantShow: LogShow[LocalInstant] = logShowWithToString
  implicit val InstantShow: LogShow[Instant] = logShowWithToString
  implicit val DurationShow: LogShow[Duration] = logShowWithToString
  implicit val FiniteDurationShow: LogShow[FiniteDuration] = logShowWithToString
  implicit val BPDurationShow: LogShow[bp.Duration] = logShowWithToString
  implicit val Sha256LogShow: LogShow[Sha256] = create(_.hexString, _.str)

  //TODO how much of a file/uri can we show in prod?
  // There might be UUIDs in the URL, so we should obfuscate them.

  //common types
  implicit val FileLogShow: LogShow[File] = create(_ => "<file>", _.getAbsolutePath)
  implicit val AUriLogShow: LogShow[Uri]  = create(_ => "<uri>", _.toString)
  implicit val UriLogShow:  LogShow[URI]  = create(_ => "<uri>", _.toString)
  implicit val WUriLogShow: LogShow[wrappers.URI] = create(_ => "<uri>", _.toString)

  implicit val PasswordShow: LogShow[Password] = create(_ => "********") //Also don't show in debug mode (e.g. Internal)

  implicit val PrefKeyLogShow: LogShow[PrefKey[_]]      = logShowWithToString
  implicit val PropertyKeyLogShow: LogShow[PropertyKey] = logShowWithToString
  implicit val ReadReceiptSettingsShow: LogShow[ReadReceiptSettings] = logShowWithToString

  implicit val SSOIdShow: LogShow[SSOId] = create(id => s"SSOId(subject: ${sha2(id.subject)}, tenant:${sha2(id.tenant)})")
  implicit val ManagedByShow: LogShow[ManagedBy] = create(id => s"ManagedBy($id)")

  implicit val CredentialsLogShow: LogShow[Credentials] =
    createFrom {
      case EmailCredentials(email, password, code) => l"EmailCredentials($email, $password, $code)"
      case PhoneCredentials(phone, code)           => l"PhoneCredentials($phone, $code)"
      case HandleCredentials(handle, password)     => l"HandleCredentials($handle, $password)"
    }

  implicit val EncryptionShow: LogShow[Encryption] =
    LogShow.createFrom {
      case NoEncryption => l"NoEncryption"
      case AES_CBC_Encryption(key) => l"AES_CBC_Encryption($key)"
    }

  implicit val CookieShow: LogShow[Cookie] = create(
    c => s"Cookie(exp: ${c.expiry}, isValid: ${c.isValid})",
    c => s"Cookie(${c.str}, exp: ${c.expiry}, isValid: ${c.isValid})"
  )

  implicit val AccessTokenShow: LogShow[AccessToken] = create(
    t => s"AccessToken(${t.accessToken.take(10)}, exp: ${t.expiresAt}, isValid: ${t.isValid})",
    t => s"AccessToken(${t.accessToken}, exp: ${t.expiresAt}, isValid: ${t.isValid})"
  )

  implicit val ClientLogShow: LogShow[Client] = createFrom { c =>
    import c._
    l"Client(id: $id | regTime: $regTime | verified: $verified)"
  }

  implicit val UserClientsLogShow: LogShow[UserClients] = createFrom { c =>
    import c._
    l"UserClients(user: $user | clients: ${clients.values})"
  }

  implicit val SessionIdLogShow: LogShow[SessionId] = createFrom { id =>
    import id._
    l"SessionId(userId: $userId | domain: $domain | client: $clientId)"
  }

  implicit val MessageDataLogShow: LogShow[MessageData] =
    LogShow.createFrom { m =>
      import m._
      l"MessageData(id: $id | convId: $convId | msgType: $msgType | userId: $userId | state: $state | time: $time | localTime: $localTime)"
    }

  implicit val MessageContentLogShow: LogShow[MessageContent] =
    LogShow.createFrom { m =>
      import m._
      l"MessageContent(tpe: $tpe | asset: $asset | width: $width | height $height | syncNeeded $syncNeeded | mentions: $mentions)"
    }

  implicit val UserDataLogShow: LogShow[UserData] =
    LogShow.createFrom { u =>
      import u._
      l"UserData(id: $id | teamId: $teamId | name: $name | email: $email | phone: $phone | handle: $handle | deleted: $deleted)"
    }

  implicit val UserInfoLogShow: LogShow[UserInfo] =
    LogShow.createFrom { u =>
      import u._
      l"UserInfo: id: $id | email: $email: | phone: $phone: | picture: $picture: | deleted: $deleted: | handle: $handle: | expiresAt: $expiresAt: | ssoId: $ssoId | managedBy: $managedBy"
    }

  implicit val ConvDataLogShow: LogShow[ConversationData] =
    LogShow.createFrom { c =>
      import c._
      l"ConversationData(id: $id | remoteId: $remoteId | name: $name | convType: $convType | team: $team | lastEventTime: $lastEventTime | muted: $muted | muteTime: $muteTime | archived: $archived | archivedTime: $archiveTime | lastRead: $lastRead | cleared: $cleared | unreadCount: $unreadCount)"
    }

  implicit val MentionShow: LogShow[Mention] =
    createFrom { m =>
      import m._
      l"Mention(userId: $userId, start: $start, length: $length)"
    }

  implicit val AssetLogShow: LogShow[Asset2] =
    LogShow.createFrom { a =>
      import a._
      l"Asset(id: $id | token: $token | sha: $sha | encryption: $encryption | localSource: $localSource | preview: $preview | details: $details)"
    }

  implicit val UploadAssetLogShow: LogShow[UploadAsset] =
    LogShow.createFrom { a =>
      import a._
      l"UploadAsset(id: $id)"
    }

  implicit val DownloadAssetLogShow: LogShow[DownloadAsset] =
    LogShow.createFrom { a =>
      import a._
      l"DownloadAsset(id: $id)"
    }

  implicit val AssetDataLogShow: LogShow[AssetData] =
    LogShow.createFrom { c =>
      import c._
      l"""
         |AssetData(id: $id | mime: $mime | sizeInBytes: $sizeInBytes | status: $status | source: $source
         |  rId: $remoteId | token: $token | otrKey: $otrKey | preview: $previewId)
        """.stripMargin
    }

  implicit val AssetStatusLogShow: LogShow[assets.AssetStatus] =
    LogShow.createFrom { s => l"AssetStatus(${showString(s.getClass.getName)})" }

  implicit val TrackDataLogShow: LogShow[TrackData] =
    LogShow.createFrom { d =>
      import d._
      l"""
         |TrackData(
         | provider: $provider,
         | title: ${redactedString(title)},
         | artist: $artist,
         | linkUrl: ${new URI(linkUrl)},
         | artwork: $artwork,
         | duration: $duration,
         | streamable: $streamable,
         | streamUrl: ${streamUrl.map(new URI(_))},
         | previewUrl: ${previewUrl.map(new URI(_))},
         | expires: $expires)
          """.stripMargin
    }

  implicit val ArtistDataLogShow: LogShow[ArtistData] =
    LogShow.createFrom { d =>
      l"ArtistData(name: ${redactedString(d.name)}, avatar: ${d.avatar})"
    }

  implicit val NotificationDataLogShow: LogShow[NotificationData] =
    LogShow.createFrom { n =>
      import n._
      l"NotificationData(id: $id | conv: $conv | user: $user | msgType: $msgType | time: $time | isReply: $isReply | isSelfMentioned: $isSelfMentioned)"
    }

  implicit val TeamDataLogShow: LogShow[TeamData] =
    LogShow.createFrom { n =>
      import n._
      l"""
         |TeamData(id: $id | name: $name | creator: $creator)
        """.stripMargin
    }

  implicit val TeamMemberLogShow: LogShow[TeamMember] =
    LogShow.create(tm => s"TeamMember with permissions: ${tm.permissions}")

  implicit val VideoStateLogShow: LogShow[VideoState] = logShowWithToString

  implicit val CallInfoLogShow: LogShow[CallInfo] =
    LogShow.createFrom { n =>
      import n._
      l"""
         |CallInfo(account: ${selfParticipant.qualifiedId.id} | clientId: ${selfParticipant.clientId} | convId: $convId | caller: $caller | state: $state | prevState: $prevState | isCbrEnabled: $isCbrEnabled
         |  isGroup: $isGroup | shouldRing: $shouldRing |  muted: $muted | startedAsVideoCall: $startedAsVideoCall | videoSendState: $videoSendState
         |  allParticipants: $allParticipants | maxParticipants: $maxParticipants |
         |  startTime: $startTime | joinedTime: $joinedTime | estabTime: $estabTime | endTime: $endTime
         |  endReason: ${endReason.map(r => showString(reasonString(r)))} | wasVideoToggled: $wasVideoToggled | hasOutstandingMsg: ${outstandingMsg.isDefined})
        """.stripMargin
    }

  implicit val AccountDataLogShow: LogShow[AccountData] =
    LogShow.createFrom { u =>
      import u._
      l"""AccountData(
         | id: $id
         | teamId: $teamId
         | cookie: $cookie
         | accessToken: $accessToken
         | pushToken: $pushToken
         | password: $password
         | ssoId: $ssoId)"""
    }

  implicit val ErrorResponseLogShow: LogShow[ErrorResponse] = LogShow.create(_.toString)

  // Global Record and Play Service

  implicit val StateLogShow: LogShow[GlobalRecordAndPlayService.State] =
    LogShow.createFrom {
      case Idle => l"Idle"
      case Playing(player, key) => l"Playing(player: $player, key: $key)"
      case Paused(player, key, playhead, transient) => l"Paused(player: $player, key: $key, playhead: $playhead, transient: $transient)"
      case Recording(_, key, start, entry, _, _) => l"Recording(key: $key, start: $start, entry: $entry)"
    }

  implicit val PlayerLogShow: LogShow[Player] = LogShow.create(_.getClass.getName)

  implicit val MediaKeyLogShow: LogShow[GlobalRecordAndPlayService.MediaKey] =
    LogShow.createFrom {
      case AssetMediaKey(id) => l"AssetMediaKey(id: $id)"
      case UriMediaKey(uri) => l"UriMediaKey(uri: $uri)"
    }

  implicit val ErrorLogShow: LogShow[GlobalRecordAndPlayService.Error] =
    LogShow.create { e =>
      s"Error(message: ${e.message})"
    }

  implicit val MediaPointerLogShow: LogShow[GlobalRecordAndPlayService.MediaPointer] =
    LogShow.createFrom { p =>
      l"MediaPointer(content: ${p.content}, playhead: ${p.playhead})"
    }

  implicit val ContentLogShow: LogShow[GlobalRecordAndPlayService.Content] =
    LogShow.createFrom {
      case UnauthenticatedContent(uri) => l"UnauthenticatedContent(uri: $uri)"
      case PCMContent(file) => l"PCMContent(file: $file)"
    }

  // Sync Job

  implicit val SyncJobLogShow: LogShow[SyncJob] =
    LogShow.createFrom { j =>
      import j._
      l"""SyncJob(
         | id: $id
         | request: $request
         | dependsOn: $dependsOn
         | priority: $priority
         | timestamp: $timestamp
         | startTime: $startTime
         | attempts: $attempts
         | offline: $offline
         | state: $state
         | error: ${j.error})""".stripMargin
    }

  implicit val SyncRequestLogShow: LogShow[SyncRequest] =
    LogShow.createFrom { r =>
      l"SyncRequest(cmd: ${r.cmd})"
    }

  implicit val SyncResultLogShow: LogShow[SyncResult] =
    LogShow.createFrom {
      case SyncResult.Success => l"Success"
      case SyncResult.Failure(error) => l"Failure(error: $error)"
      case SyncResult.Retry(error) => l"Retry(error: $error)"
    }

  implicit val SyncCommandLogShow: LogShow[SyncCommand] = LogShow.create(_.name())
  implicit val SyncStateLogShow: LogShow[SyncState] = LogShow.create(_.name())

  //Events
  implicit val OtrErrorLogShow: LogShow[OtrError] =
    LogShow.createFrom {
      case Duplicate => l"Duplicate"
      case DecryptionError(msg, code, from, sender) => l"DecryptionError(msg: ${showString(msg)} | code: $code | from: $from | sender: $sender)"
      case IdentityChangedError(from, sender) => l"IdentityChangedError(from: $from | sender: $sender)"
      case UnknownOtrErrorEvent(json) => l"UnknownOtrErrorEvent(json: $json)"
    }

  implicit val OtrErrorEventLogShow: LogShow[OtrErrorEvent] =
    LogShow.createFrom { e =>
      import e._
      l"OtrErrorEvent(convId: $convId | time: $time | from: $from | error: ${e.error})"
    }

  implicit val SessionResetLogShow: LogShow[SessionReset] =
    LogShow.createFrom { sr =>
      import sr._
      l"SessionReset(convId: $convId | time: $time | from: $from | sender: $sender)"
    }

  //Protos
  implicit val GenericMessageLogShow: LogShow[GenericMessage] = LogShow.create { m =>
    m.proto.getContentCase
    s"GenericMessage(messageId: ${sha2(m.proto.getMessageId)} | contentCase: ${m.proto.getContentCase})"
  }

  implicit val ReadReceiptShow: LogShow[ReadReceipt] = LogShow.createFrom { r =>
    import r._
    l"ReadReceipt($message, $user, $timestamp)"
  }

  implicit val ReceiptType: LogShow[ReceiptType] = logShowWithToString

  // System Types

  implicit val ThreadLogShow: LogShow[Thread] =
    LogShow.create { t =>
      import t._
      s"Thread(id: $getId, name: $getName, priority: $getPriority, state: $getState)"
    }

  implicit val TypeFilterLogShow: LogShow[TypeFilter] =
    LogShow.createFrom { f =>
      l"TypeFilter(msgType: ${f.msgType}, limit: ${f.limit}"
    }

  implicit val SearchResultsLogShow: LogShow[SearchResults] =
    LogShow.createFrom { r =>
      l"SearchResults(top: ${r.top.size}, local: ${r.local.size}, convs: ${r.convs.size}, dir: ${r.dir.size})"
    }

  implicit val IntegrationDataLogShow: LogShow[IntegrationData] =
    LogShow.createFrom { d =>
      l"IntegrationData(id: ${d.id}, provider: ${d.provider}, name: ${redactedString(d.name)}, asset: ${d.asset}, enabled: ${d.enabled}"
    }

}

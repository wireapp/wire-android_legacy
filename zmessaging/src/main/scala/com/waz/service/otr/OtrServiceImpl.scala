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
package com.waz.service.otr

import java.io._

import com.waz.cache.{CacheService, LocalData}
import com.waz.content.{GlobalPreferences, MembersStorage, OtrClientsStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.Asset.{AES_CBC, EncryptionAlgorithm}
import com.waz.model._
import com.waz.model.otr._
import com.waz.service._
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.OtrClient
import com.waz.sync.client.OtrClient.{EncryptedContent, QEncryptedContent}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.crypto.AESUtils
import com.waz.zms.BuildConfig
import com.wire.signals.Signal
import javax.crypto.Mac

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

trait OtrService {
  def resetSession(convId: ConvId, userId: UserId, clientId: ClientId): Future[SyncId]
  def resetSession(convId: ConvId, qId: QualifiedId, clientId: ClientId): Future[SyncId]

  def encryptTargetedMessage(userId: UserId, clientId: ClientId, msg: GenericMessage): Future[Option[OtrClient.EncryptedContent]]
  def encryptTargetedMessage(qId: QualifiedId, clientId: ClientId, msg: GenericMessage): Future[Option[OtrClient.QEncryptedContent]]

  def deleteClients(userMap: OtrClientIdMap): Future[Unit]
  def deleteClients(userMap: QOtrClientIdMap): Future[Unit]

  def encryptMessageForUsers(message:        GenericMessage,
                             users:          Set[UserId],
                             partialResult:  EncryptedContent,
                             useFakeOnError: Boolean = false): Future[EncryptedContent]

  def encryptMessage(message:         GenericMessage,
                     recipients:      OtrClientIdMap,
                     userFakeOnError: Boolean,
                     partialResult:   EncryptedContent): Future[EncryptedContent]
  def encryptMessage(message:         GenericMessage,
                     recipients:      QOtrClientIdMap,
                     userFakeOnError: Boolean,
                     partialResult:   QEncryptedContent): Future[QEncryptedContent]

  def encryptAssetData(key: AESKey, data: LocalData):Future[(Sha256, LocalData, EncryptionAlgorithm)]

  def decryptAssetData(assetId:    AssetId,
                       otrKey:     Option[AESKey],
                       sha:        Option[Sha256],
                       data:       Option[Array[Byte]]): Option[Array[Byte]]
}

class OtrServiceImpl(selfUserId:     UserId,
                     currentDomain:  Domain,
                     clientId:       ClientId,
                     clients:        OtrClientsService,
                     cryptoBox:      CryptoBoxService,
                     users:          => UserService, // lazy, bcs otherwise we'd have a circular dependency
                     members:        MembersStorage,
                     sync:           SyncServiceHandle,
                     cache:          CacheService,
                     metadata:       MetaDataService,
                     clientsStorage: OtrClientsStorage,
                     prefs:          GlobalPreferences,
                     tracking:       TrackingService) extends OtrService with DerivedLogTag {
  import OtrService._
  import Threading.Implicits.Background

  private lazy val sessions: CryptoSessionService = returning(cryptoBox.sessions) { sessions =>
    // request self clients sync to update prekeys on backend
    // we've just created a session from message, this means that some user had to obtain our prekey from backend (so we can upload it)
    // using signal and sync interval parameter to limit requests to one an hour
    Signal.from(sessions.onCreateFromMessage).throttle(15.seconds).foreach { _ => clients.requestSyncIfNeeded(1.hour) }
  }

  override def resetSession(convId: ConvId, userId: UserId, clientId: ClientId): Future[SyncId] =
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      for {
        qId    <- users.qualifiedId(userId)
        syncId <- resetSession(convId, qId, clientId)
      } yield syncId
    } else {
      for {
        _      <- sessions.deleteSession(SessionId(userId, Domain.Empty, clientId)).recover { case _ => () }
        _      <- clientsStorage.updateVerified(userId, clientId, verified = false)
        _      <- sync.syncPreKeys(QualifiedId(userId), Set(clientId))
        syncId <- sync.postSessionReset(convId, userId, clientId)
      } yield syncId
    }

  override def resetSession(convId: ConvId, qId: QualifiedId, clientId: ClientId): Future[SyncId] =
    for {
      _      <- sessions.deleteSession(SessionId(qId, clientId, currentDomain)).recover { case _ => () }
      _      <- clientsStorage.updateVerified(qId.id, clientId, verified = false)
      _      <- sync.syncPreKeys(qId, Set(clientId))
      syncId <- sync.postSessionReset(convId, qId, clientId)
    } yield syncId

  override def encryptTargetedMessage(userId: UserId, clientId: ClientId, msg: GenericMessage): Future[Option[OtrClient.EncryptedContent]] =
    users.qualifiedId(userId).flatMap { qId =>
      sessions.withSession(SessionId(qId, clientId, currentDomain)) { session =>
        EncryptedContent(Map(userId -> Map(clientId -> session.encrypt(msg.toByteArray))))
      }
    }

  override def encryptTargetedMessage(qId: QualifiedId, clientId: ClientId, msg: GenericMessage): Future[Option[OtrClient.QEncryptedContent]] =
    sessions.withSession(SessionId(qId, clientId, currentDomain)) { session =>
      QEncryptedContent(Map(qId -> Map(clientId -> session.encrypt(msg.toByteArray))))
    }

  /**
    * @param message the message to be encrypted
    * @param users the users whose clients should be recipients of the message
    * @param partialResult partial content encrypted in previous run, we will use that instead of encrypting again when available
    * @param useFakeOnError when true, we will return bomb emoji as msg content on encryption errors (for failing client)
    */
  override def encryptMessageForUsers(message:        GenericMessage,
                                      users:          Set[UserId],
                                      partialResult:  EncryptedContent,
                                      useFakeOnError: Boolean = false): Future[EncryptedContent] = {

    for {
      recipients       <- clientsMap(users)
      encryptedContent <- encryptMessage(message, recipients, useFakeOnError, partialResult)
    } yield encryptedContent
  }

  private def clientsMap(userIds: Set[UserId]): Future[OtrClientIdMap] =
    Future.traverse(userIds) { userId =>
      getClients(userId).map { clientIds =>
        userId -> clientIds
      }
    }.map(OtrClientIdMap(_))

  private def getClients(userId: UserId): Future[Set[ClientId]] =
    clientsStorage.getClients(userId).map { clients =>
      val clientIds = clients.map(_.id).toSet
      if (userId == selfUserId) clientIds.filter(_ != clientId) else clientIds
    }

  /**
    * @param message the message to be encrypted
    * @param recipients the precise recipients who should receive the message
    * @param useFakeOnError when true, we will return bomb emoji as msg content on encryption errors (for failing client)
    * @param partialResult partial content encrypted in previous run, we will use that instead of encrypting again when available
    */
  override def encryptMessage(message:         GenericMessage,
                              recipients:      OtrClientIdMap,
                              useFakeOnError:  Boolean,
                              partialResult:   EncryptedContent): Future[EncryptedContent] = {

    val msgData = message.toByteArray
    for {
      payloads <- Future.traverse(recipients.entries) { case (userId, clientIds) =>
                    val partialResultForUser = partialResult.content.getOrElse(userId, Map.empty)
                    encryptForClients(userId, clientIds, msgData, useFakeOnError, partialResultForUser)
                  }
      content   = payloads.filter(_._2.nonEmpty).toMap
    } yield EncryptedContent(content)
  }

  /**
   * @param message the message to be encrypted
   * @param recipients the precise recipients who should receive the message
   * @param useFakeOnError when true, we will return bomb emoji as msg content on encryption errors (for failing client)
   * @param partialResult partial content encrypted in previous run, we will use that instead of encrypting again when available
   */
  override def encryptMessage(message:         GenericMessage,
                              recipients:      QOtrClientIdMap,
                              useFakeOnError:  Boolean,
                              partialResult:   QEncryptedContent): Future[QEncryptedContent] = {

    val msgData = message.toByteArray
    for {
      payloads <- Future.traverse(recipients.entries) { case (qId, clientIds) =>
                    val partialResultForUser = partialResult.content.getOrElse(qId, Map.empty)
                    encryptForClients(qId, clientIds, msgData, useFakeOnError, partialResultForUser).map(qId -> _)
                  }
      content   = payloads.filter(_._2.nonEmpty).toMap
    } yield QEncryptedContent(content)
  }

  private def encryptForClients(userId:         UserId,
                                clients:        Set[ClientId],
                                msgData:        Array[Byte],
                                useFakeOnError: Boolean,
                                partialResult:  Map[ClientId, Array[Byte]]
                               ): Future[(UserId, Map[ClientId, Array[Byte]])] =
    users
      .qualifiedId(userId)
      .flatMap(encryptForClients(_, clients, msgData, useFakeOnError, partialResult))
      .map(userId -> _)

  private def encryptForClients(qId:            QualifiedId,
                                clients:        Set[ClientId],
                                msgData:        Array[Byte],
                                useFakeOnError: Boolean,
                                partialResult:  Map[ClientId, Array[Byte]]
                               ): Future[Map[ClientId, Array[Byte]]] =
    Future.traverse(clients) { clientId =>
      val previous = partialResult.get(clientId)
        .filter(arr => arr.nonEmpty && arr.sameElements(EncryptionFailedMsg))
      previous match {
        case Some(bytes) =>
          Future.successful(Some(clientId -> bytes))
        case None =>
          verbose(l"encrypt for client: $clientId")
          sessions.withSession(SessionId(qId, clientId, currentDomain)) {
            session => clientId -> session.encrypt(msgData)
          }.recover {
            case _: Throwable =>
              if (useFakeOnError) Some(clientId -> EncryptionFailedMsg) else None
          }
      }
    }.map(_.flatten.toMap)


  override def deleteClients(userMap: OtrClientIdMap): Future[Unit] =
    for {
      qMap <- Future.traverse(userMap.entries) { case (userId, cs) => users.qualifiedId(userId).map(_ -> cs) }
      _    <- deleteClients(QOtrClientIdMap(qMap.toMap))
    } yield ()

  override def deleteClients(userMap: QOtrClientIdMap): Future[Unit] = Future.traverse(userMap.entries) {
    case (qId, cs) =>
      for {
        removalResult <- clients.removeClients(qId.id, cs)
        _             <- Future.traverse(cs) { c => sessions.deleteSession(SessionId(qId, c, currentDomain)) }
        _             <- if (removalResult.exists(_._2.clients.isEmpty))
                           users.syncUser(qId.id)
                         else
                           Future.successful(())
      } yield ()
  }.map(_ => ())

  def encryptAssetDataCBC(key: AESKey, data: LocalData): Future[(Sha256, LocalData, EncryptionAlgorithm)] = {
    import Threading.Implicits.Background

    def encryptFile() = cache.createForFile(length = Some(sizeWithPaddingAndIV(data.length))) map { entry =>
      val mac = AESUtils.encrypt(key, data.inputStream, entry.outputStream)
      (mac, entry, AES_CBC)
    }

    def encryptBytes() = {
      val bos = new ByteArrayOutputStream()
      val mac = AESUtils.encrypt(key, data.inputStream, bos)
      cache.addData(CacheKey(), bos.toByteArray) map { (mac, _, AES_CBC) }
    }

    data.byteArray.fold(encryptFile()){ _ => encryptBytes() }
  }

  def encryptAssetData(key: AESKey, data: LocalData): Future[(Sha256, LocalData, EncryptionAlgorithm)] =
    encryptAssetDataCBC(key, data)

  def decryptAssetDataCBC(assetId: AssetId, otrKey: Option[AESKey], sha: Option[Sha256], data: Option[Array[Byte]]): Option[Array[Byte]] = {
    data.flatMap { arr =>
      otrKey.map { key =>
        if (sha.forall(_.str == com.waz.utils.sha2(arr))) Try(AESUtils.decrypt(key, arr)).toOption else None
      }.getOrElse {
        warn(l"got otr asset event without otr key: $assetId")
        Some(arr)
      }
    }.filter(_.nonEmpty)
  }

  override def decryptAssetData(assetId: AssetId, otrKey: Option[AESKey], sha: Option[Sha256], data: Option[Array[Byte]]): Option[Array[Byte]] =
    decryptAssetDataCBC(assetId, otrKey, sha, data)
}

object OtrService {

  val EncryptionFailedMsg: Array[Byte] = "\uD83D\uDCA3".getBytes("utf8")

  final case class SessionId(userId: UserId, domain: Domain, clientId: ClientId) {
    override def toString: String =
      if (BuildConfig.FEDERATION_USER_DISCOVERY && domain.isDefined)
        s"${userId}_${domain.str}_$clientId"
      else
        s"${userId}_$clientId"
  }

  object SessionId {
    def apply(ev: OtrEvent, currentDomain: Domain): SessionId =
     if (BuildConfig.FEDERATION_USER_DISCOVERY &&
         ev.fromDomain.isDefined &&
         currentDomain.isDefined &&
         !ev.fromDomain.contains(currentDomain.str))
        SessionId(ev.from, ev.fromDomain, ev.sender)
      else
        SessionId(ev.from, Domain.Empty, ev.sender)

    def apply(qId: QualifiedId, clientId: ClientId, currentDomain: Domain): SessionId =
      if (BuildConfig.FEDERATION_USER_DISCOVERY &&
          qId.hasDomain &&
          currentDomain.isDefined &&
          !currentDomain.contains(qId.domain))
        SessionId(qId.id, Domain(qId.domain), clientId)
      else
        SessionId(qId.id, Domain.Empty, clientId)
  }

  def hmacSha256(key: SignalingKey, data: Array[Byte]): Array[Byte] = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key.mac)
    mac.doFinal(data)
  }

  def sizeWithPaddingAndIV(size: Long): Long = size + (32L - (size % 16L))
}

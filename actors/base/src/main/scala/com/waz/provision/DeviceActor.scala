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
package com.waz.provision

import java.io._

import akka.actor.SupervisorStrategy._
import akka.actor._
import android.content.Context
import android.view.View
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.LogTag
import com.waz.api.{impl, _}
import com.waz.content.{Database, GlobalDatabase, GlobalPreferences}
import com.waz.log.{InternalLog, LogOutput}
import com.waz.media.manager.context.IntensityLevel
import com.waz.model.AccountData.Password
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, Liking, RConvId, MessageContent => _, _}
import com.waz.provision.DeviceActor.responseTimeout
import com.waz.service.AccountManager.ClientRegistrationState.Registered
import com.waz.service._
import com.waz.service.call.FlowManagerService
import com.waz.service.conversation.ConversationsUiService
import com.waz.testutils.Implicits._
import com.waz.threading.{DispatchQueueStats, _}
import com.waz.ui.UiModule
import com.waz.utils.RichFuture.traverseSequential
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.URI
import org.threeten.bp.Instant

import scala.concurrent.Future.successful
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Random, Success, Try}

/**
  * Protip: checkout QAActorSpec.scala for a simple test environment that'll speed up debugging
  */
object DeviceActor {

  val responseTimeout = 120.seconds

  def props(deviceName: String,
            application: Context,
            backend: BackendConfig = BackendConfig.StagingBackend) =
  Props(new DeviceActor(deviceName, application, backend)).withDispatcher("ui-dispatcher")
}

class DeviceActor(val deviceName: String,
                  val application: Context,
                  backend: BackendConfig = BackendConfig.StagingBackend) extends Actor with ActorLogging {

  import ActorMessage._

  InternalLog.add(new LogOutput {
    override def log(str: String, level: InternalLog.LogLevel, tag: LogTag, ex: Option[Throwable] = None): Unit = {
      import com.waz.log.InternalLog.LogLevel._
      level match {
        case Error => DeviceActor.this.log.error(s"$tag: $str")
        case Warn  => DeviceActor.this.log.warning(s"$tag: $str")
//        case Info  => DeviceActor.this.log.info(s"$tag: $str")
//        case Debug => DeviceActor.this.log.debug(s"$tag: $str")
        case _     => DeviceActor.this.log.info(s"$tag: $str")
      }
    }
    override def log(str: String, cause: Throwable, level: InternalLog.LogLevel, tag: LogTag): Unit =
      DeviceActor.this.log.error(cause, s"$tag: $str")

    override def flush() = Future.successful({})
    override def close() = Future.successful({})
    override val id = deviceName
  })

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 1, withinTimeRange = 10.seconds) {
      case exc: Exception =>
        log.error(exc, s"device actor '$deviceName' died")
        Stop
    }

  val globalModule = new GlobalModuleImpl(application, backend) { global =>
    ZMessaging.currentGlobal = this
    lifecycle.acquireUi()

    override lazy val accountsService = new AccountsServiceImpl(this) {
      ZMessaging.currentAccounts = this
      Await.ready(prefs(GlobalPreferences.FirstTimeWithTeams) := false, 5.seconds)
      Await.ready(prefs(GlobalPreferences.DatabasesRenamed) := true, 5.seconds)
    }

    override val storage: Database = new GlobalDatabase(application, Random.nextInt().toHexString)

    override lazy val metadata: MetaDataService = new MetaDataService(context) {
      override val cryptoBoxDirName: String = "otr_" + Random.nextInt().toHexString
      override lazy val deviceModel: String = deviceName
      override lazy val localBluetoothName: String = deviceName
    }

    override lazy val flowmanager = new FlowManagerService {
      override def flowManager = None

      override def getVideoCaptureDevices = Future.successful(Vector())

      override def setVideoCaptureDevice(id: RConvId, deviceId: String) = Future.successful(())

      override def setVideoPreview(view: View) = Future.successful(())

      override def setVideoView(id: RConvId, partId: Option[UserId], view: View) = Future.successful(())

      override val cameraFailedSig = Signal[Boolean](false)
    }

    override lazy val mediaManager = new MediaManagerService {
      override def mediaManager = Future.failed(new Exception("No media manager available in actors"))
      override def soundIntensity = Signal.empty[IntensityLevel]
      override def isSpeakerOn = Signal.empty[Boolean]
      override def setSpeaker(enable: Boolean) = Future.successful({})
    }
  }

  val accountsService = globalModule.accountsService

  val ui = returning(new UiModule(globalModule)) { ui =>
    ui.onStart()
  }

  val zms = accountsService.activeZms.collect { case Some(z) => z }
  val am  = accountsService.activeAccountManager.collect { case Some(a) => a }

  implicit val ec: DispatchQueue = new SerialDispatchQueue(name = s"DeviceActor_$deviceName")

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    ui.onDestroy()
    globalModule.lifecycle.releaseUi()
    Await.result(ui.getCurrent, 5.seconds) foreach { zms =>
      zms.syncContent.syncStorage { storage =>
        storage.getJobs foreach { job => storage.remove(job.id) }
      }
    }
    super.postStop()
  }

  def respondInFuture[S](receive: ActorMessage => Future[ResponseMessage]): Receive = {
    case message: ActorMessage =>
      log.info(s"Received message: $message")
      sender() ! (Try(Await.result(receive(message), responseTimeout)) match {
        case Success(m) => m
        case Failure(cause) =>
          val st = stackTrace(cause)
          log.error(cause, "Message handling failed")
          Failed(s"${cause.getMessage}: $st")
      })
  }

  override def receive: Receive = respondInFuture {
    case Echo(msg, _) => Future.successful(Echo(msg, deviceName))

    case Login(email, pass) => accountsService.loginEmail(email, pass).flatMap {
      case Right(userId) => accountsService.createAccountManager(userId, None, None).map(am => Right(am))
      case Left(err)     => Future.successful(Left(err))
    }.flatMap {
      case Right(Some(am)) => am.getOrRegisterClient().map(_.fold(e => Left(e), s => Right((am, s))))
      case Right(None)     => Future.successful(Left(impl.ErrorResponse.internalError("Failed to create account manager")))
      case Left(e)         => Future.successful(Left(e))
    }.map {
      case Right((am, Registered(cId))) => Successful(s"Successfully logged in with user: ${am.userId} and client: $cId")
      case Right((_, st))               => Failed(s"Failed to register client: $st")
      case Left(err)                    => Failed(s"Failed to log in with $email, $pass: $err")
    }

    case SendRequest(userId) =>
      Option(userId) match {
        case Some(uId) =>
          (for {
            z    <- zms.head
            user <- z.usersStorage.signal(uId).head
            conv <- z.connection.connectToUser(uId, "meep", user.getDisplayName)
          } yield conv.filter(_.convType == ConversationType.WaitForConnection))
            .map(_.fold2(Failed(s"Failed to send connect request to user $uId"), _ => Successful))
        case _ => Future.successful(Failed("UserId cannot be null"))
      }

    case GetUser =>
      waitForSelf.map(u => Successful(u.id.str))

    case GetUserName =>
      waitForSelf.map(u => Successful(u.name))

    case GetConv(name) =>
      zms.flatMap(_.convsStorage.convsSignal.map(_.conversations.find(_.name == name).map(_.remoteId))).head
        .map(_.fold2(Failed(s"Could not find a conversation with name: $name"), r => Successful(r.str)))

    case GetMessages(rConvId) =>
      for {
        (z, convId) <- zmsWithLocalConv(rConvId)
        idx         <- z.messagesStorage.msgsIndex(convId)
        cursor      <- idx.loadCursor
      } yield {
        ConvMessages(Array.tabulate(cursor.size) { i =>
          val m = cursor(i)
          MessageInfo(m.message.id, m.message.msgType, m.message.time.instant)
        })
      }

    case CreateGroupConversation(users@_*) =>
      zms.head.flatMap(_.convsUi.createGroupConversation(members = users.toSet)).map(_ => Successful)

    case ClearConversation(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, cId) =>
        z.convsUi.clearConversation(cId)
      }.map(_.fold2(Failed(s"Could not find a conversation with id: $remoteId"), r => Successful))

    case SendText(remoteId, msg) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, cId) =>
        z.convsUi.sendTextMessage(cId, msg)
      }.map(_.fold2(Failed(s"Unable to create message: $msg in conv: $remoteId"), r => Successful))

    case UpdateText(msgId, text) =>
      for {
        z   <- zms.head
        msg <- z.messagesStorage.getMessage(msgId)
        res <- msg match {
          case Some(msg) if msg.userId == z.selfUserId =>
            z.convsUi.updateMessage(msg.convId, msgId, text).map(_ => Successful)
          case Some(_) =>
            Future.successful(Failed("Can not update messages from other user"))
          case None =>
            Future.successful(Failed("No message found with given id"))
        }
      } yield res

    case DeleteMessage(rConvId, msgId) =>
      for {
        (z, convId) <- zmsWithLocalConv(rConvId)
        res <- z.messagesStorage.getMessage(msgId).flatMap {
          case Some(msg) =>
            z.convsUi.deleteMessage(convId, msgId).map(_ => Successful)
          case None =>
            Future.successful(Failed("No message found with given id"))
        }
      } yield res

    case SendGiphy(rConvId, searchQuery) =>
      zmsWithLocalConv(rConvId).flatMap { case (z, convId) =>
        for {
          res   <- (if (searchQuery.isEmpty) z.giphy.trending() else z.giphy.searchGiphyImage(searchQuery)).future
          msg1  <- z.convsUi.sendTextMessage(convId, "Via giphy.com")
//              msg2  <- z.convsUi.sendMessage(convId, ) //TODO use asset data directly when we get rid of ImageAsset
        } yield Successful
      }

    case RecallMessage(rConvId, msgId) =>
      zmsWithLocalConv(rConvId).flatMap {
        case (z, convId) => z.convsUi.recallMessage(convId, msgId)
      }.map(_ => Successful)

    case AcceptConnection(userId) =>
      Option(userId) match {
        case Some(_) =>
          for {
            z <- zms.head
            _ <- z.usersStorage.signal(userId).filter(_.connection == ConnectionStatus.PendingFromOther).head
            _ <- z.connection.acceptConnection(userId)
          } yield Successful
        case None =>
          Future.successful(Failed("UserId cannot be null"))
      }

    case SendImage(remoteId, path) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.sendMessage(convId, ui.images.createImageAssetFrom(IoUtils.toByteArray(new FileInputStream(path))))
      }.map(_.fold2(Failed("no message sent"), m => Successful(m.id.str)))

    case SendImageData(remoteId, bytes) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.sendMessage(convId, ui.images.createImageAssetFrom(bytes))
      }.map(_.fold2(Failed("no message sent"), m => Successful(m.id.str)))

    case SendAsset(remoteId, bytes, mime, name, _) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        //TODO for now this assumes image only - need to handle bytes for other asset types too
//        val asset = impl.AssetForUpload(AssetId(), Some(name), Mime(mime), Some(bytes.length.toLong)){
//          _ => new ByteArrayInputStream(bytes)
//        }
        z.convsUi.sendMessage(convId, bytes)
      }.map(_.fold2(Failed("no message sent"), m => Successful(m.id.str)))

    case SendLocation(remoteId, lon, lat, name, zoom) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.sendLocationMessage(convId, new MessageContent.Location(lon, lat, name, zoom))
      }.map(_.fold2(Failed("no message sent"), m => Successful(m.id.str)))

    case SendFile(remoteId, path, mime) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.sendUriMessage(convId, URI.parse(path), ConversationsUiService.DefaultConfirmation)
      }.map(_.fold2(Failed("no message sent"), m => Successful(m.id.str)))

    case AddMembers(remoteId, users@_*) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
          z.convsUi.addConversationMembers(convId, users.toSet)
      }.map(_ => Successful)

    case Knock(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.knock(convId)
      }.map(_.fold2(Failed("no ping sent"), m => Successful(m.id.str)))

    case SetEphemeral(remoteId, expiration) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.setEphemeral(convId, expiration)
      }.map(_ => Successful)

    case MarkEphemeralRead(convId, messageId) =>
      zms.head.flatMap(_.ephemeral.onMessageRead(messageId))
        .map(_ => Successful)

    case Typing(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.typing.selfChangedInput(convId)
      }.map(_ => Successful)

    case ClearTyping(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.typing.selfClearedInput(convId)
      }.map(_ => Successful)

    case ArchiveConv(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.setConversationArchived(convId, archived = true)
      }.map(_ => Successful)

    case UnarchiveConv(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.setConversationArchived(convId, archived = false)
      }.map(_ => Successful)

    case MuteConv(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.setConversationMuted(convId, muted = true)
      }.map(_ => Successful)

    case UnmuteConv(remoteId) =>
      zmsWithLocalConv(remoteId).flatMap { case (z, convId) =>
        z.convsUi.setConversationMuted(convId, muted = false)
      }.map(_ => Successful)

    case UpdateProfileImage(path) =>
      zms.head.flatMap(_.users.updateSelfPicture(ui.images.createImageAssetFrom(IoUtils.toByteArray(getClass.getResourceAsStream(path)))))
        .map(_ => Successful)

    case UpdateProfileName(name) =>
      zms.head.flatMap(_.users.updateName(name))
        .map(_ => Successful)

    case UpdateProfileUserName(userName) =>
      zms.head.flatMap(_.users.updateHandle(Handle(userName)))
        .map(_.fold(err => Failed(s"unable to update user name: ${err.code}, ${err.message}, ${err.label}"), _ => Successful))

    case SetStatus(status) =>
      Availability.all.find(_.toString.toLowerCase == status.toLowerCase) match {
        case Some(availability) =>
          for {
            z            <- zms.head
            _            <- z.users.storeAvailabilities(Map(z.selfUserId -> availability))
          } yield Successful
        case None => Future.successful(Failed(s"Unknown availability: $status"))
      }

    case UpdateProfileColor(color) =>
      zms.head.flatMap(_.users.updateAccentColor(color))
        .map(_ => Successful)

    case UpdateProfileEmail(email) =>
      zms.head.flatMap(_.users.updateEmail(EmailAddress(email)))
        .map(_ => Successful)

    case SetMessageReaction(remoteId, messageId, action) =>
      zms.head.flatMap { z =>
        z.messagesStorage.getMessage(messageId).flatMap {
          case Some(msg) if action == Liking.Action.Like =>
            z.reactions.like(msg.convId, messageId).map(_ => Successful)
          case Some(msg) =>
            z.reactions.unlike(msg.convId, messageId).map(_ => Successful)
          case None =>
            Future.successful(Failed("No message found with given id"))
        }
      }

    case SetDeviceLabel(label) =>
      for {
        z      <- zms.head
        client <- z.otrClientsService.selfClient.head
        _      <- z.otrClientsService.updateClientLabel(client.id, label)
      } yield Successful

    case DeleteDevice(clientId, password) =>
      am.head.flatMap(_.deleteClient(ClientId(clientId), Password(password)))
        .map(_.fold(err => Failed(s"Failed to delete client: ${err.code}, ${err.message}, ${err.label}"), _ => Successful))

    case DeleteAllOtherDevices(password) =>
      for {
        z             <- zms.head
        am            <- am.head
        clients       <- am.clientsStorage.getClients(am.userId).map(_.map(_.id))
        others         = clients.filter(_ != z.clientId)
        responses     <- traverseSequential(others)(am.deleteClient(_, Password(password)))
        failures       = responses.collect { case Left(err) => s"[unable to delete client: ${err.message}, ${err.code}, ${err.label}]" }
      } yield if (failures.isEmpty) Successful else Failed(failures mkString ", ")

    case GetDeviceId() =>
      zms.head.flatMap(_.otrClientsService.selfClient.head).map(c => Successful(c.id.str))

    case GetDeviceFingerPrint() =>
      (for {
        z      <- zms.head
        am     <- am.head
        client <- z.otrClientsService.selfClient.head
        fp     <- am.fingerprintSignal(z.selfUserId, client.id).head
      } yield fp.map(new String(_)))
        .map(_.fold2(Failed("Failed to get finger print for self client"), Successful(_)))

    case AwaitSyncCompleted =>
      zms.flatMap(_.syncContent.syncJobs.filter(_.isEmpty)).head.map(_ => Successful)

    case ResetQueueStats =>
      Future.successful({
        com.waz.threading.DispatchQueueStats.reset()
        Successful
      })

    case GetQueueStats =>
      println(s"dispatch queue stats")
      DispatchQueueStats.printStats(10)
      successful({
        QueueStats(DispatchQueueStats.report(10).toArray)
      })

    case ForceAddressBookUpload =>
      for {
        z <- zms.head
        _ <- z.contacts.lastUploadTime := Some(Instant.EPOCH)
        _ <- z.contacts.requestUploadIfNeeded()
      } yield Successful

    case m@_ =>
      log.error(s"unknown remote api command '$m'")
      Future.successful(Failed(s"unknown remote api command '$m'"))
  }

  def zmsWithLocalConv(rConvId: RConvId): Future[(ZMessaging, ConvId)] = {
    for {
      z   <- zms.head
      _   = log.info(s"zms ready: $z")
      cId <- z.convsStorage.convsSignal.map { csSet =>
        log.info(s"all conversations: ${csSet.conversations}")
        csSet.conversations.find(_.remoteId == rConvId)
      }.collect { case Some(c) => c.id }.head
      _   = log.info(s"Found local conv: $cId for remote: $rConvId")
    } yield (z, cId)
  }

  def waitForSelf: Future[UserData] =
    zms.flatMap(_.users.selfUser).head

  def stackTrace(t: Throwable) = {
    val result = new StringWriter()
    val printWriter = new PrintWriter(result)
    t.printStackTrace(printWriter)
    result.toString
  }
}

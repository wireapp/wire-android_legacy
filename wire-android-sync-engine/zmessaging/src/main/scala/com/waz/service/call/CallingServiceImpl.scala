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
package com.waz.service.call

import java.net.InetSocketAddress

import android.Manifest.permission.CAMERA
import com.sun.jna.Pointer
import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences.SkipTerminatingState
import com.waz.content.{GlobalPreferences, MembersStorage, UserPreferences, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.log.LogSE._
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, RConvId, UserId, _}
import com.waz.permissions.PermissionsService
import com.waz.service.EventScheduler.Stage
import com.waz.service.ZMessaging.clock
import com.waz.service._
import com.waz.service.call.Avs.AvsClosedReason.{StillOngoing, reasonString}
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.Avs.{AvsClosedReason, VideoState, WCall}
import com.waz.service.call.CallInfo.{CallState, Participant}
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.call.CallingService.GlobalCallProfile
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.service.tracking.{AVSMetricsEvent, TrackingService}
import com.waz.sync.client.CallingClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.events._
import com.waz.utils.wrappers.Context
import com.waz.utils.{RichInstant, Serialized, returning, returningF}
import org.threeten.bp.Duration

import scala.collection.immutable.ListSet
import scala.concurrent.{Future, Promise}
import scala.util.Success
import scala.util.control.NonFatal

class GlobalCallingService extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  lazy val globalCallProfile: Signal[GlobalCallProfile] =
    ZMessaging.currentAccounts.zmsInstances.flatMap(zs => Signal.sequence(zs.map(_.calling.callProfile).toSeq: _*)).map { profiles =>
      GlobalCallProfile(profiles.flatMap(_.calls.map(c => (c._2.selfParticipant.userId, c._2.convId) -> c._2)).toMap)
    }

  private lazy val services: Signal[Set[(UserId, CallingServiceImpl)]] = ZMessaging.currentAccounts.zmsInstances.map(_.map(z => z.selfUserId -> z.calling))

  //If there is an active call in one or more of the logged in accounts, returns the account id for the one with the oldest call
  lazy val activeAccount: Signal[Option[UserId]] = globalCallProfile.map(_.activeCall.map(_.selfParticipant.userId))

  //can be used to drop all active calls in case of GCM
  def dropActiveCalls(): Unit = services.head.map(_.map(_._2)).map(_.foreach(_.onInterrupted()))
}

trait CallingService {
  def calls:          Signal[Map[ConvId, CallInfo]]
  def joinableCalls:  Signal[Map[ConvId, CallInfo]]
  def currentCall:    Signal[Option[CallInfo]]

  /**
    * @param skipTerminating Used to skip the terminating state when the self user ends the call
    * @return Future as this function is called from background service
    */
  def endCall(convId: ConvId, skipTerminating: Boolean = false): Future[Unit]

  /**
    * Dismiss the Terminating active call - if there is no such call, this method will do nothing.
    * @return
    */
  def dismissCall(): Future[Unit]

  /**
    * Either start a new call or join an active call (incoming/ongoing)
    *
    * @param isVideo will be discarded if call is already active
    * @param forceOption used to ignore the current calls video send state (useful for when a user joins a video call using the audio button, for example)
    *                    //TODO we could turn isVideo into an Option[Boolean], where defined = force, and undefined means use current call state
    */
  def startCall(convId: ConvId, isVideo: Boolean = false, forceOption: Boolean = false): Future[Unit]

  def continueDegradedCall(): Unit

  def setCallMuted(muted: Boolean): Unit

  def setVideoSendState(convId: ConvId, state: VideoState.Value): Unit
}

object CallingService {

  val VideoCallMaxMembers: Int = 4

  trait AbstractCallProfile[A] {

    val calls: Map[A, CallInfo]

    val activeCall: Option[CallInfo] =
      calls
        .filter { case (_, call) => isActive(call.state, call.shouldRing) }
        .values.toSeq
        .sortBy(_.startTime)
        .headOption

    val incomingCalls: Map[A, CallInfo] =
      calls.filter { case (_, call) => call.state == OtherCalling }

    val joinableCalls: Map[A, CallInfo] =
      calls.filter { case (_, call) => isJoinable(call.state) }

    val endedCalls: Map[A, CallInfo] =
      calls.filter { case (_, call) => call.state == Ended }

    override def toString: String =
      s"active call: $activeCall, incomingCalls: ${incomingCalls.values} joinable calls: ${joinableCalls.values}, ended calls: ${endedCalls.values}"
  }

  case class CallProfile(override val calls: Map[ConvId, CallInfo]) extends AbstractCallProfile[ConvId]

  case class GlobalCallProfile(override val calls: Map[(UserId, ConvId), CallInfo]) extends AbstractCallProfile[(UserId, ConvId)]

  object CallProfile {
    val Empty = CallProfile(Map.empty)
  }

  object GlobalCallProfile {
    val Empty = GlobalCallProfile(Map.empty)
  }

}

class CallingServiceImpl(val accountId:       UserId,
                         val clientId:        ClientId,
                         callingClient:       CallingClient,
                         context:             Context,
                         avs:                 Avs,
                         convs:               ConversationsContentUpdater,
                         convsService:        ConversationsService,
                         members:             MembersStorage,
                         otrSyncHandler:      OtrSyncHandler,
                         flowManagerService:  FlowManagerService,
                         messagesService:     MessagesService,
                         mediaManagerService: MediaManagerService,
                         pushService:         PushService,
                         network:             NetworkModeService,
                         errors:              ErrorsService,
                         userPrefs:           UserPreferences,
                         globalPrefs:         GlobalPreferences,
                         permissions:         PermissionsService,
                         userStorage:         UsersStorage,
                         tracking:            TrackingService)(implicit accountContext: AccountContext) extends CallingService with DerivedLogTag with SafeToLog { self =>

  import CallingService._

  private implicit val dispatcher: SerialDispatchQueue = new SerialDispatchQueue(name = "CallingService")

  //need to ensure that flow manager and media manager are initialised for v3 (they are lazy values)
  flowManagerService.flowManager
  private var closingPromise = Option.empty[Promise[Unit]]

  private[call] val callProfile = Signal(CallProfile.Empty)

  ZMessaging.currentGlobal.httpProxy.foreach { proxy =>
     val proxyAddress = proxy.address().asInstanceOf[InetSocketAddress]
     avs.setProxy(proxyAddress.getHostName, proxyAddress.getPort)
  }

  callProfile(p => verbose(l"Call profile: ${p.calls}"))

  override val calls          = callProfile.map(_.calls).disableAutowiring() //all calls
  override val joinableCalls  = callProfile.map(_.joinableCalls).disableAutowiring() //any call a user can potentially join in the UI
  override val currentCall    = callProfile.map(_.activeCall).disableAutowiring() //state about any call for which we should show the CallingActivity
  val joinableCallsNotMuted   = joinableCalls.map(_.filter { case (_, call) => call.shouldRing })


  //exposed for tests only
  private[call] lazy val wCall = returningF(avs.registerAccount(this)) { call =>
    call.onFailure {
      case NonFatal(e) => error(l"Failed to initialise WCall for user: $accountId", e)
    }
  }

  Option(ZMessaging.currentAccounts).foreach(
    _.accountsWithManagers.map(_.contains(accountId)) {
      case false =>
        verbose(l"Account $accountId logged out, unregistering from AVS")
        wCall.map(avs.unregisterAccount)
      case true =>
    }(EventContext.Global)
  )

  def onSend(ctx: Pointer, convId: RConvId, userId: UserId, clientId: ClientId, msg: String): Future[Unit] =
    withConv(convId) { (_, conv) =>
      sendCallMessage(conv.id, GenericMessage(Uid(), GenericContent.Calling(msg)), ctx)
    }

  /**
    * @param shouldRing "Also we give you a bool to indicate whether you should ring in incoming. its always true in 1:1,
    *                   true if someone called recently for group but false if the call was started more than 30 seconds ago"
    *                                                                                                               - Chris the All-Knowing.
    */
  def onIncomingCall(convId: RConvId, userId: UserId, videoCall: Boolean, shouldRing: Boolean): Future[Unit] = {
    def showCall(conv: ConversationData, isGroup: Boolean) = {
      verbose(l"Incoming call from $userId in conv: $convId (should ring: $shouldRing)")

      permissions.allPermissions(ListSet(CAMERA)).head.foreach { granted =>
        updateCallInfo(conv.id, _.copy(videoSendState = (videoCall, granted) match {
          case (true, false) => VideoState.NoCameraPermission
          case (true, true) => VideoState.Started
          case _ => VideoState.Stopped
        }))("onIncomingCall-permissionCheck")
      }

      val newCall = CallInfo(
        conv.id,
        selfParticipant = Participant(accountId, clientId),
        isGroup,
        userId,
        OtherCalling,
        startedAsVideoCall = videoCall,
        videoSendState = VideoState.NoCameraPermission,
        shouldRing = !conv.muted.isAllMuted && shouldRing)

      callProfile.mutate { p =>
        // If we have a call in the profile with the same id, this incoming call should be just a GROUPCHECK
        val call = p.calls.get(newCall.convId).filter(c => !isFinished(c.state)).getOrElse(newCall)
        p.copy(calls = p.calls + (call.convId -> call))
      }
    }

    def withConvGroup(convId: RConvId)(f: (ConversationData, Boolean) => Unit) = Serialized.future(self) {
      (for {
        _          <- wCall
        Some(conv) <- convs.convByRemoteId(convId)
        isGroup    <- convsService.isGroupConversation(conv.id)
      } yield f(conv, isGroup))
        .recover {
          case NonFatal(e) => error(l"Unknown remote convId: $convId")
        }
    }

    withConvGroup(convId) {
      case (conv, isGroup) => showCall(conv, isGroup)
    }
  }

  def onOtherSideAnsweredCall(rConvId: RConvId): Future[Unit] =
    updateCallIfActive(rConvId) { (_, conv, call) =>
      verbose(l"outgoing call answered for conv: ${conv.id}")
      call.updateCallState(SelfJoining)
    } ("onOtherSideAnsweredCall")

  def onMissedCall(rConvId: RConvId, time: RemoteInstant, userId: UserId, videoCall: Boolean): Future[Option[MessageData]] = {
    verbose(l"Missed call for conversation: $rConvId at $time from user $userId. Video: $videoCall")
    messagesService.addMissedCallMessage(rConvId, userId, time)
  }

  def onEstablishedCall(rConvId: RConvId, userId: UserId): Future[Unit] =
    updateCallIfActive(rConvId) { (_, conv, c) =>
      verbose(l"call established for conv: ${conv.id}, userId: $userId, time: ${clock.instant}")
      setVideoSendState(conv.id, c.videoSendState) // Will upgrade call videoSendState
      setCallMuted(c.muted) // Need to set muted only after call is established
      c.updateCallState(SelfConnected)

      // TODO: In AVS 5.4, we will have access to the client id here. We should then update the
      // participants and maxParticipants property.
    }("onEstablishedCall")

  override def endCall(convId: ConvId, skipTerminating: Boolean = false) = {
    withConv(convId) { (w, conv) =>
      globalPrefs(SkipTerminatingState).apply().map { skipTerminatingPref =>
        callProfile.mutate { p =>
          val skipTerminatingUltimate = skipTerminating || skipTerminatingPref
          val updatedCall = p.calls.get(conv.id).map { call =>
            verbose(l"endCall: $convId, skipTerminating (ultimate): $skipTerminatingUltimate. Active call in state: ${call.state}")
            //avs reject and end call will always trigger the onClosedCall callback - there we handle the end of the call
            if (call.state == OtherCalling) avs.rejectCall(w, conv.remoteId) else avs.endCall(w, conv.remoteId)
            //if there is another incoming call - skip the terminating state
            val hasIncomingCall = p.incomingCalls.nonEmpty
            call.updateCallState(call.state match {
              case SelfConnected if !hasIncomingCall && !skipTerminatingUltimate => Terminating
              case OtherCalling => Ongoing //go straight to state "Ongoing" for incoming calls
              case _ if call.canOthersDialogue => Ongoing
              case _ => Ended
            })
          }
          p.copy(updatedCall.fold(p.calls)(c => p.calls + (conv.id -> c)))
        }
      }
    }
    returning(Promise[Unit]())(p => closingPromise = Some(p)).future
  }

  override def dismissCall() =
    updateActiveCallAsync { (_, conv, call) =>
      verbose(l"dismissCall(): ${conv.id}")
      call.state match {
        case Terminating => call.updateCallState(if (call.canOthersDialogue) Ongoing else Ended)
        case _ => call
      }
    }("dismissCall")

  def onClosedCall(reason: AvsClosedReason, rConvId: RConvId, time: RemoteInstant, userId: UserId): Future[Unit] =
    withConv(rConvId) { (_, conv) =>
      globalPrefs(SkipTerminatingState).apply().map { skipTerminating =>
        callProfile.mutate { p =>
          verbose(l"call closed for reason: ${showString(reasonString(reason))}, conv: ${conv.id} at $time, userId: $userId, currentState: ${p.activeCall.map(_.state)}, skipTerminating: $skipTerminating")
          val updatedCall = p.calls.get(conv.id).map { c =>
            // Group calls that you don't answer (but are answered by other users) will be "closed"
            // with reason StillOngoing. We need to keep these around so the user can join them later
            if (reason == StillOngoing) c.updateCallState(Ongoing)
            else {
              val hasIncomingCall = p.incomingCalls.nonEmpty
              c.updateCallState(c.state match {
                case SelfConnected | SelfJoining | Terminating if !hasIncomingCall && !skipTerminating => Terminating
                case _                                                                                 => Ended
              }).copy(endReason = Some(reason))
            }
          }
          p.copy(updatedCall.fold(p.calls)(c => p.calls + (conv.id -> c)))
        }
        closingPromise.foreach(_.tryComplete(Success({})))
      }
    }

  def onMetricsReady(convId: RConvId, metricsJson: String): Unit =
    tracking.track(AVSMetricsEvent(metricsJson), Some(accountId))

  def onConfigRequest(wcall: WCall): Int = {
    verbose(l"onConfigRequest")
    callingClient.getConfig.map { resp =>
      avs.onConfigRequest(wcall, resp.fold(err => err.code, _ => 0), resp.fold(_ => "", identity))
    }
    0
  }

  def onBitRateStateChanged(enabled: Boolean): Unit =
    updateActiveCallAsync { (_, _, call) =>
      verbose(l"onBitRateStateChanged enabled=$enabled")
      call.copy(isCbrEnabled = enabled)
    }("onBitRateStateChanged")

  def onVideoStateChanged(userId: String, clientId: String, videoReceiveState: VideoState): Future[Unit] =
    updateActiveCallAsync { (_, _, call) =>
      verbose(l"video state changed: $videoReceiveState")
      call.updateVideoState(Participant(UserId(userId), ClientId(clientId)), videoReceiveState)
    }("onVideoStateChanged")

  def onParticipantsChanged(rConvId: RConvId, participants: Set[Participant]): Future[Unit] =
    updateCallIfActive(rConvId) { (_, conv, call) =>
      verbose(l"group participants changed, convId: ${conv.id}, other participants: $participants")
      val updated = participants.map { p =>
        p -> call.otherParticipants.getOrElse(p, Some(LocalInstant.Now))
      }.toMap

      call.copy(otherParticipants = updated, maxParticipants = math.max(call.maxParticipants, participants.size + 1))
    } ("onParticipantsChanged")

  network.networkMode.onChanged { _ =>
    currentCall.head.flatMap {
      case Some(_) =>
        verbose(l"network mode changed during call - informing AVS")
        wCall.map(avs.onNetworkChanged)
      case _ => Future.successful({})
    }
  }

  override def startCall(convId: ConvId, isVideo: Boolean = false, forceOption: Boolean = false) =
    Serialized.future(self) {
      verbose(l"startCall $convId, isVideo: $isVideo, forceOption: $forceOption")
      (for {
        w          <- wCall
        Some(conv) <- convs.convById(convId)
        profile <- callProfile.head
        isGroup <- convsService.isGroupConversation(convId)
        vbr     <- userPrefs.preference(UserPreferences.VBREnabled).apply()
        convSize <- convsService.activeMembersData(conv.id).map(_.size).head
        callType =
          if (convSize > VideoCallMaxMembers) Avs.WCallType.ForcedAudio
          else if (isVideo) Avs.WCallType.Video
          else Avs.WCallType.Normal
        convType = if (isGroup) Avs.WCallConvType.Group else Avs.WCallConvType.OneOnOne
        _ <- permissions.ensurePermissions(ListSet(android.Manifest.permission.RECORD_AUDIO) ++ (if(forceOption && isVideo) ListSet(android.Manifest.permission.CAMERA) else ListSet()))
        _ <-
          profile.activeCall match {
            case Some(call) if call.convId == convId =>
              Future.successful {
                call.state match {
                  case OtherCalling =>
                    verbose(l"Answering call")
                    avs.answerCall(w, conv.remoteId, callType, !vbr)
                    updateActiveCall(_.updateCallState(SelfJoining))("startCall/OtherCalling")
                    if (forceOption)
                      setVideoSendState(convId, if (isVideo)  Avs.VideoState.Started else Avs.VideoState.Stopped)
                  case _ =>
                    warn(l"Tried to join an already joined/connecting call - ignoring")
                }
              }
            case Some(_) =>
              Future.successful(warn(l"Tried to start a new call while already in a call - ignoring"))
            case None =>
              profile.calls.get(convId) match {
                case Some(call) if !Set[CallState](Ended, Terminating)(call.state) =>
                  Future.successful {
                    verbose(l"Joining an ongoing background call")
                    avs.answerCall(w, conv.remoteId, callType, !vbr)
                    val active = call.updateCallState(SelfJoining).copy(joinedTime = None, estabTime = None) // reset previous call state if exists
                    callProfile.mutate(_.copy(calls = profile.calls + (convId -> active)))
                    setCallMuted(muted = false)
                    if (forceOption)
                      setVideoSendState(convId, if (isVideo)  Avs.VideoState.Started else Avs.VideoState.Stopped)
                  }
                case _ =>
                  verbose(l"No active call, starting new call")
                  avs.startCall(w, conv.remoteId, callType, convType, !vbr).map {
                    case 0 =>
                      //Assume that when a video call starts, sendingVideo will be true. From here on, we can then listen to state handler
                      val newCall = CallInfo(
                        conv.id,
                        selfParticipant = Participant(accountId, clientId),
                        isGroup,
                        accountId,
                        SelfCalling,
                        startedAsVideoCall = isVideo,
                        videoSendState = if (isVideo) VideoState.Started else VideoState.Stopped)
                      callProfile.mutate(_.copy(calls = profile.calls + (newCall.convId -> newCall)))
                    case err => warn(l"Unable to start call, reason: errno: $err")
                  }
              }
          }
      } yield {}).recover {
        case NonFatal(e) =>
          error(l"Failed to start call", e)
      }
    }

  override def continueDegradedCall() =
    currentCall.head.map {
      case Some(info) =>
        (info.outstandingMsg, info.state) match {
          case (Some((msg, ctx)), _) => convs.storage.setUnknownVerification(info.convId).map(_ => sendCallMessage(info.convId, msg, ctx))
          case (None, OtherCalling) => convs.storage.setUnknownVerification(info.convId).map(_ => startCall(info.convId))
          case _ => error(l"Tried resending message on invalid info: ${info.convId} in state ${info.state}")
        }
      case None => warn(l"Tried to continue degraded call without a current active call")
    }

  private def sendCallMessage(convId: ConvId, msg: GenericMessage, ctx: Pointer): Unit =
    withConv(convId) { (w, conv) =>
      verbose(l"Sending msg on behalf of avs: convId: $convId")
      otrSyncHandler.postOtrMessage(convId, msg).map {
        case Right(_) =>
          updateActiveCall(_.copy(outstandingMsg = None))("sendCallMessage/verified")
          avs.onHttpResponse(w, 200, "", ctx)
        case Left(ErrorResponse.Unverified) =>
          warn(l"Conversation degraded, delay sending message on behalf of AVS")
          //TODO need to handle degrading of conversation during a call
          //Currently, the call will just time out...
          updateActiveCall(_.copy(outstandingMsg = Some(msg, ctx)))("sendCallMessage/unverified")
        case Left(ErrorResponse(code, errorMsg, label)) =>
          avs.onHttpResponse(w, code, errorMsg, ctx)
      }
    }

  //Drop the current call in case of incoming GSM interruption
  //TODO - we should actually end all calls here - we don't want any other incoming calls to compete with GSM
  def onInterrupted(): Unit =
    updateActiveCallAsync { (w, conv, call) =>
      verbose(l"onInterrupted - gsm call received")
      //Ensure that conversation state is only performed INSIDE withConv
      avs.endCall(w, conv.remoteId)
      call
    } ("onInterrupted")

  override def setCallMuted(muted: Boolean): Unit = {
    verbose(l"setCallMuted: $muted")
    updateActiveCallAsync { (w, _, call) =>
      avs.setCallMuted(w, muted)
      call.copy(muted = muted)
    }("setCallMuted")
  }

  /**
    * This method should NOT be called before we have permissions AND while the call is still incoming. Once established,
    * we do call this to convert NoCameraPermission to state Stopped.
    */
  override def setVideoSendState(convId: ConvId, state: VideoState.Value): Unit =
    updateCallIfActive(convId) { (w, conv, call) =>
      val targetSt = state match {
        case NoCameraPermission => Stopped //NoCameraPermission is only valid for incoming
        case c => c
      }
      verbose(l"setVideoSendActive: $convId, providedState: $state, targetState: $targetSt")
      if (state != NoCameraPermission) avs.setVideoSendState(w, conv.remoteId, targetSt)
      call.updateVideoState(Participant(accountId, clientId), targetSt)
    }("setVideoSendState")

  val callMessagesStage: Stage.Atomic = EventScheduler.Stage[CallMessageEvent] {
    case (_, events) =>
      Future.successful(events.sortBy(_.time).foreach { e =>
        receiveCallEvent(e.content, e.time, e.convId, e.from, e.sender)
      })
  }

  private def receiveCallEvent(msg: String, msgTime: RemoteInstant, convId: RConvId, from: UserId, sender: ClientId): Unit =
    wCall.map { w =>
      val drift = pushService.beDrift.currentValue.getOrElse(Duration.ZERO)
      val curTime = LocalInstant(clock.instant + drift)
      verbose(l"Received msg for avs: localTime: ${clock.instant} curTime: $curTime, drift: $drift, msgTime: $msgTime")
      avs.onReceiveMessage(w, msg, curTime, msgTime, convId, from, sender)
    }

  private def withConv(convId: RConvId)(f: (WCall, ConversationData) => Unit) =
    atomicWithConv(convs.convByRemoteId(convId), f, s"Unknown remote convId: $convId")

  private def withConv(convId: ConvId)(f: (WCall, ConversationData) => Unit): Future[Unit] = {
    atomicWithConv(convs.convById(convId), f, s"Could not find conversation: $convId")
  }

  /**
    * Be sure to use serialised to ensure that flatmap, map and then performing f happen in an atomic operation on this dispatcher, or
    * else other futures posted to the dispatcher can sneak in between.
    */
  private def atomicWithConv(loadConversation: => Future[Option[ConversationData]], f: (WCall, ConversationData) => Unit, convNotFoundMsg: String) = {
    Serialized.future(self) {
      wCall.flatMap { w =>
        loadConversation.map {
          case Some(conv) => f(w, conv)
          case _          => error(l"${showString(convNotFoundMsg)}")
        }
      }
    }
  }

  private def updateCallIfActive(rConvId: RConvId)(f: (WCall, ConversationData, CallInfo) => CallInfo)(caller: String) = {
    withConv(rConvId) { (w, conv) =>
      updateCallInfo(conv.id, {
        case call if conv.id == call.convId => f(w, conv, call)
        case c =>
          warn(l"${showString(caller)} tried to update a call that wasn't the active one")
          c
      }) (caller)
    }
  }

  private def updateCallIfActive(convId: ConvId)(f: (WCall, ConversationData, CallInfo) => CallInfo)(caller: String) = {
    withConv(convId) { (w, conv) =>
      updateCallInfo(convId, {
        case call if conv.id == call.convId => f(w, conv, call)
        case c =>
          warn(l"${showString(caller)} tried to update a call that wasn't the active one")
          c
      }) (caller)
    }
  }

  private def updateActiveCallAsync(f: (WCall, ConversationData, CallInfo) => CallInfo)(caller: String): Future[Unit] =
    Serialized.future(self) {
      currentCall.currentValue.flatten.map(_.convId).fold(Future.successful({})) { convId =>
        wCall.flatMap { w =>
          convs.convById(convId).map {
            case Some(conv) => updateCallInfo(convId, f(w, conv, _))(caller)
            case _          => error(l"Could not find conversation: $convId")
          }
        }
      }
    }

  private def updateActiveCall(f: CallInfo => CallInfo)(caller: String): Boolean = {
    callProfile.mutate { p =>
      p.copy(calls = {
        val updated = p.activeCall.map(f)
        updated.fold {
          warn(l"${showString(caller)} tried to update active call when there was none - no change")
          p.calls
        }(u => p.calls + (u.convId -> u))
      })
    }
  }

  private def updateCallInfo(convId: ConvId, f: CallInfo => CallInfo)(caller: String) = {
    callProfile.mutate { p =>
      p.copy(calls = {
        val updated = p.calls.get(convId).map(f)
        updated.fold {
          warn(l"${showString(caller)} tried to update call info when there was none - no change")
          p.calls
        }(u => p.calls + (u.convId -> u))
      })
    }
  }
}



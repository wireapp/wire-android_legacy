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


import android.Manifest.permission.CAMERA
import com.sun.jna.Pointer
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.content.{MembersStorage, UserPreferences}
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, RConvId, UserId, _}
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging.clock
import com.waz.service._
import com.waz.service.call.Avs.AvsClosedReason.{StillOngoing, reasonString}
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.Avs.{AvsClosedReason, VideoState, WCall}
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.call.CallingService.GlobalCallProfile
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.service.tracking.{AVSMetricsEvent, TrackingService}
import com.waz.sync.client.CallingClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.events._
import com.waz.utils.wrappers.Context
import com.waz.utils.{RichInstant, RichWireInstant, Serialized, returning, returningF}
import com.waz.zms.CallWakeService
import org.threeten.bp.Duration

import scala.concurrent.{Future, Promise}
import scala.util.Success
import scala.util.control.NonFatal

class GlobalCallingService() {

  lazy val globalCallProfile: Signal[GlobalCallProfile] = ZMessaging.currentAccounts.zmsInstances.flatMap(zs => Signal.sequence(zs.map(_.calling.callProfile).toSeq: _*)).map { profiles =>
    val allCalls = profiles.flatMap(_.availableCalls.map(c => (c._2.account, c._2.convId) -> c._2)).sortBy(_._2.startTime)
    GlobalCallProfile(allCalls.headOption.map(_._1), allCalls.toMap)
  }

  lazy val services: Signal[Set[(UserId, CallingService)]] = ZMessaging.currentAccounts.zmsInstances.map(_.map(z => z.selfUserId -> z.calling))

  //If there is an active call in one or more of the logged in accounts, returns the account id for the one with the oldest call
  lazy val activeAccount: Signal[Option[UserId]] = globalCallProfile.map(_.activeCall.map(_.account))
}

class CallingService(val accountId:       UserId,
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
                     permissions:         PermissionsService,
                     tracking:            TrackingService)(implicit accountContext: AccountContext) { self =>

  import CallingService._

  private implicit val dispatcher = new SerialDispatchQueue(name = "CallingService")

  //need to ensure that flow manager and media manager are initialised for v3 (they are lazy values)
  private val fm = flowManagerService.flowManager

  val callProfile = Signal(CallProfile.Empty)

  private var closingPromise = Option.empty[Promise[Unit]]

  val availableCalls = callProfile.map(_.availableCalls) //any call a user can potentially join
  //state about any call for which we should show the CallingActivity
  val currentCall: Signal[Option[CallInfo]] = callProfile.map(_.activeCall)

  //track call on all state changes - except for when the state becomes None, this will be handled in onClosedCall
  currentCall.map(_.flatMap(_.state)).onChanged {
    case Some(_) => currentCall.currentValue.flatten.foreach(tracking.trackCallState(accountId, _))
    case _ => //
  }

  //exposed for tests only
  lazy val wCall = returningF(avs.registerAccount(this)) { call =>
    call.onFailure {
      case NonFatal(e) => error(s"Failed to initialise WCall for user: $accountId", e)
    }
  }

  Option(ZMessaging.currentAccounts).foreach(
    _.accountsWithManagers.map(_.contains(accountId)) {
      case false =>
        verbose(s"Account $accountId logged out, unregistering from AVS")
        wCall.map(avs.unregisterAccount)
      case true =>
    }(EventContext.Global)
  )

  callProfile.onChanged { p =>
    verbose(s"Call profile changed. active call: ${p.activeCall}, non active calls: ${p.nonActiveCalls}")
    p.activeCall.foreach(i => if (i.state.contains(SelfCalling)) CallWakeService(context, accountId, i.convId))
  }

  def onSend(ctx: Pointer, convId: RConvId, userId: UserId, clientId: ClientId, msg: String) = {
    withConv(convId) { (_, conv) =>
      sendCallMessage(conv.id, GenericMessage(Uid(), GenericContent.Calling(msg)), ctx)
    }
  }

  /**
    * @param shouldRing "Also we give you a bool to indicate whether you should ring in incoming. its always true in 1:1,
    *                   true if someone called recently for group but false if the call was started more than 30 seconds ago"
    *                                                                                                               - Chris the All-Knowing.
    */
  def onIncomingCall(convId: RConvId, userId: UserId, videoCall: Boolean, shouldRing: Boolean) = withConvAndIsGroup(convId) { (_, conv, isGroup) =>
    verbose(s"Incoming call from $userId in conv: $convId (should ring: $shouldRing)")

    permissions.allPermissions(Set(CAMERA)).head.foreach { granted =>
      updateCallInfo(conv.id, _.copy(videoSendState = (videoCall, granted) match {
        case (true, false) => VideoState.NoCameraPermission
        case (true, true)  => VideoState.Started
        case _             => VideoState.Stopped
      }))("onIncomingCall-permissionCheck")
    }

    val newCall = CallInfo(
      conv.id,
      accountId,
      isGroup,
      userId,
      Some(OtherCalling),
      others = Map(userId -> Some(LocalInstant.Now)),
      startedAsVideoCall = videoCall,
      videoSendState = VideoState.NoCameraPermission)

    callProfile.mutate { p =>
      val newActive = p.activeId match {
        case None if shouldRing =>
          Some(newCall.convId)
        case _ =>
          verbose(s"Incoming call from $userId while in a call or call shouldn't ring - ignoring")
          p.activeId
      }
      p.copy(activeId = newActive, availableCalls = p.availableCalls + (newCall.convId -> newCall))
    }
  }

  def onOtherSideAnsweredCall(convId: RConvId) = withConv(convId) { (_, conv) =>
    verbose(s"outgoing call answered for conv: ${conv.id}")
    updateActiveCall {
      case call if call.convId == conv.id => call.updateCallState(SelfJoining)
      case call => warn("Other side answered non-active call, ignoring"); call
    }("onOtherSideAnsweredCall")
  }

  def onMissedCall(convId: RConvId, time: RemoteInstant, userId: UserId, videoCall: Boolean) = {
    verbose(s"Missed call for conversation: $convId at $time from user $userId. Video: $videoCall")
    messagesService.addMissedCallMessage(convId, userId, time)
  }

  def onEstablishedCall(convId: RConvId, userId: UserId) = withConv(convId) { (_, conv) =>
    verbose(s"call established for conv: ${conv.id}, userId: $userId, time: ${clock.instant}")
    updateActiveCall { c =>
      setVideoSendState(conv.id, c.videoSendState) //will upgrade call videoSendState
      setCallMuted(c.muted) //Need to set muted only after call is established
      //on est. group call, switch from self avatar to other user now in case `onGroupChange` is delayed
      val others = c.others + (userId -> Some(LocalInstant.Now)) - accountId
      c.updateCallState(SelfConnected).copy(others = others, maxParticipants = others.size + 1)
    }("onEstablishedCall")
  }

  def onClosedCall(reason: AvsClosedReason, rConvId: RConvId, time: RemoteInstant, userId: UserId) = withConv(rConvId) { (_, conv) =>
    verbose(s"call closed for reason: ${reasonString(reason)}, conv: ${conv.id} at $time, userId: $userId")
    callProfile.mutate { p =>
      val drift = pushService.beDrift.currentValue.getOrElse(Duration.ZERO)
      val nowTime = LocalInstant.Now.toRemote(drift)

      //also handles call messages based on the state the call is in directly before being closed
      val newActive = p.activeCall match {
        case Some(call) if call.convId == conv.id =>

          val endTime = LocalInstant.Now
          import AvsClosedReason._

          if (reason != AnsweredElsewhere) call.state match {
            case Some(SelfCalling) =>
              //TODO do we want a small timeout before placing a "You called" message, in case of accidental calls? maybe 5 secs
              verbose("Call timed out out the other didn't answer - add a \"you called\" message")
              messagesService.addMissedCallMessage(conv.id, accountId, nowTime)
            case Some(OtherCalling) | Some(SelfJoining) if reason == StillOngoing => // do nothing - call is still ongoing in the background
            case Some(OtherCalling) | Some(SelfJoining) | Some(Ongoing) =>
              verbose("Call timed out out and we didn't answer - mark as missed call")
              messagesService.addMissedCallMessage(conv.id, call.caller, nowTime)
            case Some(SelfConnected) =>
              verbose("Had a call, save duration as a message")
              call.estabTime.foreach(est => messagesService.addSuccessfulCallMessage(conv.id, call.caller, est.toRemote(drift), est.remainingUntil(endTime)))
            case _ =>
              warn(s"unexpected call state: ${call.state}")
          }
          //need to track here manually, since the current call will either change or be set to None
          tracking.trackCallState(accountId, call.copy(state = None, prevState = call.state, endTime = Some(endTime)), reason = Some(reason))
          //Switch to any available calls that are still incoming and should ring
          p.nonActiveCalls.filter(_.state.contains(OtherCalling)).sortBy(_.startTime).headOption.map(_.convId)
        case Some(call) =>
          verbose("A call other than the current one was closed - likely missed another incoming call.")
          messagesService.addMissedCallMessage(conv.id, userId, nowTime)
          //don't change the active call, since the close callback was for a different conv/call
          Some(call.convId)
        case None =>
          verbose("No active call to update")
          None
      }

      //Group calls that you don't answer (but are answered by other users) will be "closed" with reason StillOngoing. We need to keep these around so the user can join them later
      val callUpdated = if (reason == StillOngoing) p.availableCalls.get(conv.id).map(_.updateCallState(Ongoing)) else None
      p.copy(activeId = newActive, callUpdated.fold(p.availableCalls - conv.id)(c => p.availableCalls + (conv.id -> c)))
    }
    closingPromise.foreach(_.tryComplete(Success({})))
  }

  def onMetricsReady(convId: RConvId, metricsJson: String): Unit =
    tracking.track(AVSMetricsEvent(metricsJson), Some(accountId))

  def onConfigRequest(wcall: WCall): Int = {
    verbose("onConfigRequest")
    callingClient.getConfig.map { resp =>
      avs.onConfigRequest(wcall, resp.fold(err => err.code, _ => 0), resp.fold(_ => "", identity))
    }
    0
  }

  def onBitRateStateChanged(enabled: Boolean): Unit = {
    verbose(s"onBitRateStateChanged enabled=$enabled")
    updateActiveCall { c =>
      c.copy(isCbrEnabled = enabled)
    }("onBitRateStateChanged")
  }

  def onVideoStateChanged(userId: String, videoReceiveState: VideoState) = Serialized.apply(self) {
    CancellableFuture {
      verbose(s"video state changed: $videoReceiveState")
      updateActiveCall(_.updateVideoState(UserId(userId), videoReceiveState))("onVideoStateChanged")
    }
  }

  def onGroupChanged(convId: RConvId, members: Set[UserId]) = withConv(convId) { (_, conv) =>
    verbose(s"group members changed, convId: $convId, other members: $members")
    updateCallInfo(conv.id, { call =>

      val updated = members.map { userId =>
        userId -> call.others.getOrElse(userId, Some(LocalInstant.Now))
      }.toMap

      call.copy(others = updated, maxParticipants = math.max(call.maxParticipants, members.size + 1))
    })("onGroupChanged")
  }

  network.networkMode.onChanged { _ =>
    callProfile.head.flatMap {
      case CallProfile(Some(_), _) =>
        verbose("network mode changed during call - informing AVS")
        wCall.map(avs.onNetworkChanged)
      case _ => Future.successful({})
    }
  }

  /**
    * Either start a new call or join an active call (incoming/ongoing)
    *
    * Note: This method is called from background service, so it has to return Future which spans whole execution,
    * this ensures that CallWakeService will keep a wake lock while this function runs
    *
    * @param isVideo will be discarded if call is already active
    */
  def startCall(convId: ConvId, isVideo: Boolean = false, forceOption: Boolean = false): Future[Unit] = withConvAsync(convId) { (w, conv) =>
    verbose(s"startCall $convId, $isVideo")

    for {
      profile <- callProfile.head
      isGroup <- convsService.isGroupConversation(convId)
      mems <- members.getActiveUsers(conv.id)
      others <- {
        if (isGroup) Future.successful(Set(accountId)) //TODO: Remove this as we don't use it anymore
        else if (conv.team.isEmpty) Future.successful(Set(UserId(conv.id.str)))
        else Future.successful(mems.filter(_ != accountId).toSet)
      }.map(_.map(_ -> Some(LocalInstant.Now)).toMap)
      vbr <- userPrefs.preference(UserPreferences.VBREnabled).apply()
    } yield {
      val callType =
        if (mems.size > VideoCallMaxMembers) Avs.WCallType.ForcedAudio
        else if (isVideo) Avs.WCallType.Video
        else Avs.WCallType.Normal

      val convType = if (isGroup) Avs.WCallConvType.Group else Avs.WCallConvType.OneOnOne
      profile.activeCall match {
        case Some(call) if call.convId == convId =>
          call.state match {
            case Some(OtherCalling) =>
              verbose(s"Answering call")
              avs.answerCall(w, conv.remoteId, callType, !vbr)
              updateActiveCall(_.updateCallState(SelfJoining))("startCall/OtherCalling")
              if (forceOption)
                setVideoSendState(convId, if (isVideo)  Avs.VideoState.Started else Avs.VideoState.Stopped)
            case _ =>
              warn("Tried to join an already joined/connecting call - ignoring")
          }
        case Some(_) =>
          warn("Tried to start a new call while already in a call - ignoring")
        case None =>
          profile.availableCalls.get(convId) match {
            case Some(call) =>
              verbose("Joining an ongoing background call")
              avs.answerCall(w, conv.remoteId, callType, !vbr)
              val active = call.updateCallState(SelfJoining).copy(joinedTime = None, estabTime = None) // reset previous call state if exists
              callProfile.mutate(_.copy(activeId = Some(call.convId), availableCalls = profile.availableCalls + (convId -> active)))
              setCallMuted(muted = false)
              if (forceOption)
                setVideoSendState(convId, if (isVideo)  Avs.VideoState.Started else Avs.VideoState.Stopped)
            case None =>
              verbose("No active call, starting new call")
              avs.startCall(w, conv.remoteId, callType, convType, !vbr).map {
                case 0 =>
                  //Assume that when a video call starts, sendingVideo will be true. From here on, we can then listen to state handler
                  val newCall = CallInfo(
                    conv.id,
                    accountId,
                    isGroup,
                    accountId,
                    Some(SelfCalling),
                    others = others,
                    startedAsVideoCall = isVideo,
                    videoSendState = if (isVideo) VideoState.Started else VideoState.Stopped)
                  callProfile.mutate(_.copy(activeId = Some(newCall.convId), availableCalls = profile.availableCalls + (newCall.convId -> newCall)))
                case err => warn(s"Unable to start call, reason: errno: $err")
              }
          }
      }
    }
  }

  /**
   * @return Future as this function is called from background service
   */
  def endCall(convId: ConvId): Future[Unit] = {
    withConvAsync(convId) { (w, conv) =>
      withCallInfo(convId, { call =>
        verbose(s"endCall: $convId. Active call in state: ${call.state}")
        //avs reject and end call will always trigger the onClosedCall callback - there we handle the end of the call
        if (call.state.contains(OtherCalling)) avs.rejectCall(w, conv.remoteId) else avs.endCall(w, conv.remoteId)
      })("endCall")
    }
    returning(Promise[Unit]())(p => closingPromise = Some(p)).future
  }

  def continueDegradedCall(): Unit = currentCall.head.map {
    case Some(info) =>
      (info.outstandingMsg, info.state) match {
        case (Some((msg, ctx)), _) => convs.storage.setUnknownVerification(info.convId).map(_ => sendCallMessage(info.convId, msg, ctx))
        case (None, Some(OtherCalling)) => convs.storage.setUnknownVerification(info.convId).map(_ => startCall(info.convId))
        case _ => error(s"Tried resending message on invalid info: ${info.convId} in state ${info.state} with msg: ${info.outstandingMsg}")
      }
    case None => warn("Tried to continue degraded call without a current active call")
  }

  private def sendCallMessage(convId: ConvId, msg: GenericMessage, ctx: Pointer): Unit = withConv(convId) { (w, conv) =>
    verbose(s"Sending msg on behalf of avs: convId: $convId, msg: $msg")
    otrSyncHandler.postOtrMessage(conv, msg).map {
      case Right(_) =>
        updateActiveCall(_.copy(outstandingMsg = None))("sendCallMessage/verified")
        avs.onHttpResponse(w, 200, "", ctx)
      case Left(ErrorResponse.Unverified) =>
        warn(s"Conversation degraded, delay sending message on behalf of AVS")
        //TODO need to handle degrading of conversation during a call
        //Currently, the call will just time out...
        updateActiveCall(_.copy(outstandingMsg = Some(msg, ctx)))("sendCallMessage/unverified")
      case Left(ErrorResponse(code, errorMsg, label)) =>
        avs.onHttpResponse(w, code, errorMsg, ctx)
    }
  }

  //Drop the current call in case of incoming GSM interruption
  def onInterrupted(): Unit = {
    verbose("onInterrupted - gsm call received")
    currentCall.collect { case Some(info) => info.convId }.currentValue.foreach { convId =>
      //Ensure that conversation state is only performed INSIDE withConv
      withConv(convId)((w, conv) => avs.endCall(w, conv.remoteId))
    }
  }

  def setCallMuted(muted: Boolean): Unit = fm.foreach { f =>
    verbose(s"setCallMuted: $muted")
    updateActiveCall { c =>
      f.setMute(muted)
      c.copy(muted = muted)
    }("setCallMuted")
  }

  /**
    * This method should NOT be called before we have permissions AND while the call is still incoming. Once established,
    * we do call this call this, to convert NoCameraPermission to state Stopped.
    */
  def setVideoSendState(convId: ConvId, state: VideoState.Value): Unit = {
    withConv(convId) { (w, conv) =>
      val targetSt = state match {
        case NoCameraPermission => Stopped //NoCameraPermission is only valid for incoming
        case c => c
      }

      verbose(s"setVideoSendActive: $convId, providedState: $state, targetState: $targetSt")
      updateCallInfo(convId, { c =>
        if (state != NoCameraPermission) avs.setVideoSendState(w, conv.remoteId, targetSt)
        c.updateVideoState(accountId, targetSt)
      })("setVideoSendState")
    }
  }

  val callMessagesStage = EventScheduler.Stage[CallMessageEvent] {
    case (_, events) => Future.successful(events.sortBy(_.time).foreach { e =>
      receiveCallEvent(e.content, e.time, e.convId, e.from, e.sender)
    })
  }

  private def receiveCallEvent(msg: String, msgTime: RemoteInstant, convId: RConvId, from: UserId, sender: ClientId): Unit =
    wCall.map { w =>
      val drift = pushService.beDrift.currentValue.getOrElse(Duration.ZERO)
      val curTime = LocalInstant(clock.instant + drift)
      verbose(s"Received msg for avs: localTime: ${clock.instant} curTime: $curTime, drift: $drift, msgTime: $msgTime, msg: $msg")
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
          case _          => error(convNotFoundMsg)
        }
      }
    }
  }

  private def withConvAndIsGroup(convId: RConvId)(f: (WCall, ConversationData, Boolean) => Unit) =
    Serialized.future(self) {
      (for {
        w          <- wCall
        Some(conv) <- convs.convByRemoteId(convId)
        isGroup    <- convsService.isGroupConversation(conv.id)
      } yield f(w, conv, isGroup))
        .recover {
          case NonFatal(e) => error(s"Unknown remote convId: $convId")
        }
    }

  private def withConvAsync(convId: ConvId)(f: (WCall, ConversationData) => Future[Unit]): Future[Unit] = {
    Serialized.future(self) {
      wCall.flatMap { w =>
        convs.convById(convId) flatMap {
          case Some(conv) => f(w, conv)
          case _ => Future successful error(s"Could not find conversation: $convId")
        }
      }
    }
  }

  private def updateActiveCall(f: CallInfo => CallInfo)(caller: String) = {
    callProfile.mutate { p =>
      p.copy(availableCalls = {
        val updated = p.activeId.flatMap(p.availableCalls.get).map(f)
        updated.fold {
          warn(s"$caller tried to update active call when there was none - no change")
          p.availableCalls
        }(u => p.availableCalls + (u.convId -> u))
      })
    }
  }

  private def updateCallInfo(convId: ConvId, f: CallInfo => CallInfo)(caller: String) = {
    callProfile.mutate { p =>
      p.copy(availableCalls = {
        val updated = p.availableCalls.get(convId).map(f)
        updated.fold {
          warn(s"$caller tried to update call info when there was none - no change")
          p.availableCalls
        }(u => p.availableCalls + (u.convId -> u))
      })
    }
  }

  private def withCallInfo(convId: ConvId, f: CallInfo => Unit)(caller: String): Future[Unit] =
    callProfile.head.map(_.availableCalls.get(convId).foreach(f))
}

object CallingService {

  val VideoCallMaxMembers: Int = 4

  /**
    * @param activeId       the id of the active call (that is, the call that should be displayed to the user), if any
    * @param availableCalls the entire list of available calls, including the active one, incoming calls, and any ongoing (group) calls
    */
  case class CallProfile(activeId: Option[ConvId], availableCalls: Map[ConvId, CallInfo]) {
    val activeCall: Option[CallInfo]  = activeId.flatMap(availableCalls.get)
    val nonActiveCalls: Seq[CallInfo] = activeId.fold(availableCalls)(availableCalls - _).values.toSeq

  }

  object CallProfile {
    val Empty = CallProfile(None, Map.empty)
  }

  case class GlobalCallProfile(activeId: Option[(UserId, ConvId)], availableCalls: Map[(UserId, ConvId), CallInfo]) {
    val activeCall: Option[CallInfo]  = activeId.flatMap(availableCalls.get)
    val nonActiveCalls: Seq[CallInfo] = activeId.fold(availableCalls)(availableCalls - _).values.toSeq

  }

  object GlobalCallProfile {
    val Empty = GlobalCallProfile(None, Map.empty)
  }

}



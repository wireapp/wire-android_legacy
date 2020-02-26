/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.calling.controllers

import android.os.{Build, PowerManager}
import android.telephony.{PhoneStateListener, TelephonyManager}
import com.waz.api.Verification
import com.waz.avs.VideoPreview
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging.clock
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.CallState.{SelfJoining, _}
import com.waz.service.call.CallInfo.Participant
import com.waz.service.call.{CallInfo, CallingService, GlobalCallingService}
import com.waz.service.{GlobalModule, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils._
import com.waz.utils.events._
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.controllers.{SoundController, ThemeController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DeprecationUtils
import com.waz.zclient.{Injectable, Injector, R, WireContext}
import org.threeten.bp.Instant

import scala.concurrent.duration._

class CallController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext)
  extends Injectable with DerivedLogTag {

  import Threading.Implicits.Background
  import VideoState._

  private lazy val screenManager  = new ScreenManager
  private lazy val soundController = inject[SoundController]
  private lazy val convController = inject[ConversationController]

  inject[GlobalPreferences].apply(GlobalPreferences.SkipTerminatingState) := true

  val callControlsVisible = Signal(false)

  //the zms of the account that currently has an active call (if any)
  val callingZmsOpt =
    for {
      acc <- inject[GlobalModule].calling.activeAccount
      zms <- acc.fold(Signal.const(Option.empty[ZMessaging]))(id => Signal.future(ZMessaging.currentAccounts.getZms(id)))
    } yield zms

  val callingZms = callingZmsOpt.collect { case Some(z) => z }

  val currentCallOpt: Signal[Option[CallInfo]] = callingZmsOpt.flatMap {
    case Some(z) => z.calling.currentCall
    case _       => Signal.const(None)
  }

  new GSMManager(currentCallOpt.map(_.isDefined))

  val currentCall   = currentCallOpt.collect { case Some(c) => c }
  val callConvIdOpt = currentCallOpt.map(_.map(_.convId))

  val isCallActive      = currentCallOpt.map(_.isDefined)
  val isCallActiveDelay = isCallActive.flatMap {
    case true  => Signal.future(CancellableFuture.delay(300.millis).future.map(_ => true)).orElse(Signal.const(false))
    case false => Signal.const(false)
  }

  private var lastCallZms = Option.empty[ZMessaging]
  callingZmsOpt.onUi { zms =>
    lastCallZms.foreach(_.flowmanager.setVideoPreview(null))
    lastCallZms = zms
  }

  val callStateOpt          = currentCallOpt.map(_.map(_.state))
  val callState             = callStateOpt.collect { case Some(s) => s }
  val callStateCollapseJoin = currentCall.map(_.stateCollapseJoin)

  val isCallEstablished     = callStateOpt.map(_.contains(SelfConnected))
  val isCallOutgoing        = callStateOpt.map(_.contains(SelfCalling))
  val isCallIncoming        = callStateOpt.map(_.contains(OtherCalling))

  val callConvId            = currentCall.map(_.convId)
  val isMuted               = currentCall.map(_.muted)
  val callerId              = currentCall.map(_.caller)
  val startedAsVideo        = currentCall.map(_.startedAsVideoCall)
  val isVideoCall           = currentCall.map(_.isVideoCall)
  val videoSendState        = currentCall.map(_.videoSendState)
  val videoReceiveStates    = currentCall.map(_.videoReceiveStates)
  val allVideoReceiveStates = currentCall.map(_.allVideoReceiveStates)
  val isGroupCall           = currentCall.map(_.isGroup)
  val cbrEnabled            = currentCall.map(_.isCbrEnabled)
  val duration              = currentCall.flatMap(_.durationFormatted)
  val otherParticipants     = currentCall.map(_.otherParticipants)

  val lastCallAccountId: SourceSignal[UserId] = Signal()
  currentCall.map(_.selfParticipant.userId) { selfUserId => lastCallAccountId ! selfUserId }

  val theme: Signal[Theme] = isVideoCall.flatMap {
    case true  => Signal.const(Theme.Dark)
    case false => inject[ThemeController].currentTheme
  }

  private val mergedVideoStates: Signal[Map[UserId, Set[VideoState]]] = {
    allVideoReceiveStates.map(_.groupBy(_._1.userId).mapValues(_.values.toSet))
  }

  def participantInfos(take: Option[Int] = None): Signal[Vector[CallParticipantInfo]] =
    for {
      cZms         <- callingZms
      participants <- orderedParticipants(take)
      ids           = participants.map(_.userId)
      users        <- cZms.usersStorage.listSignal(ids)
      videoStates  <- mergedVideoStates
    } yield users.map { user =>
      CallParticipantInfo(
        userId         = user.id,
        picture        = user.picture,
        displayName    = user.name,
        isGuest        = user.isGuest(cZms.teamId),
        isVerified     = user.isVerified,
        isExternal     = user.isExternal(cZms.teamId),
        isVideoEnabled = videoStates.get(user.id).exists(_.intersect(Set(Started, ScreenShare)).nonEmpty),
        isSelf         = cZms.selfUserId == user.id
      )
    }

  private def orderedParticipants(take: Option[Int] = None): Signal[Seq[Participant]] = {
    otherParticipants.map { participants =>
      val orderedByTimeDescending = participants.toSeq.sortBy(_._2.getOrElse(LocalInstant.Epoch)).reverse.map(_._1)
      take.fold(orderedByTimeDescending)(orderedByTimeDescending.take)
    }
  }

  val flowManager = callingZms.map(_.flowmanager)

  def continueDegradedCall(): Unit = callingServiceAndCurrentConvId.head.map {
    case (cs, _) => cs.continueDegradedCall()
  }

  val captureDevices = flowManager.flatMap(fm => Signal.future(fm.getVideoCaptureDevices))

  //TODO when I have a proper field for front camera, make sure it's always set as the first one
  val currentCaptureDeviceIndex = Signal(0)

  val currentCaptureDevice = captureDevices.zip(currentCaptureDeviceIndex).map {
    case (devices, devIndex) if devices.nonEmpty => Some(devices(devIndex % devices.size))
    case _ => None
  }

  (for {
    fm     <- flowManager
    conv   <- conversation
    device <- currentCaptureDevice
    VideoState.Started <- videoSendState
  } yield (fm, conv, device)) {
    case (fm, conv, Some(currentDevice)) => fm.setVideoCaptureDevice(conv.remoteId, currentDevice.id)
    case _ =>
  }

  private val cameraFailed   = flowManager.flatMap(_.cameraFailedSig)
  private val userStorage    = callingZms.map(_.usersStorage)
  private val callingService = callingZms.map(_.calling).disableAutowiring()
  val prefs                  = callingZms.map(_.prefs)

  val callingServiceAndCurrentConvId =
    for {
      cs <- callingService
      c  <- callConvId
    } yield (cs, c)


  val conversation        = callingZms.zip(callConvId) flatMap { case (z, cId) => z.convsStorage.signal(cId) }
  val conversationName    = conversation.map(_.displayName)
  val conversationMembers = for {
    cId     <- callConvId
    members <- convController.convMembers(cId)
  } yield members

  private lazy val otherUser = Signal(isGroupCall, userStorage, otherParticipants.map(_.keys.toSeq.headOption)).flatMap {
    case (false, usersStorage, Some(participant)) =>
      // 1:1 conversation has the same id as the other user, so we can access it directly
      usersStorage.optSignal(participant.userId)
    case _ =>
      // Need a none signal to help with further signals
      Signal.const[Option[UserData]](None)
  }

  val memberForPicture: Signal[Option[UserId]] = isGroupCall.flatMap {
    case true  => Signal.const(None)
    case false =>
      for {
        self   <- callingZms.map(_.selfUserId)
        member <- conversationMembers.map(_.find(m => m._1 != self).map(_._1))
      } yield member
  }

  private lazy val lastControlsClick = Signal[(Boolean, Instant)]() //true = show controls and set timer, false = hide controls

  lazy val controlsVisible =
    (for {
      true         <- isVideoCall
      Some(est)    <- currentCall.map(_.estabTime)
      (show, last) <- lastControlsClick.orElse(Signal.const((true, clock.instant())))
      display      <- if (show) ClockSignal(3.seconds).map(c => last.max(est.instant).until(c).asScala <= 3.seconds)
                      else Signal.const(false)
    } yield display).orElse(Signal.const(true))

  def controlsClick(show: Boolean): Unit = lastControlsClick ! (show, clock.instant())

  def leaveCall(): Unit = {
    verbose(l"leaveCall")
    updateCall { case (call, cs) => cs.endCall(call.convId, skipTerminating = true) }
  }

  def toggleMuted(): Unit = {
    verbose(l"toggleMuted")
    updateCall { case (call, cs) => cs.setCallMuted(!call.muted) }
  }

  def toggleVideo(): Unit = {
    verbose(l"toggleVideo")
    updateCall { case (call, cs) =>
      import VideoState._
      cs.setVideoSendState(call.convId, if (call.videoSendState != Started) Started else Stopped)
    }
  }

  def setVideoPause(pause: Boolean): Unit = {
    verbose(l"setVideoPause: $pause")
    updateCall { case (call, cs) =>
      import VideoState._
      if (call.isVideoCall) {
        call.videoSendState match {
          case Started if pause => cs.setVideoSendState(call.convId, Paused)
          case Paused if !pause => cs.setVideoSendState(call.convId, Started)
          case _ =>
        }
      }
    }
  }

  private def updateCall(f: (CallInfo, CallingService) => Unit): Unit =
    for {
      Some(call) <- currentCallOpt.head
      cs  <- callingService.head
    } yield f(call, cs)

  private var _wasUiActiveOnCallStart = false

  def wasUiActiveOnCallStart = _wasUiActiveOnCallStart

  private lazy val allowedByStatus = for {
    zms          <- callingZms
    users        <- userStorage
    availability <- users.signal(zms.selfUserId).map(_.availability)
    muteSet      <- conversation.map(_.muted)
  } yield availability != Availability.Away && (availability != Availability.Busy || muteSet != MuteSet.AllMuted)

  private val onCallStarted = currentCallOpt.map(_.map(_.convId)).onChanged.filter(_.isDefined).map { _ =>
    val active = ZMessaging.currentGlobal.lifecycle.uiActive.currentValue.getOrElse(false)
    _wasUiActiveOnCallStart = active
    active
  }

  onCallStarted.on(Threading.Ui) { activeUi =>
    (for {
      incoming <- isCallIncoming.head
      allowed  <- allowedByStatus.head
      shouldDisplayOverlay = activeUi || Build.VERSION.SDK_INT < 29
    } yield (!incoming || allowed) && shouldDisplayOverlay).foreach {
      case true => CallingActivity.start(cxt)
      case false =>
    }
  }(EventContext.Global)

  (lastCallAccountId zip isCallEstablished).onChanged.filter(_._2 == true) { case (userId, _) =>
    soundController.playCallEstablishedSound(userId)
  }

  (lastCallAccountId zip isCallActive).onChanged.filter(_._2 == false) { case (userId, _) =>
    soundController.playCallEndedSound(userId)
  }

  isCallActive.onChanged.filter(_ == false).on(Threading.Ui) { _ =>
    screenManager.releaseWakeLock()
  }(EventContext.Global)

  (for {
    v            <- isVideoCall
    st           <- callStateOpt
    callingShown <- callControlsVisible
  } yield (v, callingShown, st)) {
    case (true, _, _)                       => screenManager.setStayAwake()
    case (false, true, Some(OtherCalling))  => screenManager.setStayAwake()
    case (false, true, Some(SelfCalling |
                            SelfJoining |
                            SelfConnected)) => screenManager.setProximitySensorEnabled()
    case _                                  => screenManager.releaseWakeLock()
  }

  (for {
    isMuted    <- isMuted.orElse(Signal.const(false))
    isIncoming <- isCallIncoming
    isAllowed  <- allowedByStatus
    uid        <- lastCallAccountId
  } yield (isMuted, isIncoming, isAllowed, uid)) { case (isMuted, isIncoming, isAllowed, uid) =>
    //TODO Why we call this method when isCallIncoming is false?
    soundController.setIncomingRingTonePlaying(uid, !isMuted && isIncoming && isAllowed)
  }

  private lazy val convDegraded = conversation.map(_.verified == Verification.UNVERIFIED)
    .orElse(Signal(false))
    .disableAutowiring()

  val degradationWarningText = convDegraded.flatMap {
    case false => Signal(Option.empty[String])
    case true  =>
      (for {
        zms <- callingZms
        convId <- callConvId
      } yield {
        zms.membersStorage.activeMembers(convId).flatMap { ids =>
          zms.usersStorage.listSignal(ids)
        }.map(_.filter(_.verified != Verification.VERIFIED).toList)
      }).flatten.map {
        case u1 :: u2 :: Nil =>
          Some(getString(R.string.conversation__degraded_confirmation__header__multiple_user, u1.name, u2.name))
        case l if l.size > 2 =>
          Some(getString(R.string.conversation__degraded_confirmation__header__someone))
        case List(u) =>
          //TODO handle string for case where user adds multiple clients
          Some(getQuantityString(R.plurals.conversation__degraded_confirmation__header__single_user, 1, u.name))
        case _ => None
      }
  }

  (for {
    v <- isVideoCall
    o <- isCallOutgoing
    d <- convDegraded
  } yield (v, o & !d)) { case (v, play) =>
    soundController.setOutgoingRingTonePlaying(play, v)
  }

  def setVideoPreview(view: Option[VideoPreview]): Unit =
    flowManager.head.foreach { fm =>
      verbose(l"Setting VideoPreview on Flowmanager, view: $view")
      fm.setVideoPreview(view.orNull)
    } (Threading.Ui)

  private lazy val callingUsername: Signal[String] =
    for {
      users <- userStorage
      userId <- callerId
      data <- users.signal(userId)
    } yield data.name.toUpperCase(getLocale) + " "

  val callBannerText: Signal[String] =
    (for {
      state <- callState
      isGroupCall <- isGroupCall
      userName <- callingUsername
      convName <- conversationName
      duration <- duration
      callee <- otherUser
    } yield (state, isGroupCall, userName, convName, duration, callee)).map {
      case (SelfCalling, false, _, _, _, Some(callee))  => getString(R.string.call_banner_outgoing, callee.name)
      case (SelfCalling, true, _, convName, _, _)       => getString(R.string.call_banner_outgoing, convName)
      case (OtherCalling, true, caller, convName, _, _) => getString(R.string.call_banner_incoming_group, convName, caller)
      case (OtherCalling, false, caller, _, _, _)       => getString(R.string.call_banner_incoming, caller)
      case (SelfJoining, _, _, _, _, _)                 => getString(R.string.call_banner_joining)
      case (SelfConnected, _, _, _, d, _)               => getString(R.string.call_banner_tap_to_return_to_call, d)
      case _                                            => getString(R.string.empty_string)
    }.map(_.toUpperCase(getLocale))

  val subtitleText: Signal[String] =
    (for {
      video      <- isVideoCall
      state      <- callState
      dur        <- duration
      group      <- isGroupCall
      callerId   <- callerId
      callerName <- callingZms.flatMap(_.usersStorage.signal(callerId).map(_.name).map(Option(_))).orElse(Signal.const(None))
    } yield (video, state, dur, callerName.filter(_ => group))).map {
      case (true,  SelfCalling,  _, _)  => cxt.getString(R.string.calling__header__outgoing_video_subtitle)
      case (false, SelfCalling,  _, _)  => cxt.getString(R.string.calling__header__outgoing_subtitle)
      case (_,     OtherCalling, _, Some(callerName)) => cxt.getString(R.string.calling__header__incoming_subtitle__group, callerName)
      case (true,  OtherCalling, _, _)  => cxt.getString(R.string.calling__header__incoming_subtitle__video)
      case (false, OtherCalling, _, _)  => cxt.getString(R.string.calling__header__incoming_subtitle)
      case (_,     SelfJoining,  _, _)  => cxt.getString(R.string.calling__header__joining)
      case (_,     SelfConnected, d, _) => d
      case _ => ""
    }

  def stateMessageText(participant: Participant): Signal[Option[String]] = {
    Signal(callState, cameraFailed, videoReceiveStates.map(_.getOrElse(participant, Unknown))).map { vs =>
      verbose(l"Message Text: (callstate: ${vs._1}, cameraFailed: ${vs._2}, videoState: ${vs._3}")
      (vs match {
        case (SelfCalling,   true, _)                  => Some(R.string.calling__self_preview_unavailable_long)
        case (SelfConnected, _,    BadConnection)      => Some(R.string.ongoing__poor_connection_message)
        case (SelfConnected, _,    Paused)             => Some(R.string.video_paused)
        case (OtherCalling,  _,    NoCameraPermission) => Some(R.string.calling__cannot_start__no_camera_permission__message)
        case _                                         => None
      }).map(getString)
    }
  }

  lazy val speakerButton = ButtonSignal(callingZms.map(_.mediamanager), callingZms.flatMap(_.mediamanager.isSpeakerOn)) {
    case (mm, isSpeakerSet) => mm.setSpeaker(!isSpeakerSet)
  }.disableAutowiring()
}

private class ScreenManager(implicit injector: Injector) extends Injectable with DerivedLogTag {

  private val TAG = "CALLING_WAKE_LOCK"

  private val powerManager = Option(inject[PowerManager])

  private var stayAwake = false
  private var wakeLock: Option[PowerManager#WakeLock] = None

  def setStayAwake() = {
    (stayAwake, wakeLock) match {
      case (_, None) | (false, Some(_)) =>
        this.stayAwake = true
        createWakeLock();
      case _ => //already set
    }
  }

  def setProximitySensorEnabled() = {
    (stayAwake, wakeLock) match {
      case (_, None) | (true, Some(_)) =>
        this.stayAwake = false
        createWakeLock();
      case _ => //already set
    }
  }

  private def createWakeLock() = {
    val flags = if (stayAwake)
      DeprecationUtils.WAKE_LOCK_OPTIONS
    else PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
    releaseWakeLock()
    wakeLock = powerManager.map(_.newWakeLock(flags, TAG))
    verbose(l"Creating wakelock")
    wakeLock.foreach(_.acquire())
    verbose(l"Acquiring wakelock")
  }

  def releaseWakeLock() = {
    for (wl <- wakeLock if wl.isHeld) {
      wl.release()
      verbose(l"Releasing wakelock")
    }
    wakeLock = None
  }
}

private class GSMManager(callActive: Signal[Boolean])(implicit inject: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag {

  private lazy val telephonyManager = inject[TelephonyManager]

  private var listening = false
  private lazy val listener = new PhoneStateListener {
    override def onCallStateChanged(state: Int, incomingNumber: String): Unit = {

      import TelephonyManager._
      val stateStr = state match {
        case CALL_STATE_IDLE => "idle"
        case CALL_STATE_RINGING => "ringing"
        case CALL_STATE_OFFHOOK => "offhook"
      }

      info(l"GSM call state changed: ${redactedString(stateStr)}")
      if (state == CALL_STATE_OFFHOOK) dropWireCalls()
    }
  }

  callActive.onUi {
    case false => stopListening()
    case true =>
      if (telephonyManager.getCallState == TelephonyManager.CALL_STATE_OFFHOOK) {
        info(l"GSM call in progress, leaving voice channels or v3 call")
        dropWireCalls()
      }
      else startListening()
  }

  private def startListening() = if (!listening) {
    info(l"startListening")
    telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    listening = true
  }

  private def stopListening() = if (listening) {
    info(l"stopListening")
    telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
    listening = false
  }

  private def dropWireCalls() = inject[GlobalCallingService].dropActiveCalls()
}

object CallController {
  case class CallParticipantInfo(userId:         UserId,
                                 picture:        Option[Picture],
                                 displayName:    String,
                                 isGuest:        Boolean,
                                 isVerified:     Boolean,
                                 isExternal:     Boolean,
                                 isVideoEnabled: Boolean,
                                 isSelf:         Boolean)
}

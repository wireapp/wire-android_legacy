/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.calling.controllers

import _root_.com.waz.api.VoiceChannelState._
import _root_.com.waz.service.ZMessaging
import _root_.com.waz.utils.events.{EventContext, Signal}
import android.os.PowerManager
import com.waz.api.{IConversation, KindOfCall, Verification, VoiceChannelState}
import com.waz.model.{UserId, VoiceChannelData}
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo._
import com.waz.threading.Threading
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.media.SoundController
import com.waz.zclient.{Injectable, Injector, R, WireContext}
import com.waz.ZLog.ImplicitTag._
import com.waz.zclient.utils.ContextUtils._

class GlobalCallingController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable {

  private val screenManager = new ScreenManager
  private val soundController = inject[SoundController]

  /**
    * Opt Signals - these signals should be used where empty states (i.e, Nones) are important to the devices state. For example
    * if we have no zms instance, we should not have an active call. If we don't have a conversation, we shouldn't have an active
    * call. If there is no active call, for whatever reason, there should be no calling activity, and so on. The other signals
    * derived from these ones using `collect` will not propagate empty values, but that's okay since the calling UI should be
    * torn down before they can be of use to us anyway.
    *
    * Note, any signals which are PART of another signal (zipped or flatmapped) which needs to be aware of Nones should also
    * be careful to handle Nones themselves. This is because if a signal is collected, and the value is None, the individual
    * signal becomes empty, and so prevents the group of signals - of which it is a part of - from firing...
    */

  val zmsOpt = inject[Signal[Option[ZMessaging]]]

  val v3CallOpt: Signal[Option[CallInfo]] = zmsOpt.flatMap {
    case Some(z) => z.calling.currentCall.map(Some(_))
    case _ => Signal.const(None)
  }

  val isV3Call = v3CallOpt.map {
    case Some(IsActive()) => true
    case _ => false
  }.disableAutowiring()

  val convIdOpt = isV3Call.flatMap {
    case true => v3CallOpt.map(_.flatMap(_.convId))
    case false => zmsOpt.flatMap {
      case Some(z) => z.voiceContent.ongoingAndTopIncomingChannel
      case _ => Signal.const((Option.empty[VoiceChannelData], Option.empty[VoiceChannelData]))
    }.map {
      case (ongoing, incoming) => ongoing.orElse(incoming).map(_.id)
    }
  }

  //Note, we can't rely on the channels from ongoingAndTopIncoming directly, as they only update the presence of a channel, not their internal state
  val currentChannelOpt: Signal[Option[VoiceChannelData]] = (for {
    zms <- zmsOpt
    convId <- convIdOpt
  } yield (zms, convId)).flatMap {
    case (Some(z), Some(cId)) => z.voice.voiceChannelSignal(cId).map(Some(_))
    case _ => Signal.const(None)
  }

  val callStateOpt: Signal[Option[VoiceChannelState]] = isV3Call.flatMap {
    case true => v3CallOpt.map(_.map(_.state))
    case _ => currentChannelOpt.map(_.map(_.state))
  }

  val activeCall = zmsOpt.flatMap {
    case Some(z) => callStateOpt.map {
      case Some(SELF_CALLING | SELF_JOINING | SELF_CONNECTED | OTHER_CALLING | OTHERS_CONNECTED) => true
      case _ => false
    }
    case _ => Signal.const(false)
  }

  val outgoingCall = callStateOpt.map {
    case Some(st) if st == SELF_CALLING => true
    case _ => false
  }

  val incomingCall = callStateOpt.map {
    case Some(st) if st == OTHER_CALLING => true
    case _ => false
  }

  val muted = isV3Call.flatMap {
    case true => v3CallOpt.map {
      case Some(c) => c.muted
      case _ => false
    }
    case _ => currentChannelOpt map {
      case Some(c) => c.muted
      case _ => false
    }
  }.disableAutowiring()

  val videoCall = isV3Call.flatMap {
    case true => v3CallOpt.map {
      case Some(c) => c.isVideoCall
      case _ => false
    }
    case _ => currentChannelOpt.map {
      case Some(c) => c.video.isVideoCall
      case _ => false
    }
  }.disableAutowiring()

  /**
    * ...And from here on is where only their proper value is important.
    */

  private var _wasUiActiveOnCallStart = false

  def wasUiActiveOnCallStart = _wasUiActiveOnCallStart

  val onCallStarted = activeCall.onChanged.filter(_ == true).map { _ =>
    val active = zmsOpt.flatMap(_.fold(Signal.const(false))(_.lifecycle.uiActive)).currentValue.getOrElse(false)
    _wasUiActiveOnCallStart = active
    active
  }

  onCallStarted.on(Threading.Ui) { _ =>
    CallingActivity.start(cxt)
  }(EventContext.Global)

  activeCall.onChanged.filter(_ == false).on(Threading.Ui) { _ =>
    screenManager.releaseWakeLock()
  }(EventContext.Global)

  (for {
    v <- videoCall
    st <- callStateOpt
  } yield (v, st)) {
    case (true, _) => screenManager.setStayAwake()
    case (false, Some(OTHER_CALLING)) => screenManager.setStayAwake()
    case (false, Some(SELF_CALLING | SELF_JOINING | SELF_CONNECTED)) => screenManager.setProximitySensorEnabled()
    case _ => screenManager.releaseWakeLock()
  }

  (for {
    m <- muted
    i <- incomingCall
  } yield (m, i)) { case (m, i) =>
    soundController.setIncomingRingTonePlaying(!m && i)
  }

  /**
    * From here on, put signals where we're not too worried if we handle empty states or not
    * (mostly stuff that wouldn't affect proper closing of a call).
    *
    * Most of the stuff here is just because it saves re-defining the signals in one of the sub-CallControllers
    */

  val zms = zmsOpt.collect { case Some(z) => z }

  val userStorage = zms map (_.usersStorage)
  val prefs = zms.map(_.prefs)

  val v2Service = zms.map(_.voice)

  val v3Service = zms.map(_.calling).disableAutowiring()
  val v3Call = v3Service.flatMap(_.currentCall)

  val convId = convIdOpt.collect { case Some(c) => c }

  val v2ServiceAndCurrentConvId = (for {
    vcs <- v2Service
    cId <- convId
  } yield (vcs, cId)).disableAutowiring()

  val v3ServiceAndCurrentConvId = (for {
    svc <- v3Service
    c <- convId
  } yield (svc, c)).disableAutowiring()

  val currentChannel = currentChannelOpt.collect { case Some(c) => c }

  val callState = callStateOpt.collect { case Some(s) => s }

  val conversation = zms.zip(convId) flatMap { case (z, cId) => z.convsStorage.signal(cId) }
  val conversationName = conversation map (data => if (data.convType == IConversation.Type.GROUP) data.name.filter(!_.isEmpty).getOrElse(data.generatedName) else data.generatedName)

  //not concerned about degraded conversations for calling v2
  val convDegraded = (for {
    isV3     <- isV3Call
    degraded <- conversation.map(_.verified == Verification.UNVERIFIED)
  } yield isV3 && degraded)
    .orElse(Signal(false))
    .disableAutowiring()

  val degradationWarningText = convDegraded.flatMap {
    case false => Signal("")
    case true =>
      (for {
        zms <- zms
        convId <- convId
      } yield {
        zms.membersStorage.activeMembers(convId).flatMap { ids =>
          zms.usersStorage.listSignal(ids)
        }.map(_.filter(_.verified != Verification.VERIFIED).toList)
      }).flatten.map {
        case u1 :: u2 :: _ =>
          //TODO handle more than 2 users
          getString(R.string.conversation__degraded_confirmation__header__multiple_user, u1.name, u2.name)
        case List(u) =>
          //TODO handle string for case where user adds multiple clients
          getQuantityString(R.plurals.conversation__degraded_confirmation__header__single_user, 1, u.name)
        case _ => ""
      }
  }

  val degradationConfirmationText = convDegraded.flatMap {
    case false => Signal("")
    case true => outgoingCall.map {
      case true  => R.string.conversation__degraded_confirmation__place_call
      case false => R.string.conversation__degraded_confirmation__accept_call
    }.map(getString)
  }

  (for {
    v <- videoCall
    o <- outgoingCall
    d <- convDegraded
  } yield (v, o & !d)) { case (v, play) =>
    soundController.setOutgoingRingTonePlaying(play, v)
  }

  //Use Audio view to show conversation degraded screen for calling
  val showVideoView = convDegraded.flatMap {
    case true  => Signal(false)
    case false => videoCall
  }.disableAutowiring()

  val selfUser = zms flatMap (_.users.selfUser)

  val callerId = isV3Call.flatMap {
    case true => v3Call flatMap { case info =>
      (info.others, info.state) match {
        case (_, SELF_CALLING) => selfUser.map(_.id)
        case (others, OTHER_CALLING) if others.size == 1 => Signal.const(others.head)
        case _ => Signal.empty[UserId] //TODO Dean do I need this information for other call states?
      }
    }
    case _ => currentChannel map (_.caller) flatMap (_.fold(Signal.empty[UserId])(Signal(_)))
  }
  val callerData = userStorage.zip(callerId).flatMap { case (storage, id) => storage.signal(id) }

  val groupCall = isV3Call.flatMap {
    case true => Signal.const(false)
    case _ => currentChannel map (_.tracking.kindOfCall == KindOfCall.GROUP)
  }
}

private class ScreenManager(implicit injector: Injector) extends Injectable {

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
      PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
    else PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
    releaseWakeLock()
    wakeLock = powerManager.map(_.newWakeLock(flags, TAG))
    wakeLock.foreach(_.acquire())
  }

  def releaseWakeLock() = {
    for (wl <- wakeLock if wl.isHeld) wl.release()
    wakeLock = None
  }
}


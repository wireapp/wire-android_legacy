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

import android.os.PowerManager
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.{IConversation, Verification}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState
import com.waz.service.call.CallInfo.CallState._
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DeprecationUtils
import com.waz.zclient.{Injectable, Injector, R, WireContext}

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

  //The ZMessaging of the active call, or the currently active account if there is no active call, or none if no accounts are logged in.
  val zmsOpt = {
    for {
      acc <- ZMessaging.currentGlobal.calling.activeAccount
      zms <- acc.fold(inject[Signal[Option[ZMessaging]]])(id => Signal.future(ZMessaging.currentAccounts.getZms(id)))
    } yield zms
  }

  val currentCallOpt: Signal[Option[CallInfo]] = zmsOpt.flatMap {
    case Some(z) => z.calling.currentCall
    case _ => Signal.const(None)
  }

  val convIdOpt = currentCallOpt.map(_.map(_.convId))

  val callStateOpt: Signal[Option[CallState]] = currentCallOpt.map(_.flatMap(_.state))

  val activeCall = currentCallOpt.map(_.isDefined)

  val activeCallEstablished = zmsOpt.flatMap {
    case Some(z) => callStateOpt.map {
      case Some(SelfConnected) => true
      case _ => false
    }
    case _ => Signal.const(false)
  }

  val callEnded = zmsOpt.flatMap {
    case Some(z) => callStateOpt.map {
      case None => true
      case _ => false
    }
    case _ => Signal.const(true)
  }

  val outgoingCall = callStateOpt.map {
    case Some(st) if st == SelfCalling => true
    case _ => false
  }

  val incomingCall = callStateOpt.map {
    case Some(st) if st == OtherCalling => true
    case _ => false
  }

  val muted = currentCallOpt.map {
    case Some(c) => c.muted
    case _ => false
  }.disableAutowiring()

  val videoCall = currentCallOpt.map {
    case Some(c) => c.isVideoCall
    case _ => false
  }.disableAutowiring()

  /**
    * ...And from here on is where only their proper value is important.
    */

  private var _wasUiActiveOnCallStart = false

  def wasUiActiveOnCallStart = _wasUiActiveOnCallStart

  val onCallStarted = activeCall.onChanged.filter(_ == true).map { _ =>
    val active = ZMessaging.currentGlobal.lifecycle.uiActive.currentValue.getOrElse(false)
    _wasUiActiveOnCallStart = active
    active
  }

  onCallStarted.on(Threading.Ui) { _ =>
    CallingActivity.start(cxt)
  }(EventContext.Global)

  activeCallEstablished.onChanged.filter(_ == true) { _ =>
    soundController.playCallEstablishedSound()
  }

  callEnded.onChanged.filter(_ == true) { _ =>
    soundController.playCallEndedSound()
  }

  activeCall.onChanged.filter(_ == false).on(Threading.Ui) { _ =>
    screenManager.releaseWakeLock()
  }(EventContext.Global)

  (for {
    v <- videoCall
    st <- callStateOpt
  } yield (v, st)) {
    case (true, _) => screenManager.setStayAwake()
    case (false, Some(OtherCalling)) => screenManager.setStayAwake()
    case (false, Some(SelfCalling | SelfJoining | SelfConnected)) => screenManager.setProximitySensorEnabled()
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

  val currentCall = currentCallOpt.collect { case Some(c) => c }

  val userStorage = zms map (_.usersStorage)
  val prefs = zms.map(_.prefs)

  val callingService = zms.map(_.calling).disableAutowiring()

  val convId = convIdOpt.collect { case Some(c) => c }

  val callingServiceAndCurrentConvId = (for {
    svc <- callingService
    c <- convId
  } yield (svc, c)).disableAutowiring()

  val callState = callStateOpt.collect { case Some(s) => s }

  val conversation = zms.zip(convId) flatMap { case (z, cId) => z.convsStorage.signal(cId) }
  val conversationName = conversation map (data => if (data.convType == IConversation.Type.GROUP) data.name.filter(!_.isEmpty).getOrElse(data.generatedName) else data.generatedName)

  val convDegraded = conversation.map(_.verified == Verification.UNVERIFIED)
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

  val callerId = currentCallOpt flatMap {
    case Some(info) =>
      (info.others, info.state) match {
        case (_, Some(SelfCalling)) => selfUser.map(_.id)
        case (others, Some(OtherCalling)) if others.size == 1 => Signal.const(others.head)
        case _ => Signal.empty[UserId] //TODO Dean do I need this information for other call states?
      }
    case _ => Signal.empty[UserId]
  }

  val callerData = userStorage.zip(callerId).flatMap { case (storage, id) => storage.signal(id) }

  val groupCall = conversation.map(_.convType == ConversationType.Group)

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
      DeprecationUtils.WAKE_LOCK_OPTIONS
    else PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
    releaseWakeLock()
    wakeLock = powerManager.map(_.newWakeLock(flags, TAG))
    verbose(s"Creating wakelock")
    wakeLock.foreach(_.acquire())
    verbose(s"Aquiring wakelock")
  }

  def releaseWakeLock() = {
    for (wl <- wakeLock if wl.isHeld) {
      wl.release()
      verbose(s"Releasing wakelock")
    }
    wakeLock = None
  }
}


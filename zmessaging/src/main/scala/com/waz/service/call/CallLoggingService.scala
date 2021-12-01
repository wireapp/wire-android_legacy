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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{ConvId, LocalInstant, UserId}
import com.waz.service.call.Avs.AvsClosedReason._
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.service.tracking.TrackingService
import com.waz.utils.RichWireInstant
import com.wire.signals.EventContext
import org.threeten.bp.Duration

final class CallLoggingService(selfUserId:  UserId,
                               calling:     CallingService,
                               messages:    MessagesService,
                               pushService: PushService,
                               tracking:    TrackingService)(implicit eventContext: EventContext) extends DerivedLogTag {

  private var subscribedConvs = Set.empty[ConvId]

  /**
    * Here, we listen to the changes of the set of conversation ids which have a defined CallInfo. Whenever this set
    * changes (note, we only ever add or change CallInfos, we never remove them from the Map of available calls), we then
    * subscribe to the state changes of each one, passing in the current value for each call to decide how we might react.
    */
  calling.calls.onPartialUpdate(_.keySet).foreach { calls =>
    val ids = calls.keySet
    verbose(l"Listening to calls: $ids")

    val prevIds = subscribedConvs
    val toCreate = ids -- prevIds


    toCreate.foreach { id =>
      val callSignal = calling.calls.map(_.get(id))

      callSignal.onPartialUpdate(_.map(_.state)).foreach {
        case Some(call) if call.state == Ended => onCallFinished(call)
        case _ =>
      }

      callSignal.onPartialUpdate(_.map(c => (c.state, c.endReason))).foreach {
        //We don't want to track the Terminating state, or the Ended state if we don't yet have the end reason
        case Some(call) if call.state != Terminating && (call.state != Ended || call.endReason.isDefined) =>
          tracking.trackCallState(selfUserId, call)
        case _ =>
      }
    }

    //keep track of which conversations we're already listening to to avoid multiple subscriptions
    subscribedConvs ++= toCreate
  }

  private def onCallFinished(call: CallInfo) = {
    val drift = pushService.beDrift.currentValue.getOrElse(Duration.ZERO)
    val nowTime = LocalInstant.Now.toRemote(drift)
    val endTime = LocalInstant.Now

    if (!call.endReason.contains(AnsweredElsewhere))
      (call.prevState, call.estabTime) match {
        case (_, None) =>
          verbose(l"Call was never successfully established - mark as missed call")
          messages.addMissedCallMessage(call.convId, call.caller, nowTime)
        case (_, Some(est)) =>
          val duration = est.remainingUntil(endTime)
          verbose(l"Had a call of duration: ${duration.toSeconds} seconds, save duration as a message")
          messages.addSuccessfulCallMessage(call.convId, call.caller, est.toRemote(drift), duration)
        case _ =>
          warn(l"unexpected call state: ${call.state}")
      }
  }
}


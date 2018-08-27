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

import com.waz.ZLog.{verbose, warn}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{ConvId, LocalInstant, UserId}
import com.waz.service.call.Avs.AvsClosedReason._
import com.waz.service.call.CallInfo.CallState
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.service.tracking.TrackingService
import com.waz.utils.{RichWireInstant, returning}
import com.waz.utils.events.{EventContext, EventStreamWithAuxSignal, Subscription}
import org.threeten.bp.Duration

class CallLoggingService(selfUserId:  UserId,
                         calling:     CallingService,
                         messages:    MessagesService,
                         pushService: PushService,
                         tracking:    TrackingService)(implicit eventContext: EventContext) {

  verbose("initialised")

  private var subs = Map.empty[ConvId, Subscription]

  /**
    * Here, we listen to the changes of the set of conversation ids which have a defined CallInfo. Whenever this set
    * changes (note, we only ever add or change CallInfos, we never remove them from the Map of available calls), we then
    * subscribe to the state changes of each one, passing in the current value for each call to decide how we might react.
    */
  (new EventStreamWithAuxSignal(calling.calls.map(_.keySet).onChangedWithPrevious, calling.calls)) {
    case ((prevIds, ids), Some(calls)) =>
      verbose(s"Listening to calls: $ids")
      val toCreate  = ids -- prevIds.getOrElse(Set.empty)

      def onStateChange(st: CallState, call: CallInfo) = {
        verbose(s"call: ${call.convId} changed to state: $st")

        if (st != Terminating && (st != Ended || call.endReason.isDefined)) //We don't want to track the Terminating state, or the Ended state if we don't yet have the end reason
          tracking.trackCallState(selfUserId, call)

        if (st == Ended)
          onCallFinished(call)
      }

      subs ++= toCreate.map { id =>
        id -> {
          val callSignal = calling.calls.map(_.get(id))

          returning((new EventStreamWithAuxSignal(callSignal.map(_.map(_.state)).onChanged, callSignal)) {
            case (Some(st), Some(Some(call))) => onStateChange(st, call)
            case _ =>
            //We will miss the first event since the creation of a call triggers the creation of the subscription, so we need to call onStateChange once at the start
          })(_ => calls.get(id).foreach(call => onStateChange(call.state, call)))
        }
      }

    case _ =>
  }

  private def onCallFinished(call: CallInfo) = {
    val drift = pushService.beDrift.currentValue.getOrElse(Duration.ZERO)
    val nowTime = LocalInstant.Now.toRemote(drift)
    val endTime = LocalInstant.Now

    if (!call.endReason.contains(AnsweredElsewhere))
      (call.prevState, call.estabTime) match {
        case (_, None) =>
          verbose("Call was never successfully established - mark as missed call")
          messages.addMissedCallMessage(call.convId, call.caller, nowTime)
        case (_, Some(est)) =>
          verbose("Had a call, save duration as a message")
          messages.addSuccessfulCallMessage(call.convId, call.caller, est.toRemote(drift), est.remainingUntil(endTime))
        case _ =>
          warn(s"unexpected call state: ${call.state}")
      }
  }
}


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
package com.waz
import com.waz.ZLog.LogTag
import com.waz.model.{AssetId, ConvId, IntegrationId, UserId}
import com.waz.service.ZMessaging
import com.waz.service.call.Avs.AvsClosedReason
import com.waz.service.call.CallInfo
import com.waz.service.tracking.{ContributionEvent, IntegrationAdded, TrackingEvent, TrackingService}
import com.waz.utils.events.EventStream

import scala.concurrent.Future

class DisabledTrackingService extends TrackingService {
  override def events: EventStream[(Option[ZMessaging], TrackingEvent)] = ???
  override def track(event: TrackingEvent, userId: Option[UserId]): Future[Unit] = Future.successful(())
  override def contribution(action: ContributionEvent.Action): Future[Unit] = Future.successful(())
  override def assetContribution(assetId: AssetId, userId: UserId): Future[Unit] = Future.successful(())
  override def exception(e: Throwable, description: String, userId: Option[UserId])(implicit tag: LogTag): Future[Unit] = Future.successful(())
  override def crash(e: Throwable): Future[Unit] = Future.successful(())
  override def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method): Future[Unit] = Future.successful(())
  override def integrationRemoved(integrationId: IntegrationId): Future[Unit] = Future.successful(())
  override def historyBackedUp(isSuccess: Boolean): Future[Unit] = Future.successful(())
  override def historyRestored(isSuccess: Boolean): Future[Unit] = Future.successful(())
  override def trackCallState(userId: UserId, callInfo: CallInfo): Future[Unit] = Future.successful(())
}

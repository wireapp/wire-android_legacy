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
package com.waz.sync

import com.waz.api.impl.ErrorResponse
import com.waz.model.sync.SyncJob
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync.SyncRequest.PostOpenGraphMeta
import com.waz.model.{ConvId, MessageId, RemoteInstant, SyncId}
import com.waz.service.NetworkModeService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.queue.{SyncContentUpdater, SyncExecutor, SyncScheduler}

import scala.concurrent.Future

class SyncExecutorSpec extends AndroidFreeSpec {

  val scheduler = mock[SyncScheduler]
  val content   = mock[SyncContentUpdater]
  val network   = mock[NetworkModeService]
  val handler   = mock[SyncHandler]


  scenario("Loading a deleted sync job returns failure") {
    val job = SyncJob(SyncId(), PostOpenGraphMeta(ConvId(), MessageId(), RemoteInstant(clock.instant())), priority = Priority.Low)
    (content.getSyncJob _).expects(job.id).returning(Future.successful(None))
    result(getExecutor(job)) shouldEqual SyncResult(ErrorResponse.internalError(s"No sync job found with id: ${job.id}"))
  }

  def getExecutor = {
    new SyncExecutor(scheduler, content, network, handler, tracking)
  }

}

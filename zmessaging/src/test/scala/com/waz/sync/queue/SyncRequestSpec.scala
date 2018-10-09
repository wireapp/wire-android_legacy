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
package com.waz.sync.queue

import com.waz.model.sync.SyncJob
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync.SyncRequest.RegisterPushToken
import com.waz.model.{PushToken, SyncId}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.queue.SyncJobMerger.Merged

class SyncRequestSpec extends AndroidFreeSpec {

  scenario("RegisterPushToken") {

    val job1 = SyncJob(SyncId(), RegisterPushToken(PushToken("token")), priority = Priority.High)
    val job2 = SyncJob(SyncId(), RegisterPushToken(PushToken("token2")), priority = Priority.High)

    job1.merge(job2) shouldEqual Merged(job1.copy(request = job2.request))
  }


}

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
package com.waz.sync.handler

import com.waz.model.MessageId
import com.waz.service.media.RichMediaService
import com.waz.sync.SyncResult
import com.waz.threading.Threading

import scala.concurrent.Future

class RichMediaSyncHandler(richMediaService: RichMediaService) {
  import Threading.Implicits.Background

  def syncRichMedia(id: MessageId): Future[SyncResult] =
    richMediaService.updateRichMedia(id) map {
      case Seq() => SyncResult.Success
      case errors => SyncResult(errors.maxBy(_.isFatal)) // report fatal error if exists to prevent useless retries
    }
}

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
package com.waz.service.assets

import com.waz.model.TeamId
import com.waz.model.errors.AssetContentTooLargeError

import scala.util.{Success, Try}

trait AssetRestrictionsService {
  def validate(content: Content): Try[Unit]
}

class AssetRestrictionsServiceImpl(uriHelper: UriHelper, teamId: Option[TeamId]) extends AssetRestrictionsService {

  private val maxAllowedAssetSize: Long =
    if (teamId.isEmpty) 25 * 1024 * 1024
    else 100 * 1024 * 1024

  override def validate(content: Content): Try[Unit] = {
    for {
      size <- content.getSize(uriHelper)
      _ <- if (size > maxAllowedAssetSize) Try(AssetContentTooLargeError(size, maxAllowedAssetSize)) else Success(())
    } yield ()
  }

}

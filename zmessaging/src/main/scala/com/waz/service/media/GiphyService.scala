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
package com.waz.service.media

import com.waz.ZLog.ImplicitTag._
import com.waz.model.AssetData
import com.waz.service.images.ImageAssetGenerator
import com.waz.sync.client.GiphyClient
import com.waz.threading.{CancellableFuture, Threading}

class GiphyService(client: GiphyClient) {
  import Threading.Implicits.Background

  def searchGiphyImage(keyword: String, offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[(Option[AssetData], AssetData)]] = {
    client.search(keyword, offset, limit).map {
      case Nil => Nil
      case images => images.filter{case (prev, med) => med.size <= ImageAssetGenerator.MaxGifSize}
    }
  }

  def trending(offset: Int = 0, limit: Int = 25): CancellableFuture[Seq[(Option[AssetData], AssetData)]] = {
    client.loadTrending(offset, limit).map {
      case Nil => Nil
      case images => images.filter{case (prev, med) => med.size <= ImageAssetGenerator.MaxGifSize}
    }
  }

}

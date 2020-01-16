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
package com.waz.zclient.storage

import java.net.URI

import android.support.test.filters.MediumTest
import android.support.test.runner.AndroidJUnit4
import com.waz.model._
import com.waz.service.assets
import com.waz.service.assets.{ImageDetails, LocalSource, NoEncryption, PreviewEmpty, UploadAsset}
import com.waz.service.assets.UploadAssetStorage.UploadAssetDao
import com.waz.service.assets2.{AssetStatus, _}
import com.waz.sync.client.AssetClient.Retention
import org.junit.runner.RunWith

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
@RunWith(classOf[AndroidJUnit4])
@MediumTest
class RawAssetStorageTest extends GeneralStorageTest(UploadAssetDao)(
  entities = Set(
    UploadAsset(
      id = UploadAssetId(),
      md5 = MD5(Array.emptyByteArray),
      sha = Sha256("ad34da34"),
      encryption = NoEncryption,
      encryptionSalt = None,
      localSource = Some(LocalSource(URI.create("https://github.com/wireapp/wire-android-sync-engine/pull/437"), Sha256.Empty)),
      mime = Mime.Default,
      name = "test",
      uploaded = 50,
      size = 1000,
      retention = Retention.Eternal,
      public = true,
      details = ImageDetails(Dim2(1,2)),
      assetId = None,
      status = assets.AssetStatus.Done,
      preview = PreviewEmpty
    )
//    RawAsset(
//      id = RawAssetId(),
//      sha = Sha256("ad34da34"),
//      encryption = NoEncryption,
//      source = URI.create("https://github.com/wireapp/wire-android-sync-engine/pull/437"),
//      mime = Mime.Default,
//      uploaded = 50,
//      size = 1000,
//      retention = Retention.Eternal,
//      public = false,
//      convId = None,
//      details = AudioDetails(Duration.ofDays(1), Loudness(Vector(0.4f, 0.5f, 0.6f))),
//      assetId = Some(AssetId())
//    ),
//    RawAsset(
//      id = RawAssetId(),
//      sha = Sha256("ad34da34"),
//      encryption = AES_CBC_Encryption(AESKey()),
//      source = URI.create("https://github.com/wireapp/wire-android-sync-engine/pull/437"),
//      mime = Mime.Default,
//      uploaded = 50,
//      size = 1000,
//      retention = Retention.Eternal,
//      public = true,
//      convId = None,
//      details = VideoDetails(Dim2(1,2), Duration.ofDays(1)),
//      assetId = None
//    )
  )
)

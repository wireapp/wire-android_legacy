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
import com.waz.service.assets.{Asset, ImageDetails, LocalSource, NoEncryption}
import com.waz.service.assets.AssetStorageImpl.AssetDao
import com.waz.service.assets2._
import org.junit.runner.RunWith

@RunWith(classOf[AndroidJUnit4])
@MediumTest
class AssetStorageTest extends GeneralStorageTest(AssetDao)(
  entities = Set(
    Asset(
      id = AssetId(),
      token = Some(AssetToken("some_token_1")),
      sha = Sha256("ad34da34"),
      encryption = NoEncryption,
      localSource = Some(LocalSource(URI.create("https://github.com/wireapp/wire-android-sync-engine/pull/437"), Sha256.Empty)),
      name = "test_asset",
      size = 225,
      convId = None,
      preview = None,
      details = ImageDetails(Dim2(1,2)),
      mime = Mime.Unknown
    )
//    Asset(
//      id = AssetId(),
//      token = Some(AssetToken("some_token_2")),
//      sha = Sha256("ad34da34"),
//      encryption = NoEncryption,
//      localSource = Some(URI.create("https://github.com/wireapp/wire-android-sync-engine/pull/437")),
//      convId = Some(RConvId()),
//      preview = None,
//      name = "test_asset",
//      size = 225,
//      details = AudioDetails(Duration.ofDays(1), Loudness(Vector(0.4f, 0.5f, 0.6f))),
//      messageId = Some(MessageId())
//    ),
//    Asset(
//      id = AssetId(),
//      token = Some(AssetToken("some_token_3")),
//      sha = Sha256("ad34da34"),
//      encryption = AES_CBC_Encryption(AESKey()),
//      localSource = Some(URI.create("https://github.com/wireapp/wire-android-sync-engine/pull/437")),
//      convId = None,
//      preview = None,
//      details = VideoDetails(Dim2(1,2), Duration.ofDays(1))
//    )
  )
)

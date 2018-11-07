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
package com.waz.utils.crypto

import com.waz.content.AssetsStorage
import com.waz.model.{RAssetId, RemoteInstant}
import com.waz.specs.AndroidFreeSpec

/**
  * This test class checks that our hashing implementation matches that defined in
  * https://github.com/wearezeta/documentation/blob/master/topics/replies/use-cases/004-quote-hash.md
  */
class ReplyHashSpec extends AndroidFreeSpec {

  private val timestamp1 = RemoteInstant.ofEpochMilli(1540213769)
  private val timestamp2 = RemoteInstant.ofEpochMilli(1540213965)

  private val rt = RemoteInstant.Epoch

  val assetStorage = mock[AssetsStorage]
  val base64 = new JVMBase64

  feature("hashing of quoted messages") {

    scenario("asset id 1") {
      val assetId = RAssetId.apply("3-2-1-38d4f5b9")
      getReplyHashing.hashAsset(assetId, timestamp1).hexString shouldEqual "bf20de149847ae999775b3cc88e5ff0c0382e9fa67b9d382b1702920b8afa1de"
    }

    scenario("asset id 2") {
      val assetId = RAssetId.apply("3-3-3-82a62735")
      getReplyHashing.hashAsset(assetId, timestamp2).hexString shouldEqual "2235f5b6c00d9b0917675399d0314c8401f0525457b00aa54a38998ab93b90d6"
    }

    scenario("emojis") {
      val str = "Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67!"
      val hash = getReplyHashing.hashTextReply(str, timestamp1).hexString
      hash shouldEqual "4f8ee55a8b71a7eb7447301d1bd0c8429971583b15a91594b45dee16f208afd5"
    }

    scenario("link text") {
      val str = "https://www.youtube.com/watch?v=DLzxrzFCyOs"
      val hash = getReplyHashing.hashTextReply(str, timestamp1).hexString
      hash shouldEqual "ef39934807203191c404ebb3acba0d33ec9dce669f9acec49710d520c365b657"
    }

    scenario("markdown") {
      val str = "This has **markdown**"
      val hash = getReplyHashing.hashTextReply(str, timestamp2).hexString
      hash shouldEqual "f25a925d55116800e66872d2a82d8292adf1d4177195703f976bc884d32b5c94"
    }

    scenario("arabic") {
      val str = "بغداد"
      val hash = getReplyHashing.hashTextReply(str, timestamp2).hexString
      hash shouldEqual "5830012f6f14c031bf21aded5b07af6e2d02d01074f137d106d4645e4dc539ca"
    }

    scenario("check location 1") {
      val lat: Float = 52.5166667.toFloat
      val lng: Float = 13.4.toFloat
      getReplyHashing.hashLocation(lat, lng, timestamp1).hexString shouldEqual "56a5fa30081bc16688574fdfbbe96c2eee004d1fb37dc714eec6efb340192816"
    }

    scenario("check location 2") {
      val lat: Float = 51.509143.toFloat
      val lng: Float = -0.117277.toFloat
      getReplyHashing.hashLocation(lat, lng, timestamp1).hexString shouldEqual "803b2698104f58772dbd715ec6ee5853d835df98a4736742b2a676b2217c9499"
    }

  }

  private def getReplyHashing = new ReplyHashingImpl(assetStorage)(base64)

}

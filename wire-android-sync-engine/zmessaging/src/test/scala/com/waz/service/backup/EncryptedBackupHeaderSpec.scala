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
package com.waz.service.backup

import com.waz.specs.AndroidFreeSpec

class EncryptedBackupHeaderSpec extends AndroidFreeSpec {

  import EncryptedBackupHeader._

  feature("Serializing EncryptedBackupHeader") {
    scenario("serializing to and from byte array should work") {
      val salt = Array.fill[Byte](saltLength)(1)
      val uuidHash = Array.fill[Byte](uuidHashLength)(2)
      val opslimit = 3
      val memlimit = 3
      val header = EncryptedBackupHeader(currentVersion, salt, uuidHash, opslimit, memlimit)

      val result = parse(serializeHeader(header))

      result should not be empty
      val r = result.get
      r.version shouldBe currentVersion
      r.salt should contain theSameElementsInOrderAs salt
      r.uuidHash should contain theSameElementsInOrderAs uuidHash
      r.opslimit shouldEqual opslimit
      r.memlimit shouldEqual memlimit
    }
  }
}

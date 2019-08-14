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
package com.waz.sync.client

import com.waz.model._
import org.json.JSONObject
import org.scalatest.{FeatureSpec, Ignore, Matchers}

class VersionBlacklistClientSpec extends FeatureSpec with Matchers {
  val blacklistResponse =
    """
      |{
      |  "oldestAccepted": 13,
      |  "blacklisted": [
      |    18, 23, 25
      |  ]
      |}
    """.stripMargin

  feature("Response parsing") {
    scenario("Parse version blacklist response") {
      val response = VersionBlacklist.Decoder(new JSONObject(blacklistResponse))

      response.oldestAccepted shouldEqual 13
      response.blacklisted shouldEqual Seq(18, 23, 25)
    }
  }
}

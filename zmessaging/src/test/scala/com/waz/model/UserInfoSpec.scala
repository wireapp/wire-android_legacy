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
package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.threeten.bp.Instant

class UserInfoSpec extends AndroidFreeSpec {

  feature("Deserialization from JSON") {

    scenario("JSON with 'expires_at'") {

      // GIVEN
      val expectedTime = RemoteInstant(Instant.parse("2019-02-20T14:32:09.329Z"))
      val document =
        """
          |{
          |  "expires_at" : "2019-02-20T14:32:09.329Z",
          |  "id" : "e902e865-7564-4bd9-9789-d2395a984922",
          |  "picture" : [
          |
          |  ],
          |  "assets" : [
          |
          |  ],
          |  "name" : "Atticus",
          |  "accent_id" : 6
          |}
        """.stripMargin

      // WHEN
      val info: UserInfo = JsonDecoder.decode[UserInfo](document)

      // THEN
      info.expiresAt.shouldEqual(Some(expectedTime))
      info.id.shouldEqual(UserId("e902e865-7564-4bd9-9789-d2395a984922"))
      info.name.shouldEqual(Some(Name("Atticus")))
      info.accentId.shouldEqual(Some(6))

    }

    scenario("JSON with 'qualified_id'") {
      // GIVEN
      val document =
        """
          |{
          |  "qualified_id":{"domain":"staging.zinfra.io","id":"e902e865-7564-4bd9-9789-d2395a984922"},
          |  "id" : "e902e865-7564-4bd9-9789-d2395a984922",
          |  "picture" : [
          |
          |  ],
          |  "assets" : [
          |
          |  ],
          |  "name" : "Atticus",
          |  "accent_id" : 6
          |}
          """.stripMargin

      // WHEN
      val info: UserInfo = JsonDecoder.decode[UserInfo](document)

      // THEN
      info.id.shouldEqual(UserId("e902e865-7564-4bd9-9789-d2395a984922"))
      info.domain.shouldEqual(Domain("staging.zinfra.io"))
      info.name.shouldEqual(Some(Name("Atticus")))
      info.accentId.shouldEqual(Some(6))
    }
  }

  feature("Deserialize and serialize again") {
    scenario("Deserialize and serialize again with 'qualified_id'") {
      // GIVEN
      val info = UserInfo(
        id = UserId("e902e865-7564-4bd9-9789-d2395a984922"),
        domain = Domain("staging.zinfra.io"),
        name = Some("Atticus"),
        accentId = Some(6)
      )

      // WHEN
      val document = JsonEncoder.encode(info)(UserInfo.Encoder).toString
      val info2: UserInfo = JsonDecoder.decode[UserInfo](document)

      // THEN
      info.id.shouldEqual(info2.id)
      info.domain.shouldEqual(info2.domain)
      info.name.shouldEqual(info2.name)
      info.accentId.shouldEqual(info2.accentId)
    }
  }
}

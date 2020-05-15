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

import com.waz.service.media.RichMediaContentParser.GoogleMapsLocation
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.GoogleMapsClient.StaticMapsPathBase

class GoogleMapsClientSpec extends AndroidFreeSpec {

  feature("google map previews") {

    scenario("get the map preview url") {
      // Given
      val location = GoogleMapsLocation("13.402276", "52.523842", "2")

      // When
      val actual = GoogleMapsClient.getStaticMapPath(location, 100, 200)

      // Then
      val expected = s"$StaticMapsPathBase?center=${location.x}%2C${location.y}&zoom=2&size=100x200"
      actual shouldEqual expected
    }
  }
}

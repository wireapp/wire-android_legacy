/**
  * Wire
  * Copyright (C) 2019 Wire Swiss GmbH
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

package org.robolectric.shadows

import android.security.NetworkSecurityPolicy
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements


@Implements(classOf[NetworkSecurityPolicy]) object ShadowNetworkSecurityPolicy {
  @Implementation def getInstance: NetworkSecurityPolicy = try {
    val shadow = Class.forName("android.security.NetworkSecurityPolicy")
    shadow.newInstance.asInstanceOf[NetworkSecurityPolicy]
  } catch {
    case e: Exception =>
      throw new AssertionError
  }
}

@Implements(classOf[NetworkSecurityPolicy]) class ShadowNetworkSecurityPolicy {
  @Implementation def isCleartextTrafficPermitted = true

  @Implementation def isCleartextTrafficPermitted(host: String) = true
}

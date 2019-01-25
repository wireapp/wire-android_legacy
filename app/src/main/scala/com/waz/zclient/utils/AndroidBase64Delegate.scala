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
package com.waz.zclient.utils

import android.util.Base64
import com.waz.utils.SafeBase64

import scala.util.Try

class AndroidBase64Delegate extends SafeBase64.Delegate {
  override def encode(bytes: Array[Byte]): String = Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.NO_CLOSE)
  override def decode(base64: String): Try[Array[Byte]] = Try { Base64.decode(base64, Base64.DEFAULT) }
}

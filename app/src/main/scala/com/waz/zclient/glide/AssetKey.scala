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
package com.waz.zclient.glide

import java.security.MessageDigest

import com.bumptech.glide.load.{Key, Options}

case class AssetKey(key: String, width: Int, height: Int, options: Options) extends Key {

  override def hashCode(): Int = toString.hashCode

  override def equals(obj: scala.Any): Boolean = obj match {
    case ak: AssetKey =>
      ak.key == key && ak.width == width && ak.height == height && ak.options.eq(options)
    case _ =>
      false
  }

  override def toString: String = s"$key-$width-$height-$options"

  override def updateDiskCacheKey(messageDigest: MessageDigest): Unit = {
    messageDigest.update(toString.getBytes(Key.CHARSET))
  }
}

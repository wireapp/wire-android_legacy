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
package com.waz.utils

import java.util.{EnumMap, HashMap}

import scala.reflect.ClassTag

trait EnumCodec[A, B] {
  def encode(a: A): B
  def decode(b: B): A // throws a NoSuchElementException if decoding fails
}

object EnumCodec {
  def injective[A <: Enum[A], B](f: A => B)(implicit tag: ClassTag[A]): EnumCodec[A, B] = new EnumCodec[A, B] {
    private val clazz = tag.runtimeClass.asInstanceOf[Class[A]]
    private val encoding: EnumMap[A, B] = new EnumMap(clazz)
    private val decoding = new HashMap[B, A]

    clazz.getEnumConstants.foreach { a =>
      val encoded = f(a)
      require(encoded != null, s"$a of ${clazz.getName} maps to null, which is not allowed")
      require(! decoding.containsKey(encoded), s"function not injective - both ${decoding.get(encoded)} and $a of ${clazz.getName} map to '$encoded'")
      encoding.put(a, encoded)
      decoding.put(encoded, a)
    }

    def encode(a: A): B = encoding.get(a)

    def decode(b: B): A = {
      val decoded = decoding.get(b)
      if (decoded eq null) throw new NoSuchElementException(s"no enum value of ${clazz.getName} for identifier '$b'")
      else decoded
    }
  }
}

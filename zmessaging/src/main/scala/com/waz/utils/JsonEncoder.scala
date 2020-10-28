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

import java.util.Date

import com.waz.api.IConversation.{Access, AccessRole}
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant

import scala.collection.GenTraversable

trait JsonEncoder[A] { self =>
  def apply(v: A): JSONObject

  def comap[B](f: B => A): JsonEncoder[B] = JsonEncoder.lift(b => self(f(b)))
}

object JsonEncoder {

  def arr[A](items: GenTraversable[A])(implicit enc: JsonEncoder[A]): JSONArray = returning(new JSONArray)(arr => items.foreach(item => arr.put(enc(item))))

  def array[A](items: GenTraversable[A])(enc: (JSONArray, A) => Unit): JSONArray = returning(new JSONArray)(arr => items.foreach(enc(arr, _)))

  def arrString(items: Seq[String]): JSONArray = returning(new JSONArray) { arr => items foreach arr.put }

  def arrNum[A: Numeric](items: Seq[A]): JSONArray = returning(new JSONArray) { arr => items foreach arr.put }

  def apply(encode: JSONObject => Unit): JSONObject = returning(new JSONObject)(encode)

  def build[A](f: A => JSONObject => Unit): JsonEncoder[A] = lift(a => returning(new JSONObject)(f(a)))

  def lift[A](f: A => JSONObject): JsonEncoder[A] = new JsonEncoder[A] {
    override def apply(a: A): JSONObject = f(a)
  }

  def encode[A: JsonEncoder](value: A): JSONObject = implicitly[JsonEncoder[A]].apply(value)

  def encodeString[A: JsonEncoder](value: A): String = encode(value).toString

  def encodeDate(date: Date): String = JsonDecoder.utcDateFormat.get().format(date)
  def encodeInstant(instant: Instant): Long = instant.toEpochMilli

  def encodeISOInstant(time: Instant): String = encodeDate(new Date(time.toEpochMilli))

  def encodeAccess(a: Set[Access]): JSONArray = JsonEncoder.array(a)((arr, e) => arr.put(e.name().toLowerCase()))
  def encodeAccessRoleOpt(a: Option[AccessRole]): String = a.map(encodeAccessRole).getOrElse("")
  def encodeAccessRole(a: AccessRole): String = a.name().toLowerCase()

}

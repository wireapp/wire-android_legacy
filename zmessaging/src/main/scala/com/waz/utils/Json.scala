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

import org.json
import org.json.{JSONArray, JSONObject}

import scala.util.Try

object Json {

  object syntax {

    def decode[T](json: JSONObject)(implicit decoder: JsonDecoder[T]): Try[T] = Try(decoder(json))

    def decode[T](str: String)(implicit decoder: JsonDecoder[T]): Try[T] = Try(new JSONObject(str)).flatMap((o: JSONObject) => decode(o))

    def decodeUnsafe[T](json: JSONObject)(implicit decoder: JsonDecoder[T]): T = decode(json).get

    def decodeUnsafe[T](str: String)(implicit decoder: JsonDecoder[T]): T = decodeUnsafe(new JSONObject(str))

    implicit class Encodable[T](value: T) {
      def toJson(implicit encoder: JsonEncoder[T]): JSONObject = encoder(value)
      def toJsonString(implicit encoder: JsonEncoder[T]): String = toJson.toString
    }

  }

  // TODO: re-implement as macro for typesafety and performance
  def apply(entries: (String, Any)*): JSONObject = apply(entries.toMap)

  def apply(seq: Iterable[Any]): JSONArray =
    returning(new JSONArray()) { arr => seq.foreach(v => arr.put(wrap(v))) }

  def apply(map: Map[String, Any]): JSONObject =
    returning(new json.JSONObject()) { obj =>
      map foreach { case (key, value) => obj.put(key, wrap(value)) }
    }

  def wrap(value: Any): AnyRef = value match {
    case null => JSONObject.NULL
    case None => JSONObject.NULL
    case o: Some[_] => wrap(o.get)
    case o: JSONObject => o
    case arr: JSONArray => arr
    case arr: Array[_] => apply(arr.toSeq)
    case seq: Seq[_] => apply(seq)
    case set: Set[_] => apply(set.toSeq)
    case map: Map[_, _] => apply(map map { case (k, v) => k.toString -> v })
    case b: Boolean => java.lang.Boolean.valueOf(b)
    case i: Int => Integer.valueOf(i)
    case b: Byte => java.lang.Byte.valueOf(b)
    case s: Short => java.lang.Short.valueOf(s)
    case l: Long => java.lang.Long.valueOf(l)
    case f: Float => java.lang.Float.valueOf(f)
    case d: Double => java.lang.Double.valueOf(d)
    case _ => value.toString
  }
}

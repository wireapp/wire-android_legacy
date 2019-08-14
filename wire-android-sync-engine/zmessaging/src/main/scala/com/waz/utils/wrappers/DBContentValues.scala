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
package com.waz.utils.wrappers

import android.content.ContentValues

import scala.collection.mutable
import scala.language.implicitConversions

abstract class DBContentValues {
  def containsKey(key: String): Boolean
  def size: Int
  def keySet: Set[String]

  def put(key: String, value: Int): Unit
  def put(key: String, value: Long): Unit
  def put(key: String, value: String): Unit
  def put(key: String, value: Boolean): Unit
  def put(key: String, value: Double): Unit
  def put(key: String, value: Float): Unit

  protected def remove(key: String): Unit
  protected def clear(): Unit

  def getAsInt(key: String): Int
  def getAsLong(key: String): Long
  def getAsString(key: String): String
  def getAsBoolean(key: String): Boolean
  def getAsDouble(key: String): Double
  def getAsFloat(key: String): Float

  override def toString = "{" + keySet.map( key => s"$key:${getAsString(key)}" + "}" ).mkString(",")
}

class DBContentValuesWrapper(val values: ContentValues) extends DBContentValues {
  def containsKey(key: String) = values.containsKey(key)
  def size = values.size
  def keySet = {
    val set = mutable.HashSet[String]()
    val it = values.keySet().iterator()
    while(it.hasNext) set += it.next()
    set.toSet
  }

  override def put(key: String, value: Int) = values.put(key, java.lang.Integer.valueOf(value))
  override def put(key: String, value: Long) = values.put(key, java.lang.Long.valueOf(value))
  override def put(key: String, value: String) = values.put(key, java.lang.String.valueOf(value))
  override def put(key: String, value: Boolean) = values.put(key, java.lang.Boolean.valueOf(value))
  override def put(key: String, value: Double) = values.put(key, java.lang.Double.valueOf(value))
  override def put(key: String, value: Float) = values.put(key, java.lang.Float.valueOf(value))

  override def getAsInt(key: String) = values.getAsInteger(key)
  override def getAsLong(key: String) = values.getAsLong(key)
  override def getAsString(key: String) = values.getAsString(key)
  override def getAsBoolean(key: String) = values.getAsBoolean(key)
  override def getAsDouble(key: String) = values.getAsDouble(key)
  override def getAsFloat(key: String) = values.getAsFloat(key)

  override def remove(key: String) = values.remove(key)
  override def clear() = values.clear()
}

class DBContentValuesMap(private val map: mutable.HashMap[String, Any]) extends DBContentValues {
  override def containsKey(key: String) = map.contains(key)
  override def size = map.size
  override def keySet = map.keySet.toSet

  override def put(key: String, value: Int) = map.put(key, value)
  override def put(key: String, value: Long) = map.put(key, value)
  override def put(key: String, value: String) = map.put(key, value)
  override def put(key: String, value: Boolean) = map.put(key, value)
  override def put(key: String, value: Double) = map.put(key, value)
  override def put(key: String, value: Float) = map.put(key, value)

  override def getAsInt(key: String) = map(key).asInstanceOf[Int]
  override def getAsLong(key: String) = map(key).asInstanceOf[Long]
  override def getAsString(key: String) = map(key).asInstanceOf[String]
  override def getAsBoolean(key: String) = map(key).asInstanceOf[Boolean]
  override def getAsDouble(key: String) = map(key).asInstanceOf[Double]
  override def getAsFloat(key: String) = map(key).asInstanceOf[Float]

  override protected def remove(key: String) = map.remove(key)
  override protected def clear() = map.clear()
}

object DBContentValuesMap {
  def apply() = new DBContentValuesMap(mutable.HashMap[String, Any]())
}

object DBContentValues {
  def apply(values: ContentValues) = new DBContentValuesWrapper(values)
  def apply() = new DBContentValuesMap(mutable.HashMap())

  implicit def fromAndroid(values: ContentValues): DBContentValues = apply(values)
  implicit def toAndroid(values: DBContentValues): ContentValues = values match {
    case wrapper: DBContentValuesWrapper => wrapper.values
    case _ => throw new IllegalArgumentException(s"Expected Android ContentValues, but tried to unwrap: ${values.getClass.getName}")
  }
}

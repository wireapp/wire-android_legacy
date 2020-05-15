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

import java.io.Closeable

import android.database.Cursor

import scala.language.implicitConversions

trait DBCursor extends Closeable {
  def getCount: Int
  def moveToPosition(position: Int): Boolean
  def moveToFirst(): Boolean
  def moveToLast(): Boolean
  def moveToNext(): Boolean
  def moveToPrevious(): Boolean
  def isFirst: Boolean
  def isLast: Boolean
  def isBeforeFirst: Boolean
  def isAfterLast: Boolean
  def getColumnIndex(columnName: String): Int
  def getColumnName(columnIndex: Int): String
  def getColumnNames: Array[String]
  def getColumnCount: Int
  def getBlob(columnIndex: Int): Array[Byte]
  def getString(columnIndex: Int): String
  def getShort(columnIndex: Int): Short
  def getInt(columnIndex: Int): Int
  def getLong(columnIndex: Int): Long
  def getFloat(columnIndex: Int): Float
  def getDouble(columnIndex: Int): Double
  def getType(columnIndex: Int): Int
  def isNull(columnIndex: Int): Boolean
  def close(): Unit
  def isClosed: Boolean
}

class DBCursorWrapper(val cursor: Cursor) extends DBCursor {
  override def getCount = cursor.getCount

  override def moveToPosition(position: Int) = cursor.moveToPosition(position)

  override def moveToFirst() = cursor.moveToFirst()

  override def moveToLast() = cursor.moveToLast()

  override def moveToNext() = cursor.moveToNext()

  override def moveToPrevious() = cursor.moveToPrevious()

  override def isFirst = cursor.isFirst

  override def isLast = cursor.isLast

  override def isBeforeFirst = cursor.isBeforeFirst

  override def isAfterLast = cursor.isAfterLast

  override def getColumnIndex(columnName: String) = cursor.getColumnIndex(columnName)

  override def getColumnName(columnIndex: Int) = cursor.getColumnName(columnIndex)

  override def getColumnNames = cursor.getColumnNames

  override def getColumnCount = cursor.getColumnCount

  override def getBlob(columnIndex: Int) = cursor.getBlob(columnIndex)

  override def getString(columnIndex: Int) = cursor.getString(columnIndex)

  override def getShort(columnIndex: Int) = cursor.getShort(columnIndex)

  override def getInt(columnIndex: Int) = cursor.getInt(columnIndex)

  override def getLong(columnIndex: Int) = cursor.getLong(columnIndex)

  override def getFloat(columnIndex: Int) = cursor.getFloat(columnIndex)

  override def getDouble(columnIndex: Int) = cursor.getDouble(columnIndex)

  override def getType(columnIndex: Int) = cursor.getType(columnIndex)

  override def isNull(columnIndex: Int) = cursor.isNull(columnIndex)

  override def close() = cursor.close()

  override def isClosed = cursor.isClosed
}

object DBCursor {
  def apply(cursor: Cursor): DBCursor = new DBCursorWrapper(cursor)

  implicit def fromAndroid(cursor: Cursor): DBCursor = apply(cursor)
  implicit def toAndroid(cursor: DBCursor): Cursor = cursor match {
    case wrapper: DBCursorWrapper => wrapper.cursor
    case _ => throw new IllegalArgumentException(s"Expected Android Cursor, but tried to unwrap: ${cursor.getClass.getName}")
  }
}

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

import androidx.sqlite.db.SupportSQLiteProgram

import scala.language.implicitConversions

trait DBProgram {
  def bindBlob(index: Int, value: Array[Byte]): Unit
  def bindNull(index: Int): Unit
  def bindDouble(index: Int, value: Double): Unit
  def bindLong(index: Int, value: Long): Unit
  def bindLong(index: Int, value: Int): Unit
  def bindString(index: Int, value: String): Unit
}

class SQLiteProgramWrapper(val program: SupportSQLiteProgram) extends DBProgram {
  override def bindBlob(index: Int, value: Array[Byte]): Unit = program.bindBlob(index, value)
  override def bindNull(index: Int): Unit = program.bindNull(index)
  override def bindDouble(index: Int, value: Double): Unit = program.bindDouble(index, value)
  override def bindLong(index: Int, value: Long): Unit = program.bindLong(index, value)
  override def bindLong(index: Int, value: Int): Unit = program.bindLong(index, value)
  override def bindString(index: Int, value: String): Unit = program.bindString(index, value)
}

object DBProgram {
  def apply(program: SupportSQLiteProgram): DBProgram = new SQLiteProgramWrapper(program)

  implicit def fromAndroid(program: SupportSQLiteProgram): DBProgram = apply(program)
}

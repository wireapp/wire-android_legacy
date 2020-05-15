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

import androidx.sqlite.db.SupportSQLiteStatement

import scala.language.implicitConversions

trait DBStatement extends DBProgram {
  def clearBindings(): Unit
  def execute(): Unit
  def executeUpdateDelete(): Int
  def executeInsert(): Long
  def simpleQueryForLong(): Long
  def simpleQueryForString(): String
  def close(): Unit
}

class SQLiteStatementWrapper(val statement: SupportSQLiteStatement) extends SQLiteProgramWrapper(statement) with DBStatement {
  def clearBindings() = statement.clearBindings()
  def execute() = statement.execute()
  def executeUpdateDelete = statement.executeUpdateDelete()
  def executeInsert = statement.executeInsert()
  def simpleQueryForLong = statement.simpleQueryForLong()
  def simpleQueryForString = statement.simpleQueryForString()
  def close() = statement.close()
}

object DBStatement {
  def apply(statement: SupportSQLiteStatement): DBStatement = new SQLiteStatementWrapper(statement)

  implicit def fromAndroid(statement: SupportSQLiteStatement): DBStatement = apply(statement)
}

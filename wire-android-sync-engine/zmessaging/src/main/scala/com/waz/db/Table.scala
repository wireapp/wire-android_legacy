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
package com.waz.db

import com.waz.utils.wrappers.{DB, DBContentValues, DBProgram}

class Table[A](val name: String, val columns: ColBinder[_, A]*) {
  require(columns.nonEmpty)

  columns.zipWithIndex.foreach { case (col, i) => col.index = i }

  lazy val createSql = columns.map(c => s"${c.name} ${c.col.sqlType} ${c.col.modifiers}").mkString(s"CREATE TABLE $name (", ", ", ");")

  lazy val createFtsSql = columns.map(c => s"${c.name} ${c.col.sqlType} ${c.col.modifiers}").mkString(s"CREATE VIRTUAL TABLE $name using fts3(", ", ", ");")

  lazy val insertSql = insertOr("REPLACE")
  lazy val insertOrIgnoreSql = insertOr("IGNORE")

  private def insertOr(onConflict: String) = s"INSERT OR $onConflict INTO $name (${columns.map(_.name).mkString(",")}) VALUES (${columns.map(c => "?").mkString(",")});"

  def save(obj: A): DBContentValues = {
    val values = DB.ContentValues()
    columns.foreach(_.save(obj, values))
    values
  }

  def bind(obj: A, stmt: DBProgram) = columns.foreach(_.bind(obj, stmt))
}

class TableWithId[A](name: String, columns: ColBinder[_, A]*)(idCols: => Seq[ColBinder[_, A]]) extends Table[A](name, columns:_*) {
  override lazy val createSql = columns.map(c => s"${c.name} ${c.col.sqlType} ${c.col.modifiers}").mkString(s"CREATE TABLE $name (", ", ", s"$primaryKeyDDL);")

  private def primaryKeyDDL =
    if (idCols.size == 1 && idCols.head.col.modifiers.matches("(?i).*primary key.*")) ""
    else s", PRIMARY KEY (${idCols.map(_.name).mkString(", ")})"
}

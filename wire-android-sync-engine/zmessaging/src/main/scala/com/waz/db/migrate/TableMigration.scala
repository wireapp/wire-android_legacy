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
package com.waz.db.migrate

import com.waz.db._
import com.waz.utils.wrappers.{DB, DBCursor, DBProgram}

import scala.collection._

abstract class TableMigration(from: TableDesc, to: TableDesc) { migration =>
  import language.implicitConversions

  trait Binder {
    def copy(c: DBCursor, stmt: DBProgram): Unit
  }

  case class ColBinder[A](from: Col[A], to: Col[A]) extends Binder {
    val fromIndex = migration.from.colIndex(from.name)
    val toIndex = migration.to.colIndex(to.name)
    def copy(c: DBCursor, stmt: DBProgram) = to.bind(from.load(c, fromIndex), toIndex + 1, stmt)
  }

  case class FunBinder[A](to: Col[A], f: DBCursor => A) extends Binder {
    val toIndex = migration.to.colIndex(to.name)
    override def copy(c: DBCursor, stmt: DBProgram): Unit = to.bind(f(c), toIndex + 1, stmt)
  }

  class BindingBuilder[A](toColumn: Col[A]) {
    def :=(fromCol: Col[A]): Binder = new ColBinder(fromCol, toColumn)
    def :=(f: DBCursor => A): Binder = new FunBinder(toColumn, f)
  }

  protected implicit def colToBinder[A](col: Col[A]): BindingBuilder[A] = new BindingBuilder[A](col)

  protected implicit def colToLoader[A](col: Col[A]): Function[DBCursor, A] = col.load(_, from.colIndex(col.name))

  val bindings: Seq[Binder]

  def migrate(implicit db: DB) = inTransaction { tr: Transaction =>
    var count = 0
    db.execSQL(to.createSql)
    withStatement(to.insertSql) { stmt =>
      forEachRow(db.query(from.name, from.colNames, null, null, null, null, null)) { c =>
        stmt.clearBindings()
        bindings foreach { _.copy(c, stmt) }
        stmt.execute()

        count += 1
        if (count > 10000) {
          println(s"flushing db transaction")
          count = 0
          tr.flush()
        }
      }
    }
  }
}

case class TableDesc(name: String, columns: Seq[Col[_]]) {
  lazy val colNames = columns.map(_.name).toArray
  lazy val colIndex = columns.zipWithIndex.map { case (c, i) => c.name -> i } .toMap

  lazy val createSql = columns.map(c => s"${c.name} ${c.sqlType} ${c.modifiers}").mkString(s"CREATE TABLE IF NOT EXISTS $name (", ", ", ");")
  lazy val insertSql = s"INSERT OR REPLACE INTO $name VALUES (${columns.map(_ => "?").mkString(",")})"
}

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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.utils.wrappers.{DB, DBContentValues, DBCursor}
import com.waz.utils.{Managed, returning}

import java.util.UUID
import scala.collection.{GenTraversableOnce, breakOut}
import scala.language.implicitConversions

trait Reader[A] {
  def apply(implicit c: DBCursor): A
}

object Reader {
  def apply[A](f: DBCursor => A): Reader[A] = new Reader[A] { def apply(implicit c: DBCursor): A = f(c) }
}

abstract class Dao[T, A] extends DaoIdOps[T] {
  type IdCols = Column[A]
  type IdVals = A

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]] = Vector(id)
  protected def idValueSplitter(v: IdVals): Array[String] = Array(idCol(v))

  override def getAll(ids: Set[A])(implicit db: DB): Vector[T] =
    if (ids.isEmpty) Vector.empty
    else list(db.query(table.name, null, s"${idCol.name} IN (${ids.map(idCol.col.sqlLiteral).mkString("'", "','", "'")})", null, null, null, null))
}

abstract class Dao2[T, A, B] extends DaoIdOps[T] {
  type IdCols = (Column[A], Column[B])
  type IdVals = (A, B)

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]] = Vector(id._1, id._2)
  protected def idValueSplitter(v: IdVals): Array[String] = Array(idCol._1.col.sqlLiteral(v._1), idCol._2.col.sqlLiteral(v._2))
}

abstract class Dao3[T, A, B, C] extends DaoIdOps[T] {
  type IdCols = (Column[A], Column[B], Column[C])
  type IdVals = (A, B, C)

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]] = Vector(id._1, id._2, id._3)
  protected def idValueSplitter(v: IdVals): Array[String] = Array(idCol._1.col.sqlLiteral(v._1), idCol._2.col.sqlLiteral(v._2), idCol._3.col.sqlLiteral(v._3))
}

abstract class DaoIdOps[T] extends BaseDao[T] {
  type IdCols
  type IdVals

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]]
  protected def idValueSplitter(v: IdVals): Array[String]

  val idCol: IdCols

  private lazy val builtIdCols = idColumnsQueryBuilder(idCol)
  protected def idColumnsQueryBuilder(id: IdCols): String = idColumns(id).map(c => s"${c.name} = ?").mkString(" AND ")

  def getAll(ids: Set[IdVals])(implicit db: DB): Vector[T] = {
    val builder = Vector.newBuilder[T]
    ids foreach { getById(_) foreach { builder += _ } }
    builder.result()
  }

  def getById(id: IdVals)(implicit db: DB) = single(findById(id))

  def getCursor(id: IdVals)(implicit db: DB) = findById(id)

  private def findById(id: IdVals)(implicit db: DB): DBCursor = db.query(table.name, null, builtIdCols, idValueSplitter(id), null, null, null, "1")

  def delete(id: IdVals)(implicit db: DB): Int = db.delete(table.name, builtIdCols, idValueSplitter(id))

  def deleteEvery(ids: GenTraversableOnce[IdVals])(implicit db: DB): Unit = inTransaction {
    withStatement(s"DELETE FROM ${table.name} WHERE $builtIdCols") { stmt =>
      ids.foreach { id =>
        idValueSplitter(id).zipWithIndex foreach { case (v, idx) => stmt.bindString(idx + 1, v) }
        stmt.execute()
      }
    }
  }

  def updateById(id: IdVals)(f: T => T)(implicit db: DB): Option[T] = getById(id).map(it => insertOrReplace(f(it)))

  override def Table(name: String, columns: Column[_]*) = new TableWithId(name, columns:_*)(idColumns(idCol))
}

abstract class BaseDao[T] extends Reader[T] with DerivedLogTag {
  import BaseDao._

  val table: Table[T]

  def onCreate(db: DB) = db.execSQL(table.createSql)

  def values(item: T): DBContentValues = table.save(item)

  def listCursor(implicit db: DB): DBCursor = db.query(table.name, null, null, null, null, null, null, null)

  def list(implicit db: DB): Vector[T] = list(listCursor)

  def iterating(c: => DBCursor, tag: Option[UUID] = None): Managed[Iterator[T]] = iteratingWithReader(this)(c)

  def iteratingMultiple(cursors: => Seq[DBCursor]): Managed[Iterator[T]] = iteratingMultipleWithReader(this)(cursors)

  def single(c: DBCursor, close: Boolean = true): Option[T] =
    try { if (c.moveToFirst()) Option(apply(c)) else None }
    catch { case ex: Throwable =>
      error(l"Error in db operation", ex)
      throw ex
    }
    finally { if (close) c.close() }

  def list(c: DBCursor, close: Boolean = true, filter: T => Boolean = { _ => true }): Vector[T] =
    CursorIterator.list[T](c, close, filter)(this)

  def foreach(c: DBCursor, f: T => Unit): Unit =
    try { new CursorIterator(c)(this).foreach(f) }
    catch { case ex: Throwable =>
      error(l"Error in db operation", ex)
      throw ex
    }
    finally { c.close() }

  def foreach(f: T => Unit)(implicit db: DB): Unit = {
    val c = listCursor
    try { new CursorIterator(c)(this).foreach(f) }
    catch { case ex: Throwable =>
      error(l"Error in db operation", ex)
      throw ex
    }
    finally { c.close() }
  }

  def find[A](col: Column[A], value: A, jobTag: Option[UUID] = None)(implicit db: DB): DBCursor = {
    verbose(l"SSXX<JOB:$jobTag> Dao.find")
    db.query(table.name, null, s"${col.name} = ?", Array(col(value)), null, null, null)
  }

  def findInSet[A](col: Column[A], values: Set[A])(implicit db: DB): Seq[DBCursor] = {
    // Android throws an SQLiteException if the number of variables in a query exceeds ~999.
    // To avoid this we make batched queries, returning the a list of cursors.
    values.sliding(MaxQueryVariables, MaxQueryVariables).map { chunk =>
      db.query(table.name, null, s"${col.name} IN (${chunk.iterator.map(_ => "?").mkString(", ")})", chunk.map(col(_))(breakOut): Array[String], null, null, null)
    }.toList
  }

  def delete[A](col: Column[A], value: A)(implicit db: DB): Int = db.delete(table.name, s"${col.name} = ?", Array(col(value)))

  def insertOrIgnore(items: GenTraversableOnce[T])(implicit db: DB): Unit = insertWith(table.insertOrIgnoreSql)(items)

  def insertOrReplace(items: GenTraversableOnce[T])(implicit db: DB): Unit = insertWith(table.insertSql)(items)

  private def insertWith(sql: String)(items: GenTraversableOnce[T])(implicit db: DB): Unit = inTransaction {
    withStatement(sql) { stmt =>
      items foreach { item =>
        table.bind(item, stmt)
        stmt.execute()
      }
    }
  }

  def maxWithDefault(col: String, default: Long = -1): String = s"SELECT COALESCE(MAX(${col}), $default) FROM ${table.name}"

  def queryForLong(sql: String)(implicit db: DB): Long = withStatement(sql)(_.simpleQueryForLong())

  def insertOrIgnore(item: T)(implicit db: DB): T = returning(item)(i => db.insertOrIgnore(table.name, values(i)))

  def insertOrReplace(item: T)(implicit db: DB): T = returning(item)(i => db.insertOrReplace(table.name, values(i)))

  def deleteAll(implicit db: DB): Int = db.delete(table.name, null, null)

  def Table(name: String, columns: Column[_]*) = new Table(name, columns:_*)

  type Column[A] = ColBinder[A, T]

  final implicit def columnToValue[A](col: Column[A])(implicit cursor: DBCursor): A = {
    val index = cursor.getColumnIndex(col.name)
    if (index < 0) {
      error(l"getColumnIndex returned $index for column: ${showString(col.name)}, cursor columns: ${showString(cursor.getColumnNames.mkString(","))}")
    }
    col.load(cursor, index)
  }

  def readerFor[A](col: Column[A]): Reader[A] = new Reader[A] {
    def apply(implicit c: DBCursor): A = col
  }

  final implicit class colToColumn[A](col: Col[A]) {
    def apply(extractor: T => A): Column[A] = ColBinder[A, T](col, extractor)
  }
}

object BaseDao {
  val MaxQueryVariables = 500
}

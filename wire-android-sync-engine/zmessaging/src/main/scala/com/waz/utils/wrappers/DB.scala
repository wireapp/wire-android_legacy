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

import android.content.ContentValues
import android.database.sqlite.{SQLiteDatabase, SQLiteSession}

import scala.language.implicitConversions

trait DB extends Closeable {

  // see SQLLiteClosable for comments how these should be used

  def acquireReference(): Unit

  def releaseReference(): Unit

  override def close() = releaseReference()

  // see SQLLiteDatabase for comments how these should be used

  def beginTransaction(): Unit

  def beginTransactionNonExclusive(): Unit

  def endTransaction(): Unit

  def setTransactionSuccessful(): Unit

  def inTransaction: Boolean

  def compileStatement(sql: String): DBStatement

  def query(table: String,
            columns: Array[String],
            selection: String,
            selectionArgs: Array[String],
            groupBy: String,
            having: String,
            orderBy: String,
            limit: String = null): DBCursor

  def rawQuery(sql: String, selectionArgs: Array[String]): DBCursor

  def delete(table: String, whereClause: String, whereArgs: Array[String]): Int

  def update(table: String, values: DBContentValues, whereClause: String, whereArgs: Array[String]): Int

  def execSQL(sql: String): Unit

  def execSQL(sql: String, bindArgs: Array[AnyRef]): Unit

  def isReadOnly: Boolean

  def isInMemoryDatabase: Boolean

  def isOpen: Boolean

  def needUpgrade(newVersion: Int): Boolean

  def getPath: String

  def enableWriteAheadLogging(): Boolean

  def disableWriteAheadLogging(): Unit

  def getThreadSession: DBSession

  def insertOrIgnore(tableName: String, values: DBContentValues): Unit

  def insertOrReplace(tableName: String, values: DBContentValues): Unit
}

class SQLiteDBWrapper(val db: SQLiteDatabase) extends DB {
  override def acquireReference() = db.acquireReference()

  override def releaseReference() = db.releaseReference()

  override def beginTransaction() = db.beginTransaction()

  override def beginTransactionNonExclusive() = db.beginTransactionNonExclusive()

  override def endTransaction() = db.endTransaction()

  override def setTransactionSuccessful() = db.setTransactionSuccessful()

  override def inTransaction = db.inTransaction

  def compileStatement(sql: String) = DBStatement(db.compileStatement(sql))

  override def query(table: String,
                     columns: Array[String],
                     selection: String,
                     selectionArgs: Array[String],
                     groupBy: String,
                     having: String,
                     orderBy: String,
                     limit: String = null) = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit)

  override def rawQuery(sql: String, selectionArgs: Array[String]) = db.rawQuery(sql, selectionArgs)

  override def delete(table: String, whereClause: String, whereArgs: Array[String]) = db.delete(table, whereClause, whereArgs)

  override def update(table: String, values: DBContentValues, whereClause: String, whereArgs: Array[String]) =
    db.update(table, values, whereClause, whereArgs)

  override def execSQL(sql: String) = db.execSQL(sql)

  override def execSQL(sql: String, bindArgs: Array[AnyRef]) = db.execSQL(sql, bindArgs)

  override def isReadOnly = db.isReadOnly

  override def isInMemoryDatabase = false

  override def isOpen = db.isOpen

  override def needUpgrade(newVersion: Int) = db.needUpgrade(newVersion)

  override def getPath = db.getPath

  override def enableWriteAheadLogging() = db.enableWriteAheadLogging()

  override def disableWriteAheadLogging() = db.disableWriteAheadLogging()

  override def getThreadSession = {
    val method = classOf[SQLiteDatabase].getDeclaredMethod("getThreadSession")
    method.setAccessible(true)
    method.invoke(db).asInstanceOf[SQLiteSession]
  }

  override def insertOrIgnore(tableName: String, values: DBContentValues): Unit = db.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_IGNORE)

  override def insertOrReplace(tableName: String, values: DBContentValues): Unit = db.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

trait DBUtil {
  def ContentValues(): DBContentValues
}

object AndroidDBUtil extends DBUtil {
  override def ContentValues() = new ContentValues()
}

object DB {
  private var util: DBUtil = AndroidDBUtil

  def setUtil(util: DBUtil): Unit = this.util = util

  def ContentValues(): DBContentValues = util.ContentValues()

  def apply(db: SQLiteDatabase): DB = new SQLiteDBWrapper(db)

  implicit def fromAndroid(db: SQLiteDatabase): DB = apply(db)

  implicit def toAndroid(db: DB): SQLiteDatabase = db match {
    case wrapper: SQLiteDBWrapper => wrapper.db
    case _ => throw new IllegalArgumentException(s"Expected Android DB, but tried to unwrap: ${db.getClass.getName}")
  }
}
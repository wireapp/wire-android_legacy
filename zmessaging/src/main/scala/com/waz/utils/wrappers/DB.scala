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
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.{SupportSQLiteDatabase, SupportSQLiteQueryBuilder}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag

import java.util.UUID
import scala.language.implicitConversions

trait DB extends Closeable {

  // see SQLLiteClosable for comments how these should be used

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
            limit: String = null,
            tag: Option[UUID] = None
           ): DBCursor

  def rawQuery(sql: String): DBCursor

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

  def insertOrIgnore(tableName: String, values: DBContentValues): Unit

  def insertOrReplace(tableName: String, values: DBContentValues): Unit
}

class SQLiteDBWrapper(val db: SupportSQLiteDatabase) extends DB with DerivedLogTag {

  import com.waz.log.LogSE._

  override def beginTransaction(): Unit =
    try { db.beginTransaction() }
    catch { case ex: Throwable =>
      error(l"Error on beginTransaction ", ex)
      throw ex
    }

  override def beginTransactionNonExclusive(): Unit =
    try { db.beginTransactionNonExclusive() }
    catch { case ex: Throwable =>
      error(l"Error on beginTransactionNonExclusive ", ex)
      throw ex
    }

  override def endTransaction(): Unit =
    try { db.endTransaction() }
    catch { case ex: Throwable =>
      error(l"Error on endTransaction ", ex)
      throw ex
    }

  override def setTransactionSuccessful(): Unit =
    try { db.setTransactionSuccessful() }
    catch { case ex: Throwable =>
      error(l"Error on setTransactionSuccessful ", ex)
      throw ex
    }

  override def inTransaction: Boolean =
    try { db.inTransaction }
    catch { case ex: Throwable =>
      error(l"Error on inTransaction ", ex)
      throw ex
    }

  def compileStatement(sql: String): DBStatement =
    try {
      verbose(l"DB.QUERY.LOG CompileStatement $sql")
      val statement = DBStatement(db.compileStatement(sql))
      verbose(l"DB.QUERY.LOG CompileStatement.END $sql")
      statement
    }
    catch { case ex: Throwable =>
      error(l"Error on compileStatement. sql: $sql ", ex)
      throw ex
    }

  override def query(table: String,
                     columns: Array[String],
                     selection: String,
                     selectionArgs: Array[String],
                     groupBy: String,
                     having: String,
                     orderBy: String,
                     limit: String = null,
                     tag: Option[UUID] = None
                    ): DBCursor = {
    val supportQuery = SupportSQLiteQueryBuilder.builder(table)
      .columns(columns)
      .selection(selection, selectionArgs.asInstanceOf[Array[AnyRef]])
      .groupBy(groupBy)
      .having(having)
      .orderBy(orderBy)
      .limit(limit)
      .create()
    try {
      verbose(l"DB.QUERY.LOG Query $table. Selection=${Option(selection)}; SelectionArgs=${Option(selectionArgs)}; OrderBy=${Option(orderBy)}")
      val cursor = db.query(supportQuery)
      verbose(l"DB.QUERY.LOG Query.End $table. Selection=${Option(selection)}; SelectionArgs=${Option(selectionArgs)}; OrderBy=${Option(orderBy)}")
      cursor
    }
    catch { case ex: Throwable =>
      verbose(l"<JOB:$tag> DB.query step 3")
      error(l"Error in supportQuery $supportQuery ", ex)
      throw ex
    }
  }

  override def rawQuery(sql: String): DBCursor =
    try{
      verbose(l"DB.QUERY.LOG RawQuery $sql")
      val cursor = db.query(sql)
      verbose(l"DB.QUERY.LOG RawQuery.END $sql")
      cursor
    }
    catch { case ex: Throwable =>
      error(l"Error in query $sql ", ex)
      throw ex
    }

  override def delete(table: String, whereClause: String, whereArgs: Array[String]): Int =
    try {
      verbose(l"DB.QUERY.LOG Query.Delete $table. WhereClause=${Option(whereClause)}")
      val result: Int = db.delete(table, whereClause, whereArgs.asInstanceOf[Array[AnyRef]])
      verbose(l"DB.QUERY.LOG Query.Delete.End $table. WhereClause=${Option(whereClause)}")
      result
    }
    catch { case ex: Throwable =>
      error(l"Error in delete table $table ", ex)
      throw ex
    }

  override def update(table: String, values: DBContentValues, whereClause: String, whereArgs: Array[String]): Int =
    try {
      verbose(l"DB.QUERY.LOG Update $table. WhereClause=${Option(whereClause)}")
      val result = db.update(table, SQLiteDatabase.CONFLICT_REPLACE, values, whereClause, whereArgs.asInstanceOf[Array[AnyRef]])
      verbose(l"DB.QUERY.LOG Update.END $table. WhereClause=${Option(whereClause)}")
      result
    }
    catch { case ex: Throwable =>
      error(l"Error in update table $table ", ex)
      throw ex
    }

  override def execSQL(sql: String): Unit =
    try {
      verbose(l"DB.QUERY.LOG execSQL $sql")
      db.execSQL(sql)
      verbose(l"DB.QUERY.LOG execSQL.END $sql")
      Unit
    }
    catch { case ex: Throwable =>
      error(l"Error in execSQL: $sql", ex)
      throw ex
    }

  override def execSQL(sql: String, bindArgs: Array[AnyRef]): Unit =
    try {
      verbose(l"DB.QUERY.LOG execSQL.args $sql")
      db.execSQL(sql, bindArgs)
      verbose(l"DB.QUERY.LOG execSQL.args.END $sql")
      Unit
    }
    catch { case ex: Throwable =>
      error(l"Error in execSQL: $sql", ex)
      throw ex
    }

  override def isReadOnly: Boolean = db.isReadOnly

  override def isInMemoryDatabase: Boolean = false

  override def isOpen: Boolean = db.isOpen

  override def needUpgrade(newVersion: Int): Boolean =
    try { db.needUpgrade(newVersion) }
    catch { case ex: Throwable =>
      error(l"Error on needUpgrade. NewVersion: $newVersion ", ex)
      throw ex
    }

  override def getPath: String = db.getPath

  override def enableWriteAheadLogging(): Boolean =
    try {
//      db.enableWriteAheadLogging()
      true
    }
    catch { case ex: Throwable =>
      error(l"Error in enableWriteAheadLogging ", ex)
      throw ex
    }

  override def disableWriteAheadLogging(): Unit =
    try {
      db.disableWriteAheadLogging() }
    catch { case ex: Throwable =>
      error(l"Error in disableWriteAheadLogging ", ex)
      throw ex
    }

  override def insertOrIgnore(tableName: String, values: DBContentValues): Unit =
    try {
      verbose(l"DB.QUERY.LOG Insert $tableName")
      val result = db.insert(tableName, SQLiteDatabase.CONFLICT_IGNORE, values)
      verbose(l"DB.QUERY.LOG Insert.End $tableName")
      result
    }
    catch { case ex: Throwable =>
      error(l"Error in insertOrIgnore table: $tableName", ex)
      throw ex
    }

  override def insertOrReplace(tableName: String, values: DBContentValues): Unit =
    try {
      verbose(l"DB.QUERY.LOG InsertOrReplace $tableName")
      val result = db.insert(tableName, SQLiteDatabase.CONFLICT_REPLACE, values)
      verbose(l"DB.QUERY.LOG InsertOrReplace.End $tableName")
      result
    }
    catch { case ex: Throwable =>
      error(l"Error in insertOrReplace table: $tableName", ex)
      throw ex
    }

  override def releaseReference(): Unit =
    try { db.close() }
    catch { case ex: Throwable =>
      error(l"Error while closing db ", ex)
      throw ex
    }
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

  def apply(db: SupportSQLiteDatabase): DB = new SQLiteDBWrapper(db)

  implicit def fromAndroid(db: SupportSQLiteDatabase): DB = apply(db)

  implicit def toAndroid(db: DB): SupportSQLiteDatabase = db match {
    case wrapper: SQLiteDBWrapper => wrapper.db
    case _                        => throw new IllegalArgumentException(s"Expected Android DB, but tried to unwrap: ${db.getClass.getName}")
  }
}

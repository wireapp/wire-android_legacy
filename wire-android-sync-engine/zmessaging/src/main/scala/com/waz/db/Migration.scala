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

import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.log.LogShow
import com.waz.service.tracking.TrackingService
import com.waz.utils.wrappers.DB
import androidx.room.migration.{Migration => RoomMigration}

import scala.util.control.NonFatal

trait Migration { self =>
  val fromVersion: Int
  val toVersion: Int
  
  def apply(db: DB): Unit

  def toRoomMigration: RoomMigration = new RoomMigration(fromVersion, toVersion) {
    override def migrate(database: SupportSQLiteDatabase): Unit = apply(DB(database))
  }
}

object Migration {

  implicit val MigrationLogShow: LogShow[Migration] =
    LogShow.create(m => s"Migration from ${m.fromVersion} to ${m.toVersion}")

  val AnyVersion = -1

  def apply(from: Int, to: Int)(migrations: (DB => Unit)*): Migration = new Migration {
    override val toVersion = to
    override val fromVersion = from

    override def apply(db: DB): Unit = migrations.foreach(_(db))

    override def toString: String = s"Migration from: $from to $to"
  }

  def to(to: Int)(migrations: (DB => Unit)*): Migration = apply(AnyVersion, to)(migrations:_*)
}

/**
 * Uses given list of migrations to migrate database from one version to another.
 * Finds shortest migration path and applies it.
 */
class Migrations(migrations: Migration*)(implicit val tracking: TrackingService) extends DerivedLogTag {

  val toVersionMap = migrations.groupBy(_.toVersion)

  def plan(from: Int, to: Int): List[Migration] = {

    def shortest(from: Int, to: Int): List[Migration] = {
      val possible = toVersionMap.getOrElse(to, Nil)
      val plans = possible.map { m =>
        if (m.fromVersion == from || m.fromVersion == Migration.AnyVersion) List(m)
        else if (m.fromVersion < from) Nil
        else shortest(from, m.fromVersion) match {
          case Nil => List()
          case best => best ::: List(m)
        }
      } .filter(_.nonEmpty)

      if (plans.isEmpty) Nil
      else plans.minBy(_.length)
    }

    if (from == to) Nil
    else shortest(from, to)
  }

  /**
    * Migrates database using provided migrations.
    * Falls back to dropping all data if migration fails.
    *
    * Note, SQLiteOpenHelper#onUpgrade is already called from within an exclusive transaction when the system realises
    * that a database you are trying to access needs to be upgraded. So we can just execute all sql commands directly
    * (no point in calling nested transactions for code we control)
    *
    * @throws IllegalStateException if no migration plan can be found
    */
  @throws[IllegalStateException]("If no migration plan can be found for given versions")
  def migrate(storage: DaoDB, fromVersion: Int, toVersion: Int)(implicit db: SupportSQLiteDatabase): Unit = {
    if (fromVersion != toVersion) {
      plan(fromVersion, toVersion) match {
        case Nil => throw new IllegalStateException(s"No migration plan from: $fromVersion to: $toVersion")
        case ms =>
          try {
            ms.foreach { m =>
              verbose(l"applying $m")
              m(db)
              db.execSQL(s"PRAGMA user_version = ${m.toVersion}")
            }
          } catch {
            case NonFatal(e) =>
              error(l"Migration failed for from: $fromVersion to: $toVersion", e)
              tracking.exception(e, s"Migration failed for $storage, from: $fromVersion to: $toVersion")
              fallback(storage, db)
          }
      }
    }
  }

  def fallback(storage: DaoDB, db: SupportSQLiteDatabase): Unit = {
    warn(l"Dropping all data!!")
    storage.dropAllTables(db)
    storage.onCreate(db)
  }
}

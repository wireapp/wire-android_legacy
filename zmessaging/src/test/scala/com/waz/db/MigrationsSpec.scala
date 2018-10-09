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

import android.database.sqlite.SQLiteDatabase
import org.robolectric.Robolectric
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

import scala.collection.mutable.ListBuffer

class MigrationsSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  var appliedMigrations = new ListBuffer[(Int, Int)]
  var dropAllCalled = false

  def testMigration(from: Int, to: Int) = Migration(from, to) { _ => appliedMigrations.append(from -> to) }

  lazy val testDaoDb = new DaoDB(Robolectric.application, "test", null, 1, Seq.empty[BaseDao[_]], Seq.empty[Migration]) {

    override def dropAllTables(db: SQLiteDatabase): Unit = dropAllCalled = true
  }

  implicit def db: SQLiteDatabase = testDaoDb.getWritableDatabase

  before {
    appliedMigrations.clear()
    dropAllCalled = false
  }

  feature("Migration plan") {

    scenario("Try finding a plan when there are no migrations") {
      new Migrations().plan(1, 2) shouldEqual Nil
      new Migrations().plan(2, 1) shouldEqual Nil
      new Migrations().plan(1, 1) shouldEqual Nil
    }

    scenario("Find single migration") {
      val migration = testMigration(0, 1)
      val migrations = new Migrations(migration)

      migrations.plan(0, 1) shouldEqual List(migration)
      migrations.plan(1, 2) shouldEqual Nil
      migrations.plan(0, 2) shouldEqual Nil
      migrations.plan(1, 1) shouldEqual Nil
    }

    scenario("Find shortest plan") {
      val m01 = testMigration(0, 1)
      val m12 = testMigration(1, 2)
      val m02 = testMigration(0, 2)
      val m23 = testMigration(2, 3)
      val m24 = testMigration(2, 4)
      val m4 = testMigration(Migration.AnyVersion, 4)
      val migrations = new Migrations(m01, m02, m12, m23, m24, m4)

      migrations.plan(0, 1) shouldEqual List(m01)
      migrations.plan(0, 2) shouldEqual List(m02)
      migrations.plan(1, 2) shouldEqual List(m12)
      migrations.plan(0, 3) shouldEqual List(m02, m23)
      migrations.plan(0, 4) shouldEqual List(m4)
      migrations.plan(3, 4) shouldEqual List(m4)
    }
  }

  feature("Apply migrations") {

    scenario("Do nothing when versions match") {
      new Migrations().migrate(testDaoDb, 1, 1)

      appliedMigrations should be(empty)
      dropAllCalled shouldEqual false
    }

    scenario("Fail when there is no migration plan") {
      intercept[IllegalStateException] {
        new Migrations().migrate(testDaoDb, 0, 1)
      }

      intercept[IllegalStateException] {
        new Migrations(testMigration(0, 1)).migrate(testDaoDb, 0, 2)
      }

      appliedMigrations should be(empty)
      dropAllCalled shouldEqual false
    }

    scenario("Execute migration plan") {
      new Migrations(testMigration(0, 1), testMigration(0, 2), testMigration(1, 2), testMigration(2, 3)).migrate(testDaoDb, 0, 3)

      appliedMigrations shouldEqual  List(0 -> 2, 2 -> 3)
      dropAllCalled shouldEqual false
    }

    scenario("Execute fallback on migration fail") {
      new Migrations(
        testMigration(0, 1),
        testMigration(0, 2),
        testMigration(1, 2),
        Migration(2, 3) { _ => throw new Exception("migration fail") }
      ).migrate(testDaoDb, 0, 3)

      appliedMigrations shouldEqual  List(0 -> 2)
      dropAllCalled shouldEqual true
    }
  }
}

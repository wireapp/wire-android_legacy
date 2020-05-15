package com.waz.db

import androidx.room.migration.{Migration => RoomMigration}
import com.waz.specs.AndroidFreeSpec
import com.waz.zclient.storage.db.{GlobalDatabase, UserDatabase}

class RoomDbCompatibilitySpec extends AndroidFreeSpec {

  scenario("Check UserDatabase Room database version") {
    checkRoomDbVersion("UserDatabase", UserDatabase.VERSION, ZMessagingDB.DbVersion)
  }

  scenario("Check GlobalDatabase Room database version") {
    checkRoomDbVersion("GlobalDatabase", GlobalDatabase.VERSION, ZGlobalDB.DbVersion)
  }

  scenario("Check UserDatabase Room db migration versions") {
    checkMigrations(UserDatabase.getMigrations, ZMessagingDB.DbVersion, "UserDatabase", "ZMessagingDB")
  }

  scenario("Check GlobalDatabase Room db migration versions") {
    checkMigrations(GlobalDatabase.getMigrations, ZGlobalDB.DbVersion, "GlobalDatabase", "ZGlobalDB")
  }

  private def checkRoomDbVersion(roomDbName: String, roomDbVersion: Int, legacyDbVersion: Int): Unit = {
    assert(roomDbVersion > legacyDbVersion,
    s"\nRoom database $roomDbName has an outdated version. " +
      s"Room db version: $roomDbVersion, legacy db version: $legacyDbVersion")
  }

  private def checkMigrations(migrations: Array[RoomMigration],
                              legacyDbVersion: Int,
                              roomDbName: String,
                              legacyDbName: String): Unit = {
    val outdatedMigrations = migrations.filter(_.startVersion < legacyDbVersion)

    val errorMessage: String = outdatedMigrations.map(m => s"(${m.startVersion}, ${m.endVersion})\n").mkString(",")

    assert(outdatedMigrations.isEmpty,
      s"\nSome Room migrations of $roomDbName try to operate on legacy $legacyDbName. " +
        s"Consider updating the start/end versions for these migrations: $errorMessage")
  }
}

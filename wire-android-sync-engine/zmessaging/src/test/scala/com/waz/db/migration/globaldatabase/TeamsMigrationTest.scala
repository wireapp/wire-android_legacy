package com.waz.db.migration.globaldatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.TeamData.TeamDataDao
import com.waz.model._
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.teams.TeamsEntity

class TeamsMigrationTest extends GlobalDatabaseMigrationTest {
  feature("Teams table migration") {
    scenario("Teams migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zGlobalDb.getWritableDatabase)
      val teamData = TeamData(TeamId(""), Name(""), UserId(""), AssetId(""))
      TeamDataDao.insertOrReplace(Seq(teamData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertTeamsEntity(_, new TeamsEntity(
          "", "", "", ""
        ))
      })
    }
  }
}

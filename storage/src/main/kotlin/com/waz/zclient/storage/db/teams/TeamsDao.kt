package com.waz.zclient.storage.db.teams

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TeamsDao {

    @Query("SELECT * FROM Teams")
    suspend fun allTeams(): List<TeamsEntity>

    @Insert
    suspend fun insertTeam(team: TeamsEntity)
}

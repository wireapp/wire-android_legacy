package com.waz.zclient.storage.db.userclients

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserClientDao {

    @Query("SELECT * FROM Clients")
    suspend fun allClients(): List<UserClientsEntity>

    @Insert
    suspend fun insertClient(client: UserClientsEntity)
}

package com.waz.zclient.storage.db.clients.service

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.waz.zclient.storage.db.clients.model.ClientEntity

@Dao
interface ClientsDao {

    @Query("SELECT * from client WHERE id = :clientId")
    suspend fun clientById(clientId: String): ClientEntity

    @Query("SELECT * from client")
    suspend fun allClients(): List<ClientEntity>

    @Transaction
    suspend fun updateClients(clients: List<ClientEntity>) {
        deleteAllClients()
        insertAll(clients)
    }

    @Insert
    suspend fun insertAll(clients: List<ClientEntity>)

    @Query("DELETE FROM client")
    suspend fun deleteAllClients()

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateClient(client: ClientEntity)
}

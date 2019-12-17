package com.waz.zclient.storage.db.clients.service

import androidx.room.*
import com.waz.zclient.storage.db.clients.model.ClientDao

@Dao
interface ClientDbService {

    @Query("SELECT * from client WHERE id = :clientId")
    suspend fun clientById(clientId: String): ClientDao

    @Query("SELECT * from client")
    suspend fun allClients(): List<ClientDao>

    @Transaction
    suspend fun updateClients(clients: List<ClientDao>) {
        deleteAllClients()
        insertAll(clients)
    }

    @Insert
    suspend fun insertAll(clients: List<ClientDao>)

    @Query("DELETE FROM client")
    suspend fun deleteAllClients()

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateClient(client: ClientDao)
}

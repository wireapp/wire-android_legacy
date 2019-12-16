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
    fun updateClients(clients: List<ClientDao>) {
        deleteAllClients()
        insertAll(clients)
    }

    @Insert
    fun insertAll(clients: List<ClientDao>)

    @Query("DELETE FROM client")
    fun deleteAllClients()

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateClient(client: ClientDao)
}

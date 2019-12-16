package com.waz.zclient.storage.db.clients.service

import androidx.room.*
import com.waz.zclient.storage.db.clients.model.ClientEntity

@Dao
interface ClientDbService {

    @Query("SELECT * from client WHERE id = :clientId")
    suspend fun clientById(clientId: String): ClientEntity

    @Query("SELECT * from client")
    suspend fun allClients(): Array<ClientEntity>

    @Transaction
    fun updateClients(clients: Array<ClientEntity>) {
        deleteAllClients()
        insertAll(clients)
    }

    @Insert
    fun insertAll(clients: Array<ClientEntity>)

    @Query("DELETE FROM client")
    fun deleteAllClients()

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateClient(client: ClientEntity)
}

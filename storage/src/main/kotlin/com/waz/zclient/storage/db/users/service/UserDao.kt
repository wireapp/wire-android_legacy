package com.waz.zclient.storage.db.users.service

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.users.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM Users")
    suspend fun allUsers(): List<UserEntity>

    @Insert
    suspend fun insert(user: UserEntity)

    @Query("SELECT * from Users WHERE _id = :userId")
    fun byId(userId: String): Flow<UserEntity>

    @Query("UPDATE Users SET name=:name WHERE _id = :userId")
    suspend fun updateName(userId: String, name: String)

    @Query("UPDATE Users SET handle=:handle WHERE _id = :userId")
    suspend fun updateHandle(userId: String, handle: String)

    @Query("UPDATE Users SET email=:email WHERE _id = :userId")
    suspend fun updateEmail(userId: String, email: String)

    @Query("UPDATE Users SET phone=:phone WHERE _id = :userId")
    suspend fun updatePhone(userId: String, phone: String)
}

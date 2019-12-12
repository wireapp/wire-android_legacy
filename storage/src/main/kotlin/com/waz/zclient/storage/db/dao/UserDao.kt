package com.waz.zclient.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.model.UserEntity
import io.reactivex.Completable

@Dao
interface UserDao {

    @Insert
    fun insert(user: UserEntity): Completable

    @Query("SELECT * from user WHERE _id = :userId")
    fun selectById(userId: String): UserEntity

    @Query("UPDATE user SET name=:name WHERE _id = :userId")
    fun updateName(userId: String, name: String): Any

    @Query("UPDATE user SET handle=:handle WHERE _id = :userId")
    fun updateHandle(userId: String, handle: String): Any

    @Query("UPDATE user SET email=:email WHERE _id = :userId")
    fun updateEmail(userId: String, email: String): Any

    @Query("UPDATE user SET phone=:phone WHERE _id = :userId")
    fun updatePhone(userId: String, phone: String): Any
}


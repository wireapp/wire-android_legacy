package com.waz.zclient.storage.db.users.service

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.users.model.UserDao

@Dao
interface UserDbService {

    @Insert
    fun insert(user: UserDao)

    @Query("SELECT * from user WHERE _id = :userId")
    fun selectById(userId: String): UserDao

//    @Query("UPDATE user SET name=:name WHERE _id = :userId")
//    fun updateName(userId: String, name: String): Int

//    @Query("UPDATE user SET handle=:handle WHERE _id = :userId")
//    fun updateHandle(userId: String, handle: String): Int
//
//    @Query("UPDATE user SET email=:email WHERE _id = :userId")
//    fun updateEmail(userId: String, email: String): Int
//
//    @Query("UPDATE user SET phone=:phone WHERE _id = :userId")
//    fun updatePhone(userId: String, phone: String): Int
}


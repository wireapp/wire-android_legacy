package com.waz.zclient.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface UserDao {

    @Insert
    fun insert(user: UserEntity): Completable

    @Query("SELECT * from user WHERE _id = :userId")
    fun userById(userId: String): Single<UserEntity>

    @Query("UPDATE user SET name=:name WHERE _id = :userId")
    fun updateName(userId: String, name: String): Completable

    @Query("UPDATE user SET handle=:handle WHERE _id = :userId")
    fun updateHandle(userId: String, handle: String): Completable

    @Query("UPDATE user SET email=:email WHERE _id = :userId")
    fun updateEmail(userId: String, email: String): Completable

    @Query("UPDATE user SET phone=:phone WHERE _id = :userId")
    fun updatePhone(userId: String, phone: String): Completable
}

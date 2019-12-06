package com.waz.zclient.roomdb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.roomdb.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface UserDao {

    @Insert
    fun insert(user: UserEntity): Completable

    @Query("SELECT * from Users WHERE _id = :userId")
    fun userById(userId: String): Single<UserEntity>

    @Query("UPDATE Users SET name=:name WHERE _id = :userId")
    fun updateName(userId: String, name: String): Completable

    @Query("UPDATE Users SET handle=:handle WHERE _id = :userId")
    fun updateHandle(userId: String, handle: String): Completable

    @Query("UPDATE Users SET email=:email WHERE _id = :userId")
    fun updateEmail(userId: String, email: String): Completable

    @Query("UPDATE Users SET phone=:phone WHERE _id = :userId")
    fun updatePhone(userId: String, phone: String): Completable
}

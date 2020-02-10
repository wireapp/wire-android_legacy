package com.waz.zclient.storage.db.users.service

import androidx.room.Dao
import androidx.room.Insert
import com.waz.zclient.storage.db.users.model.UserPreferenceEntity

@Dao
interface UserPreferenceDao {

    @Insert
    fun insert(userPreferenceEntity: UserPreferenceEntity)

    @Insert
    fun insert(userPreferenceEntityList: List<UserPreferenceEntity>)
}

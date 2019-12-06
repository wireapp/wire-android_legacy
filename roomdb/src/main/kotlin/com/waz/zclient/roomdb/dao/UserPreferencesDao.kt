package com.waz.zclient.roomdb.dao

import androidx.room.Dao
import androidx.room.Insert
import com.waz.zclient.roomdb.model.UserPreferenceEntity

@Dao
interface UserPreferencesDao {

    @Insert
    fun insert(userPreferenceEntity: UserPreferenceEntity)

    @Insert
    fun insert(userPreferenceEntityList: List<UserPreferenceEntity>)
}

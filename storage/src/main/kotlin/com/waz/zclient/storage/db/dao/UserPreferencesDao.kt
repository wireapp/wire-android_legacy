package com.waz.zclient.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import com.waz.zclient.storage.db.model.UserPreferenceEntity

@Dao
interface UserPreferencesDao {

    @Insert
    fun insert(userPreferenceEntity: UserPreferenceEntity)

    @Insert
    fun insert(userPreferenceEntityList: List<UserPreferenceEntity>)
}

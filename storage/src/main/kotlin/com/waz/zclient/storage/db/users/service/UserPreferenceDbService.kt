package com.waz.zclient.storage.db.users.service

import androidx.room.Dao
import androidx.room.Insert
import com.waz.zclient.storage.db.users.model.UserPreferenceDao

@Dao
interface UserPreferenceDbService {

    @Insert
    fun insert(userPreferenceEntity: UserPreferenceDao)

    @Insert
    fun insert(userPreferenceEntityList: List<UserPreferenceDao>)
}

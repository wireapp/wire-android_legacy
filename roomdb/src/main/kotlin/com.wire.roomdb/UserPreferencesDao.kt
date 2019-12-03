package com.wire.roomdb

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface UserPreferencesDao {

    @Insert
    fun insert(userPreference: UserPreference)

    @Insert
    fun insert(userPreferenceList: List<UserPreference>)
}

package com.waz.zclient.storage.db.teams

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Teams")
data class TeamsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val teamId: String,

    @ColumnInfo(name = "name")
    val teamName: String,

    @ColumnInfo(name = "creator")
    val creatorId: String,

    @ColumnInfo(name = "icon")
    val iconId: String
)

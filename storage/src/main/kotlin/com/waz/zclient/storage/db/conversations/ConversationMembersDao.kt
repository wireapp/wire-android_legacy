package com.waz.zclient.storage.db.conversations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.conversationmembers.ConversationMembersEntity

@Dao
interface ConversationMembersDao {

    @Query("SELECT * FROM ConversationMembers")
    suspend fun allConversationMemebers(): List<ConversationMembersEntity>

    @Insert
    suspend fun insertConversationMemeber(conversationMember: ConversationMembersEntity)
}

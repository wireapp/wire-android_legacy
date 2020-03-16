package com.waz.zclient.conversations

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface ConversationsRepository {

    suspend fun joinConversation(convId: String): Either<Failure, Unit>

}

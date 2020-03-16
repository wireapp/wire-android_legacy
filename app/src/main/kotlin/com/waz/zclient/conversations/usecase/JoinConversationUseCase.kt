package com.waz.zclient.conversations.usecase

import com.waz.zclient.conversations.ConversationsRepository
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class JoinConversationUseCase(private val conversationsRepository: ConversationsRepository)
    : UseCase<Unit, JoinConversationParams>() {

    override suspend fun run(params: JoinConversationParams): Either<Failure, Unit> =
        conversationsRepository.joinConversation(params.convId)
}

data class JoinConversationParams(val convId: String)

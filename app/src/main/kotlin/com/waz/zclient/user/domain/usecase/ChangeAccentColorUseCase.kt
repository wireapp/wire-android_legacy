package com.waz.zclient.user.domain.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository

class ChangeAccentColorUseCase(private val usersRepository: UsersRepository)
    : UseCase<Any, ChangeAccentColorParams>() {

    override suspend fun run(params: ChangeAccentColorParams): Either<Failure, Any> =
        usersRepository.changeAccentColor(params.newAccentColorId)
}

data class ChangeAccentColorParams(val newAccentColorId: Int)

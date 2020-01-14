package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository

class UpdateHandleUseCase(private val usersRepository: UsersRepository)
    : UseCase<Any, ChangeHandleParams>() {

    override suspend fun run(params: ChangeHandleParams): Either<Failure, Any> =
        usersRepository.changeHandle(params.newHandle)
}

data class ChangeHandleParams(val newHandle: String)



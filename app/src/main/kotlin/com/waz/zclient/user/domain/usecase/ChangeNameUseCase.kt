package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository


class ChangeNameUseCase(private val usersRepository: UsersRepository)
    : UseCase<Any, ChangeNameParams>() {

    override suspend fun run(params: ChangeNameParams): Either<Failure, Any> =
        usersRepository.changeName(params.name)
}

data class ChangeNameParams(val name: String)



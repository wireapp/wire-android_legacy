package com.waz.zclient.shared.user.name

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.user.UsersRepository

class ChangeNameUseCase(private val usersRepository: UsersRepository) :
    UseCase<Any, ChangeNameParams>() {

    override suspend fun run(params: ChangeNameParams): Either<Failure, Any> =
        usersRepository.changeName(params.newName)
}

data class ChangeNameParams(val newName: String)

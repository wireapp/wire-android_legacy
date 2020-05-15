package com.waz.zclient.shared.user.email

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.user.UsersRepository

class ChangeEmailUseCase(private val usersRepository: UsersRepository) :
    UseCase<Any, ChangeEmailParams>() {

    override suspend fun run(params: ChangeEmailParams): Either<Failure, Any> =
        usersRepository.changeEmail(params.newEmail)
}

data class ChangeEmailParams(val newEmail: String)

package com.waz.zclient.user.domain.usecase.phonenumber

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository

class DeletePhoneNumberUseCase(private val usersRepository: UsersRepository) : UseCase<Any, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, Any> =
        usersRepository.deletePhone()
}

package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository


class ChangePhoneUseCase(private val usersRepository: UsersRepository)
    : UseCase<Any, ChangePhoneParams>() {

    override suspend fun run(params: ChangePhoneParams): Either<Failure, Any> =
        usersRepository.changePhone(params.phoneNumber)
}

data class ChangePhoneParams(val phoneNumber: String)



package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.domain.model.User

class GetUserProfileUseCase(private val usersRepository: UsersRepository)
    : UseCase<User, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, User> =
        usersRepository.profile()
}

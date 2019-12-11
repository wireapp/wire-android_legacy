package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.network.requestData
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.domain.model.User

class GetUserProfileUseCase(private val usersRepository: UsersRepository)
    : UseCase<User, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, User> = requestData {
        usersRepository.profile()
    }
}

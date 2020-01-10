package com.waz.zclient.user.domain.usecase

import com.waz.zclient.core.usecase.ObservableUseCase
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.domain.model.User
import kotlinx.coroutines.flow.Flow

class GetUserProfileUseCase(private val usersRepository: UsersRepository)
    : ObservableUseCase<User, Unit>() {

    override suspend fun run(params: Unit): Flow<User> =
        usersRepository.profileDetails()
}

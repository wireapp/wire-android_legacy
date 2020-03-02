package com.waz.zclient.user.profile

import com.waz.zclient.core.usecase.ObservableUseCase
import com.waz.zclient.user.UsersRepository
import com.waz.zclient.user.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
class GetUserProfileUseCase(private val usersRepository: UsersRepository) :
    ObservableUseCase<User, Unit>() {

    override suspend fun run(params: Unit): Flow<User> =
        usersRepository.profileDetails()
}

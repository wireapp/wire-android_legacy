package com.waz.zclient.shared.user.profile

import com.waz.zclient.core.usecase.ObservableUseCase
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
class GetUserProfileUseCase(private val usersRepository: UsersRepository) :
    ObservableUseCase<User, Unit>() {

    override suspend fun run(params: Unit): Flow<User> =
        usersRepository.profileDetails()
}

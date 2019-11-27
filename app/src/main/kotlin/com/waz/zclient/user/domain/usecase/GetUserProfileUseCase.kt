package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.network.Network
import com.waz.zclient.core.usecase.SingleUseCase
import com.waz.zclient.user.data.repository.UserRepository
import com.waz.zclient.user.data.repository.UserRepositoryImpl
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceImpl
import com.waz.zclient.user.domain.model.User
import io.reactivex.Scheduler
import io.reactivex.Single

class GetUserProfileUseCase(subscribeScheduler: Scheduler,
                            postExecutionScheduler: Scheduler) : SingleUseCase<User, Unit>(subscribeScheduler, postExecutionScheduler) {
    private val userRepository: UserRepository = UserRepositoryImpl(UserRemoteDataSourceImpl(Network.userApi()))
    override fun buildUseCaseSingle(params: Unit?): Single<User> = userRepository.profile()
}


package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.usecase.SingleUseCase
import com.waz.zclient.user.data.repository.UserRepository
import com.waz.zclient.user.data.repository.UserRepositoryImpl
import com.waz.zclient.user.domain.model.User
import io.reactivex.Scheduler
import io.reactivex.Single

class GetUserProfileSingleUseCase(subscribeScheduler: Scheduler,
                                  postExecutionScheduler: Scheduler) : SingleUseCase<User, Unit>(subscribeScheduler, postExecutionScheduler) {
    private val userRepository: UserRepository = UserRepositoryImpl()
    override fun buildUseCaseSingle(params: Unit?): Single<User> = userRepository.getProfile()
}


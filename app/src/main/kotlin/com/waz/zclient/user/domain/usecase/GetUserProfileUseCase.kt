package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.usecase.SingleUseCase
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.data.source.UsersDataSource
import com.waz.zclient.user.domain.model.User
import io.reactivex.Scheduler
import io.reactivex.Single

class GetUserProfileUseCase(subscribeScheduler: Scheduler,
                            postExecutionScheduler: Scheduler) : SingleUseCase<User, Unit>(subscribeScheduler, postExecutionScheduler) {
    private val userRepository: UsersDataSource = UsersRepository()
    override fun buildUseCaseSingle(params: Unit?): Single<User> = userRepository.profile()
}


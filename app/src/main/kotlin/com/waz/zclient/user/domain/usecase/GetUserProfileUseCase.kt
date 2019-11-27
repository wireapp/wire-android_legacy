package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.repository.UserRepository
import com.waz.zclient.user.data.repository.UserRepositoryImpl
import com.waz.zclient.user.domain.model.User
import io.reactivex.Scheduler
import io.reactivex.Single

class GetUserProfileUseCase(subscribeScheduler: Scheduler,
                            postExecutionScheduler: Scheduler) : UseCase<User, Unit>(subscribeScheduler, postExecutionScheduler) {
    private val userRepository: UserRepository = UserRepositoryImpl()
    override fun buildUseCaseSingle(params: Unit?): Single<User> = userRepository.getProfile()
}


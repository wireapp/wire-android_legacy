package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.usecase.CompletableUseCase
import com.waz.zclient.user.data.repository.UserRepository
import com.waz.zclient.user.data.repository.UserRepositoryImpl
import io.reactivex.Completable
import io.reactivex.Scheduler

class UpdateHandleUseCase(subscribeScheduler: Scheduler,
                          postExecutionScheduler: Scheduler) : CompletableUseCase<String>(subscribeScheduler, postExecutionScheduler) {
    private val userRepository: UserRepository = UserRepositoryImpl()
    override fun buildUseCaseCompletable(params: String?): Completable = userRepository.updateHandle(params!!)
}


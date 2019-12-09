package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.usecase.CompletableUseCase
import com.waz.zclient.user.data.UsersRepository
import com.waz.zclient.user.data.source.UsersDataSource
import io.reactivex.Completable
import io.reactivex.Scheduler

class ChangePhoneUseCase(subscribeScheduler: Scheduler,
                         postExecutionScheduler: Scheduler) : CompletableUseCase<String>(subscribeScheduler, postExecutionScheduler) {
    private val userRepository: UsersDataSource = UsersRepository()
    override fun buildUseCaseCompletable(params: String?): Completable = userRepository.changePhone(params!!)
}


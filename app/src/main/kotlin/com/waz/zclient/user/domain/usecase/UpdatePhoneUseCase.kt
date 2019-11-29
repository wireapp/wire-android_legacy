package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.data.source.remote.Network
import com.waz.zclient.core.usecase.CompletableUseCase
import com.waz.zclient.user.data.repository.UserRepository
import com.waz.zclient.user.data.repository.UserRepositoryImpl
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceImpl
import io.reactivex.Completable
import io.reactivex.Scheduler

class UpdatePhoneUseCase(subscribeScheduler: Scheduler,
                         postExecutionScheduler: Scheduler) : CompletableUseCase<String>(subscribeScheduler, postExecutionScheduler) {
    private val userRepository: UserRepository = UserRepositoryImpl(UserRemoteDataSourceImpl(Network().getUserApi()))
    override fun buildUseCaseCompletable(params: String?): Completable = userRepository.updatePhone(params!!)
}


package com.waz.zclient.settings.user.usecase


import com.waz.zclient.settings.domain.usecase.UseCase
import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.user.data.repository.UserRepository
import com.waz.zclient.user.data.repository.UserRepositoryImpl
import io.reactivex.Scheduler
import io.reactivex.Single

class GetUserProfileUseCase (subscribeScheduler: Scheduler,
                             postExecutionScheduler: Scheduler) : UseCase<UserEntity, Unit>(subscribeScheduler, postExecutionScheduler) {

    private val userRepository: UserRepository = UserRepositoryImpl()

    override fun buildUseCaseSingle(params: Unit?): Single<UserEntity> = userRepository.getUserProfile()

}


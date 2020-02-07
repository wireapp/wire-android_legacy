package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.core.usecase.ObservableUseCase
import com.waz.zclient.user.data.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
class GetHandleUseCase(private val userRepository: UsersRepository) : ObservableUseCase<String?, Unit>() {

    override suspend fun run(params: Unit): Flow<String?> =
        userRepository.profileDetails().mapLatest { it.handle }
}

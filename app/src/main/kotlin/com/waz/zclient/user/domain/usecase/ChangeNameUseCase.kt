package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.usecase.FlowUseCase
import com.waz.zclient.user.data.UsersRepository
import kotlinx.coroutines.flow.Flow


class ChangeNameUseCase(private val usersRepository: UsersRepository)
    : FlowUseCase<Void, ChangeNameParams>() {

    override suspend fun run(params: ChangeNameParams): Flow<Void> =
        usersRepository.changeName(params.name)
}

data class ChangeNameParams(val name: String)



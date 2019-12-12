package com.waz.zclient.user.domain.usecase


import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.core.network.requestData
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository

class ChangeHandleUseCase(private val usersRepository: UsersRepository)
    : UseCase<Any, ChangeHandle>() {

    override suspend fun run(params: ChangeHandle): Either<Failure, Any> = requestData {
        usersRepository.changePhone(params.handle)
    }
}

data class ChangeHandle(val handle: String)



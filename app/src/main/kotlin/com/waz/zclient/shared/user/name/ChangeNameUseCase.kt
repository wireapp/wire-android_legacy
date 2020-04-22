package com.waz.zclient.shared.user.name

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCaseTemp
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChangeNameUseCase(private val usersRepository: UsersRepository) :
    UseCaseTemp<Any, ChangeNameParams>() {

    override suspend fun invoke(params: ChangeNameParams): Either<Failure, Any> =
        withContext(Dispatchers.IO) {
            usersRepository.changeName(params.newName)
        }
}

data class ChangeNameParams(val newName: String)

package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestApi
import com.waz.zclient.user.data.source.remote.model.UserApi
import com.waz.zclient.user.domain.usecase.handle.HandleExistsAlreadyError
import com.waz.zclient.user.domain.usecase.handle.HandleInvalidError
import com.waz.zclient.user.domain.usecase.handle.HandleIsAvailable
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleState

class UsersRemoteDataSource constructor(private val usersNetworkService: UsersNetworkService) {

    companion object {
        private const val HANDLE_TAKEN = 200
        private const val HANDLE_INVALID = 400
    }

    suspend fun profileDetails(): Either<Failure, UserApi> = requestApi { usersNetworkService.profileDetails() }

    suspend fun changeName(name: String): Either<Failure, Any> = requestApi { usersNetworkService.changeName(ChangeNameRequest(name)) }

    suspend fun changeHandle(handle: String): Either<Failure, Any> = requestApi { usersNetworkService.changeHandle(ChangeHandleRequest(handle)) }

    suspend fun changeEmail(email: String): Either<Failure, Any> = requestApi { usersNetworkService.changeEmail(ChangeEmailRequest(email)) }

    suspend fun changePhone(phone: String): Either<Failure, Any> = requestApi { usersNetworkService.changePhone(ChangePhoneRequest(phone)) }

    suspend fun doesHandleExist(newHandle: String): Either<Failure, ValidateHandleState> {
        //TODO Temporary until network is integrated
        val response = usersNetworkService.doesHandleExist(newHandle)
        return when {
            response.code() == HANDLE_TAKEN -> {
                Either.Left(HandleExistsAlreadyError)
            }
            response.code() == HANDLE_INVALID -> {
                Either.Left(HandleInvalidError)
            }
            else ->
                Either.Right(HandleIsAvailable)
        }
    }
}

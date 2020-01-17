package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.user.data.source.remote.model.UserApi
import com.waz.zclient.user.domain.usecase.handle.*

class UsersRemoteDataSource(
    private val usersNetworkService: UsersNetworkService,
    override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun profileDetails(): Either<Failure, UserApi> =
        request { usersNetworkService.profileDetails() }

    suspend fun changeName(name: String): Either<Failure, Any> =
        request { usersNetworkService.changeName(ChangeNameRequest(name)) }

    suspend fun changeHandle(handle: String): Either<Failure, Any> =
        request { usersNetworkService.changeHandle(ChangeHandleRequest(handle)) }

    suspend fun changeEmail(email: String): Either<Failure, Any> =
        request { usersNetworkService.changeEmail(ChangeEmailRequest(email)) }

    suspend fun changePhone(phone: String): Either<Failure, Any> =
        request { usersNetworkService.changePhone(ChangePhoneRequest(phone)) }

    suspend fun doesHandleExist(newHandle: String): Either<Failure, ValidateHandleSuccess> =
        when (usersNetworkService.doesHandleExist(newHandle).code()) {
            HANDLE_TAKEN -> Either.Left(HandleExistsAlreadyError)
            HANDLE_INVALID -> Either.Left(HandleInvalidError)
            HANDLE_AVAILABLE -> Either.Right(HandleIsAvailable)
            else -> Either.Left(HandleUnknownError)
        }

    companion object {
        private const val HANDLE_TAKEN = 200
        private const val HANDLE_INVALID = 400
        private const val HANDLE_AVAILABLE = 404
    }
}

package com.waz.zclient.shared.user.datasources.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.shared.user.handle.HandleAlreadyExists
import com.waz.zclient.shared.user.handle.HandleInvalid
import com.waz.zclient.shared.user.handle.HandleIsAvailable
import com.waz.zclient.shared.user.handle.UnknownError
import com.waz.zclient.shared.user.handle.ValidateHandleSuccess

class UsersRemoteDataSource(
    private val usersApi: UsersApi,
    override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun profileDetails(): Either<Failure, UserResponse> =
        request { usersApi.profileDetails() }

    suspend fun changeName(name: String): Either<Failure, Any> =
        request { usersApi.changeName(ChangeNameRequest(name)) }

    suspend fun changeHandle(handle: String): Either<Failure, Any> =
        request { usersApi.changeHandle(ChangeHandleRequest(handle)) }

    suspend fun changeEmail(email: String): Either<Failure, Any> =
        request { usersApi.changeEmail(ChangeEmailRequest(email)) }

    suspend fun changePhone(phone: String): Either<Failure, Any> =
        request { usersApi.changePhone(ChangePhoneRequest(phone)) }

    suspend fun doesHandleExist(newHandle: String): Either<Failure, ValidateHandleSuccess> =
        when (usersApi.doesHandleExist(newHandle).code()) {
            HANDLE_TAKEN -> Either.Left(HandleAlreadyExists)
            HANDLE_INVALID -> Either.Left(HandleInvalid)
            HANDLE_AVAILABLE -> Either.Right(HandleIsAvailable)
            else -> Either.Left(UnknownError)
        }

    suspend fun deletePhone(): Either<Failure, Any> =
        request { usersApi.deletePhone() }

    suspend fun deleteAccountPermanently(): Either<Failure, Unit> =
        request { usersApi.deleteAccount(DeleteAccountRequest) }

    companion object {
        private const val HANDLE_TAKEN = 200
        private const val HANDLE_INVALID = 400
        private const val HANDLE_AVAILABLE = 404
    }
}

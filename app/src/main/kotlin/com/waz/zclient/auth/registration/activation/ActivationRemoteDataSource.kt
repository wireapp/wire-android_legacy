package com.waz.zclient.auth.registration.activation

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import retrofit2.Response

class ActivationRemoteDataSource(
    private val activationApi: ActivationApi, override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun sendEmailActivationCode(email: String): Either<Failure, Unit> =
        request { activationApi.sendActivationCode(SendActivationCodeRequest(email = email)) }

    override fun <T> handleRequestError(response: Response<T>): Either<Failure, T> =
        when (response.code()) {
            INVALID_EMAIL -> Either.Left(InvalidEmail)
            EMAIL_BLACKLISTED -> Either.Left(EmailBlackListed)
            EMAIL_IN_USE -> Either.Left(EmailInUse)
            else -> super.handleRequestError(response)
        }

    companion object {
        private const val INVALID_EMAIL = 400
        private const val EMAIL_BLACKLISTED = 403
        private const val EMAIL_IN_USE = 409
    }
}

object InvalidEmail : SendActivationCodeFailure()
object EmailBlackListed : SendActivationCodeFailure()
object EmailInUse : SendActivationCodeFailure()
object ActivationCodeSent : SendActivationCodeSuccess()

sealed class SendActivationCodeSuccess
sealed class SendActivationCodeFailure : FeatureFailure()

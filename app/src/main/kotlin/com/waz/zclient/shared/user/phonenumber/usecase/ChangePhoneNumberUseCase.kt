package com.waz.zclient.shared.user.phonenumber.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.user.phonenumber.PhoneNumberRepository

class ChangePhoneNumberUseCase(private val phoneNumberRepository: PhoneNumberRepository) :
    UseCase<Any, ChangePhoneNumberParams>() {

    override suspend fun run(params: ChangePhoneNumberParams): Either<Failure, Any> =
        phoneNumberRepository.changePhone(params.newPhoneNumber)
}

data class ChangePhoneNumberParams(val newPhoneNumber: String)

package com.waz.zclient.user.domain.usecase.phonenumber

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.phone.UserPhoneNumberRepository

class ChangePhoneNumberUseCase(private val phoneNumberRepository: UserPhoneNumberRepository) :
    UseCase<Any, ChangePhoneNumberParams>() {

    override suspend fun run(params: ChangePhoneNumberParams): Either<Failure, Any> =
        phoneNumberRepository.changePhone(params.newPhoneNumber)
}

data class ChangePhoneNumberParams(val newPhoneNumber: String)

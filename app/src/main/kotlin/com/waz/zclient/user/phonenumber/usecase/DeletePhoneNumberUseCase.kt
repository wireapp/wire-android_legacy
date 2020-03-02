package com.waz.zclient.user.phonenumber.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.phonenumber.PhoneNumberRepository

class DeletePhoneNumberUseCase(private val phoneNumberRepository: PhoneNumberRepository) : UseCase<Any, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, Any> =
        phoneNumberRepository.deletePhone()
}

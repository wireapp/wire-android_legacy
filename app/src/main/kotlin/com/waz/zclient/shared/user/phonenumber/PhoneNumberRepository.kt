package com.waz.zclient.shared.user.phonenumber

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface PhoneNumberRepository {
    suspend fun changePhone(phone: String): Either<Failure, Any>
    suspend fun deletePhone(): Either<Failure, Any>
}

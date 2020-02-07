package com.waz.zclient.user.data.phone

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface UserPhoneNumberRepository {
    suspend fun changePhone(phone: String): Either<Failure, Any>
    suspend fun deletePhone(): Either<Failure, Any>
}

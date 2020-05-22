package com.waz.zclient.core.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface UseCase<out Type, in Params> {

    suspend fun run(params: Params): Either<Failure, Type>
}

package com.waz.zclient.core.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

abstract class UseCase<out Type, in Params> {

    abstract suspend fun run(params: Params): Either<Failure, Type>
}

package com.waz.zclient.devices.domain

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.core.usecase.UseCase

class GetSelfClientIdUseCase : UseCase<String, Unit>() {
    override suspend fun run(params: Unit): Either<Failure, String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

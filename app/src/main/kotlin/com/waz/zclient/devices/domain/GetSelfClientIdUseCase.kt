package com.waz.zclient.devices.domain

import com.waz.zclient.core.data.source.remote.Either
import com.waz.zclient.core.data.source.remote.Failure
import com.waz.zclient.core.usecase.coroutines.UseCase

class GetSelfClientIdUseCase() : UseCase<String, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

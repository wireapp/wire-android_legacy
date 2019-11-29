package com.waz.zclient.devices.domain

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.core.usecase.coroutines.UseCase

class GetSelfClientIdUseCase() : UseCase<String, Unit>() {

    override suspend fun run(params: Unit): RequestResult<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


}

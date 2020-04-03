package com.waz.zclient.core.backend

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface BackendRepository {
    suspend fun getCustomBackendConfig(url: String): Either<Failure, CustomBackend>
}

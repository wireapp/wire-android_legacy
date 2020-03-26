package com.waz.zclient.core.backend.datasources

import com.waz.zclient.core.backend.usecase.CustomBackend
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface BackendRepository {
    suspend fun getCustomBackendConfig(url: String): Either<Failure, CustomBackend>
}


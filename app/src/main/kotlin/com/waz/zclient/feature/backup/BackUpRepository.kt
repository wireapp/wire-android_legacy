package com.waz.zclient.feature.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface BackUpRepository {
    suspend fun backUp(): Either<Failure, Unit>

    suspend fun restore(): Either<Failure, Unit>
}

package com.waz.zclient.feature.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface BackUpRepository<R> {
    suspend fun saveBackup(): Either<Failure, R>

    suspend fun restoreBackup(): Either<Failure, Unit>
}

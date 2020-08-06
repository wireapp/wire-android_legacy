package com.waz.zclient.feature.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface BackUpRepository {
    suspend fun saveBackup(): Either<Failure, Unit>

    suspend fun restoreBackup(): Either<Failure, Unit>
}

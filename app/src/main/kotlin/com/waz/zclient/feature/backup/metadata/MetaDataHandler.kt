package com.waz.zclient.feature.backup.metadata

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import java.io.File

interface MetaDataHandler {
    fun generateMetaDataFile(userId: UserId, userHandle: String): Either<Failure, File>
    fun readMetaData(file: File): Either<Failure, BackupMetaData>
}

package com.waz.zclient.shared.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import java.io.File

interface BackupRepository {
    fun writeAllToFiles(targetDir: File): Either<Failure, List<File>>
}

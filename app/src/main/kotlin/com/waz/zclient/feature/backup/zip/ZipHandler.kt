package com.waz.zclient.feature.backup.zip

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import java.io.File

interface ZipHandler {
    fun zip(zipFileName: String, files: List<File>): Either<Failure, File>
    fun unzip(zipFile: File): Either<Failure, List<File>>
}

package com.waz.zclient.core.utilities

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import java.io.File
import java.io.IOException

object IOHandler {
    data class IOFailure(val exception: IOException) : FeatureFailure()

    fun writeTextToFile(targetDir: File, fileName: String, text: () -> String): Either<Failure, File> =
        try {
            val file = File(targetDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()
            file.writeText(text())
            Either.Right(file)
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        }
}

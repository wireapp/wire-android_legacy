package com.waz.zclient.shared.backup

import com.waz.model.Handle
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.flatten
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.mapRight
import com.waz.zclient.core.utilities.IOHandler
import org.threeten.bp.Instant
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipOutputStream

interface ZipBackupHandler {
    fun zipData(userHandle: Handle, targetDir: File, files: List<File>): Either<Failure, File>
}

class ZipBackupHandlerImpl : ZipBackupHandler {
    override fun zipData(userHandle: Handle, targetDir: File, files: List<File>) =
        IOHandler.createTemporaryFolder(backupTempDirName(userHandle)).flatMap { tempDir ->
            val zipFile = File(targetDir, backupZipFileName(userHandle)).apply {
                deleteOnExit()
            }

            IOHandler.withResource(ZipOutputStream(FileOutputStream(zipFile))) { zip ->
                files.mapRight { file ->
                    IOHandler.withResource(BufferedInputStream(FileInputStream(file))) {
                        IOHandler.writeZipEntry(it, zip, file.name)
                        file
                    }
                }
            }.flatten().map {
                tempDir.delete()
                zipFile
            }
        }

    companion object {
        @SuppressWarnings("MagicNumber")
        fun timestamp(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Instant.now().getEpochSecond() * 1000).toString()

        fun backupTempDirName(userHandle: Handle): String = "Wire-${userHandle.string()}-Backup_${timestamp()}"

        fun backupZipFileName(userHandle: Handle): String = "Wire-${userHandle.string()}-Backup_${timestamp()}.android_wbu"
    }
}

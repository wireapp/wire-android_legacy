/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.backup

import java.io._
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.{ZipFile, ZipOutputStream}

import com.waz.db.ZMessagingDB
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AccountData.Password
import com.waz.model.UserId
import com.waz.model.otr.ClientId
import com.waz.service.backup.BackupManager.InvalidBackup.{DbEntryNotFound, MetadataEntryNotFound}
import com.waz.service.backup.BackupManager._
import com.waz.utils.IoUtils.withResource
import com.waz.utils.Json.syntax._
import com.waz.utils.crypto.LibSodiumUtils
import com.waz.utils.{IoUtils, JsonDecoder, JsonEncoder, RichTry, returning}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.io.Source
import scala.util.{Failure, Success, Try}

trait BackupManager {
  def exportDatabase(userId: UserId,
                     userHandle: String,
                     databaseDir: File,
                     targetDir: File,
                     backupPassword: Password): Try[File]
  def importDatabase(userId: UserId,
                     exportFile: File,
                     targetDir: File,
                     currentDbVersion: Int = BackupMetadata.currentDbVersion,
                     backupPassword: Password): Try[File]
}

object BackupManager {
  sealed trait BackupError extends Exception

  case class UnknownBackupError(cause: Throwable) extends BackupError

  sealed trait InvalidBackup extends BackupError
  object InvalidBackup {
    case object DbEntryNotFound extends InvalidBackup
    case object MetadataEntryNotFound extends InvalidBackup
  }

  sealed trait InvalidMetadata extends BackupError
  object InvalidMetadata {
    case class WrongFormat(cause: Throwable) extends InvalidMetadata
    case object UserId extends InvalidMetadata
    case object DbVersion extends InvalidMetadata
    case object Platform extends InvalidMetadata
  }

  object BackupMetadata {

    def currentPlatform: String = "android"
    def currentDbVersion: Int = ZMessagingDB.DbVersion


    implicit def backupMetadataEncoder: JsonEncoder[BackupMetadata] = new JsonEncoder[BackupMetadata] {
      override def apply(data: BackupMetadata): JSONObject = JsonEncoder { o =>
        o.put("user_id", data.userId.str)
        o.put("version", data.version)

        data.clientId.foreach { id => o.put("client_id", id.str) }
        o.put("creation_time", JsonEncoder.encodeISOInstant(data.creationTime))
        o.put("platform", data.platform)
      }
    }

    implicit def backupMetadataDecoder: JsonDecoder[BackupMetadata] = new JsonDecoder[BackupMetadata] {

      import JsonDecoder._

      override def apply(implicit js: JSONObject): BackupMetadata = {
        BackupMetadata(
          decodeUserId('user_id),
          'version,
          decodeOptClientId('client_id),
          JsonDecoder.decodeISOInstant('creation_time),
          'platform
        )
      }
    }
  }

  case class BackupMetadata(userId: UserId,
                            version: Int = BackupMetadata.currentDbVersion,
                            clientId: Option[ClientId] = None,
                            creationTime: Instant = Instant.now(),
                            platform: String = BackupMetadata.currentPlatform)

  def backupZipFileName(userHandle: String): String = {
    val timestamp = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Instant.now().getEpochSecond * 1000)
    s"Wire-$userHandle-Backup_$timestamp.android_wbu"
  }
  def backupMetadataFileName: String = "export.json"

  def getDbFileName(id: UserId): String = id.str
  def getDbWalFileName(id: UserId): String = id.str + "-wal"

}

class BackupManagerImpl(libSodiumUtils: LibSodiumUtils) extends BackupManager with DerivedLogTag {

  import BackupManager._

  // The current solution writes the database file(s) directly to the newly created zip file.
  // This way we save memory, but it means that it takes longer before we can release the lock.
  // If this becomes the problem, we might consider first copying the database file(s) to the
  // external storage directory, release the lock, and then safely proceed with zipping them.
  override def exportDatabase(userId:         UserId,
                              userHandle:     String,
                              databaseDir:    File,
                              targetDir:      File,
                              backupPassword: Password): Try[File] = {
    verbose(l"BKP exportDatabase($userId, $userHandle, ${databaseDir.getAbsolutePath}, $backupPassword)")
    val backup = Try {
      returning(new File(targetDir, backupZipFileName(userHandle))) { zipFile =>
        zipFile.deleteOnExit()

        withResource(new ZipOutputStream(new FileOutputStream(zipFile))) { zip =>

          withResource(new ByteArrayInputStream(BackupMetadata(userId).toJsonString.getBytes("utf8"))) {
            IoUtils.writeZipEntry(_, zip, backupMetadataFileName)
          }

          val dbFileName = getDbFileName(userId)
          val dbFile = new File(databaseDir, dbFileName)
          withResource(new BufferedInputStream(new FileInputStream(dbFile))) {
            IoUtils.writeZipEntry(_, zip, dbFileName)
          }
          verbose(l"BKP file: $dbFile exists: ${dbFile.exists}, length: ${if (dbFile.exists) dbFile.length else 0}")

          val walFileName = getDbWalFileName(userId)
          val walFile = new File(databaseDir, walFileName)
          verbose(l"BKP WAL file: $walFile exists: ${walFile.exists}, length: ${if (walFile.exists) walFile.length else 0}")

          if (walFile.exists) withResource(new BufferedInputStream(new FileInputStream(walFile))) {
            IoUtils.writeZipEntry(_, zip, walFileName)
          }
        }

        verbose(l"BKP database export finished: $zipFile. Data contains: ${zipFile.length} bytes")
      }
    }.mapFailureIfNot[BackupError](UnknownBackupError.apply)

    for {
      unencryptedFile <- backup
      _ = verbose(l"BKP unencrypted file: $unencryptedFile, exists: ${unencryptedFile.exists}, length: ${if (unencryptedFile.exists) unencryptedFile.length else 0}")
      encryptedFile <- encryptDatabase(unencryptedFile, backupPassword, userId)
      _ = verbose(l"BKP encrypted file: $encryptedFile, exists: ${encryptedFile.exists}, length: ${if (encryptedFile.exists) encryptedFile.length else 0}")
    } yield encryptedFile

  }

  private def encryptDatabase(backup: File, password: Password, userId: UserId): Try[File] = {
    verbose(l"BKP encryptDatabase(${backup.getAbsolutePath}, $password, $userId)")
    val salt = libSodiumUtils.generateSalt()
    val backupBytes = IoUtils.readFileBytes(backup)

    (libSodiumUtils.encrypt(backupBytes, password, salt), getMetaDataBytes(password, salt, userId)) match {
      case (Some(encryptedBytes), Some(meta)) =>
        verbose(l"BKP encrypted data: ${encryptedBytes.length} bytes, meta: ${meta.length} bytes")
        Try {
          val encryptedBackup = returning(new File(backup.getPath + "_encrypted")) { encryptedDbFile =>
            encryptedDbFile.deleteOnExit()
            IoUtils.writeBytesToFile(encryptedDbFile, meta ++ encryptedBytes)
          }

          backup.delete()
          encryptedBackup.renameTo(backup)
          new File(backup.getPath)
        }.mapFailureIfNot[BackupError](UnknownBackupError.apply)
      case (_, None) =>
        error(l"BKP Failed to create metadata")
        Failure(new Throwable("Failed to create metadata"))
      case (None, _) =>
        val msg = "Failed to encrypt backup"
        error(l"BKP $msg")
        Failure(new Exception(msg))
    }
  }

  override def importDatabase(userId:           UserId,
                              exportFile:       File,
                              targetDir:        File,
                              currentDbVersion: Int = BackupMetadata.currentDbVersion,
                              backupPassword:   Password): Try[File] = {
    verbose(l"BKP importDatabase($userId, ${exportFile.getAbsolutePath}, ${targetDir.getAbsolutePath}, $currentDbVersion, $backupPassword)")
    if (backupPassword.str.isEmpty)
      importUnencryptedDatabase(userId, exportFile, targetDir, currentDbVersion)
    else
      importEncryptedDatabase(userId, exportFile, targetDir, currentDbVersion, backupPassword)
  }

  private def decryptDatabase(file: File, password: Password, userId: UserId): Try[File] = {
    EncryptedBackupHeader.readEncryptedMetadata(file) match {
      case Some(metadata) =>
        libSodiumUtils.hash(userId.str, metadata.salt) match {
          case Some(hash) if hash.sameElements(metadata.uuidHash) =>
            val encryptedBackupBytes = IoUtils.readFileBytes(file, EncryptedBackupHeader.totalHeaderLength)
            libSodiumUtils.decrypt(encryptedBackupBytes, password, metadata.salt) match {
              case Some(decryptedDbBytes) =>
                verbose(l"BKP decrypted data: ${decryptedDbBytes.length}")
                val decryptedDbExport = File.createTempFile("wire_backup", ".zip")
                decryptedDbExport.deleteOnExit()
                IoUtils.writeBytesToFile(decryptedDbExport, decryptedDbBytes)
                Success(decryptedDbExport)
              case None =>
                error(l"BKP backup decryption failed")
                Failure(new Throwable("backup decryption failed"))
            }
          case Some(_) =>
            error(l"BKP Uuid hashes don't match")
            Failure(new Throwable("Uuid hashes don't match"))
          case None =>
            error(l"BKP Uuid hashing failed")
            Failure(new Throwable("Uuid hashing failed"))
        }
      case None =>
        error(l"BKP metadata could not be read")
        Failure(new Throwable("metadata could not be read"))
    }
  }

  private def importEncryptedDatabase(userId: UserId, exportFile: File, targetDir: File,
                                      currentDbVersion: Int = BackupMetadata.currentDbVersion,
                                      password: Password): Try[File] = {
    for {
      decryptedFile <- decryptDatabase(exportFile, password, userId)
      _ = verbose(l"BKP encrypted file: $exportFile, exists: ${exportFile.exists}, length: ${if (exportFile.exists) exportFile.length else 0}")
      _ = verbose(l"BKP decrypted file: $decryptedFile, exists: ${decryptedFile.exists}, length: ${if (decryptedFile.exists) decryptedFile.length else 0}")
      importedFile <- importUnencryptedDatabase(userId, decryptedFile, targetDir, currentDbVersion)
      _ = verbose(l"BKP imported file: $decryptedFile, exists: ${decryptedFile.exists}, length: ${if (decryptedFile.exists) decryptedFile.length else 0}")
    } yield importedFile
  }

  private def importUnencryptedDatabase(userId: UserId, exportFile: File, targetDir: File,
                                        currentDbVersion: Int = BackupMetadata.currentDbVersion): Try[File] =
    Try {
      verbose(l"BKP importUnencryptedDatabase($userId, ${exportFile.getAbsolutePath}, ${targetDir.getAbsolutePath}, $currentDbVersion)")
      verbose(l"BKP exportFile: $exportFile, exists: ${exportFile.exists}, size: ${if (exportFile.exists()) exportFile.length() else 0}")
      val zipFile = new ZipFile(exportFile)
      verbose(l"BKP -1")
      withResource(new ZipFile(exportFile)) { zip =>
        verbose(l"BKP 0")
        val metadataEntry = Option(zip.getEntry(backupMetadataFileName)).getOrElse { throw MetadataEntryNotFound }
        verbose(l"BKP 1")
        val metadataStr = withResource(zip.getInputStream(metadataEntry))(Source.fromInputStream(_).mkString)
        val metadata = decode[BackupMetadata](metadataStr).recoverWith {
          case err => Failure(InvalidMetadata.WrongFormat(err))
        }.get
        verbose(l"BKP 2")
        if (userId != metadata.userId) throw InvalidMetadata.UserId
        verbose(l"BKP 3")
        if (BackupMetadata.currentPlatform != metadata.platform) throw InvalidMetadata.Platform
        verbose(l"BKP 4")
        if (currentDbVersion < metadata.version) throw InvalidMetadata.DbVersion
        verbose(l"BKP 5")
        val dbFileName = getDbFileName(userId)
        val dbEntry = Option(zip.getEntry(dbFileName)).getOrElse { throw DbEntryNotFound }
        verbose(l"BKP 6")
        val walFileName = getDbWalFileName(userId)
        verbose(l"BKP walFileName: $walFileName")
        Option(zip.getEntry(walFileName)).foreach { walEntry =>
          IoUtils.copy(zip.getInputStream(walEntry), new File(targetDir, walFileName))
        }
        verbose(l"BKP 7")
        returning(new File(targetDir, dbFileName)) { dbFile =>
          verbose(l"BKP 8")
          IoUtils.copy(zip.getInputStream(dbEntry), dbFile)
          verbose(l"BKP 9")
        }
      }
    }.mapFailureIfNot[BackupError](UnknownBackupError.apply)


  private def getMetaDataBytes(password: Password, salt: Array[Byte], userId: UserId): Option[Array[Byte]] = {
    //This method returns the metadata in the format described here:
    //https://github.com/wearezeta/documentation/blob/master/topics/backup/use-cases/001-export-history.md

    import EncryptedBackupHeader.{logTag => _, _}

    libSodiumUtils.hash(userId.str, salt) match {
      case Some(uuidHash) if uuidHash.length == uuidHashLength =>
        val header = EncryptedBackupHeader(currentVersion, salt, uuidHash,
          libSodiumUtils.getOpsLimit, libSodiumUtils.getMemLimit)
        Some(serializeHeader(header))
      case Some(uuidHash) =>
        error(l"BKP uuidHash length invalid, expected: $uuidHashLength, got: ${uuidHash.length}")(logTag)
        None
      case None =>
        error(l"BKP Failed to hash account id for backup")(logTag)
        None
    }
  }

}

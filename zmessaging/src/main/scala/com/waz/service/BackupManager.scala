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
package com.waz.service

import java.io._
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.{ZipFile, ZipOutputStream}

import com.waz.ZLog.ImplicitTag._
import com.waz.db.ZMessagingDB
import com.waz.log.ZLog2._
import com.waz.model.UserId
import com.waz.model.otr.ClientId
import com.waz.service.BackupManager.InvalidBackup.{DbEntryNotFound, MetadataEntryNotFound}
import com.waz.utils.IoUtils.withResource
import com.waz.utils.Json.syntax._
import com.waz.utils.{IoUtils, JsonDecoder, JsonEncoder, returning, RichTry}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.io.Source
import scala.util.{Failure, Try}

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

  case class BackupMetadata(userId: UserId,
                            version: Int = BackupMetadata.currentDbVersion,
                            clientId: Option[ClientId] = None,
                            creationTime: Instant = Instant.now(),
                            platform: String = BackupMetadata.currentPlatform)

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

  def backupZipFileName(userHandle: String): String = {
    val timestamp = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Instant.now().getEpochSecond * 1000)
    s"Wire-$userHandle-Backup_$timestamp.android_wbu"
  }
  def backupMetadataFileName: String = "export.json"

  def getDbFileName(id: UserId): String = id.str
  def getDbWalFileName(id: UserId): String = id.str + "-wal"

  // The current solution writes the database file(s) directly to the newly created zip file.
  // This way we save memory, but it means that it takes longer before we can release the lock.
  // If this becomes the problem, we might consider first copying the database file(s) to the
  // external storage directory, release the lock, and then safely proceed with zipping them.
  def exportDatabase(userId: UserId, userHandle: String, databaseDir: File, targetDir: File): Try[File] =
    Try {
      returning(new File(targetDir, backupZipFileName(userHandle))) { zipFile =>
        zipFile.deleteOnExit()

        withResource(new ZipOutputStream(new FileOutputStream(zipFile))) { zip =>

          withResource(new ByteArrayInputStream(BackupMetadata(userId).toJsonString.getBytes("utf8"))) {
            IoUtils.writeZipEntry(_, zip, backupMetadataFileName)
          }

          val dbFileName = getDbFileName(userId)
          withResource(new BufferedInputStream(new FileInputStream(new File(databaseDir, dbFileName)))) {
            IoUtils.writeZipEntry(_, zip, dbFileName)
          }

          val walFileName = getDbWalFileName(userId)
          val walFile = new File(databaseDir, walFileName)
          verbose(l"WAL file: $walFile exists: ${walFile.exists}, length: ${if (walFile.exists) walFile.length else 0}")

          if (walFile.exists) withResource(new BufferedInputStream(new FileInputStream(walFile))) {
            IoUtils.writeZipEntry(_, zip, walFileName)
          }
        }

        verbose(l"database export finished: $zipFile . Data contains: ${zipFile.length} bytes")
      }
    }.mapFailureIfNot[BackupError](UnknownBackupError.apply)

  def importDatabase(userId: UserId, exportFile: File, targetDir: File, currentDbVersion: Int = BackupMetadata.currentDbVersion): Try[File] =
    Try {
      withResource(new ZipFile(exportFile)) { zip =>
        val metadataEntry = Option(zip.getEntry(backupMetadataFileName)).getOrElse { throw MetadataEntryNotFound }

        val metadataStr = withResource(zip.getInputStream(metadataEntry))(Source.fromInputStream(_).mkString)
        val metadata = decode[BackupMetadata](metadataStr).recoverWith {
          case err => Failure(InvalidMetadata.WrongFormat(err))
        }.get

        if (userId != metadata.userId) throw InvalidMetadata.UserId
        if (BackupMetadata.currentPlatform != metadata.platform) throw InvalidMetadata.Platform
        if (currentDbVersion < metadata.version) throw InvalidMetadata.DbVersion

        val dbFileName = getDbFileName(userId)
        val dbEntry = Option(zip.getEntry(dbFileName)).getOrElse { throw DbEntryNotFound }

        val walFileName = getDbWalFileName(userId)
        Option(zip.getEntry(walFileName)).foreach { walEntry =>
          IoUtils.copy(zip.getInputStream(walEntry), new File(targetDir, walFileName))
        }

        returning(new File(targetDir, dbFileName)) { dbFile =>
          IoUtils.copy(zip.getInputStream(dbEntry), dbFile)
        }
      }
    }.mapFailureIfNot[BackupError](UnknownBackupError.apply)


}

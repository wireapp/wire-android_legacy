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
package com.waz

import java.io._
import java.util.zip.{ZipFile, ZipOutputStream}

import com.waz.model.UserId
import com.waz.utils.IoUtils.withResource
import com.waz.utils.Json.syntax._
import com.waz.utils.{IoUtils, returning}
import org.scalatest._

import scala.util.Try

class BackupManagerSpec extends WordSpec with BeforeAndAfterAll with BeforeAndAfterEach with MustMatchers {

  import com.waz.service.BackupManager._

  private val testUserId = UserId()
  private val testDirectory =
    new File(s"${System.getProperty("java.io.tmpdir")}/${getClass.getSimpleName}_${System.currentTimeMillis()}")
  private val testMetadata = BackupMetadata(testUserId, version = 20)

  private def createFakeDatabase(targetDirectory: File = testDirectory): File =
    returning(new File(targetDirectory, getDbFileName(testUserId))) { file =>
      withResource(new PrintWriter(file)) { _.write("some content") }
  }

  private def createFakeDatabaseWal(targetDirectory: File = testDirectory): File =
    returning(new File(targetDirectory, getDbWalFileName(testUserId))) { file =>
      withResource(new PrintWriter(file)) { _.write("some content") }
    }

  private def createFakeBackup(metadata: Option[Array[Byte]] = Some(testMetadata.toJsonString.getBytes("utf8")),
                               database: Option[File] = Some(createFakeDatabase()),
                               databaseWal: Option[File] = Some(createFakeDatabaseWal()),
                               targetDirectory: File = testDirectory): File = {
    returning(new File(targetDirectory, "fake_backup.zip")) { zipFile =>
      withResource(new ZipOutputStream(new FileOutputStream(zipFile))) { zip =>
        metadata foreach { md =>
          withResource(new ByteArrayInputStream(md)) {
            IoUtils.writeZipEntry(_, zip, backupMetadataFileName)
          }
        }

        database foreach { db =>
          withResource(new BufferedInputStream(new FileInputStream(db))) {
            IoUtils.writeZipEntry(_, zip, getDbFileName(testUserId))
          }
        }

        databaseWal foreach { wal =>
          withResource(new BufferedInputStream(new FileInputStream(wal))) {
            IoUtils.writeZipEntry(_, zip, getDbWalFileName(testUserId))
          }
        }
      }
    }
  }

  override protected def beforeEach(): Unit = {
    if (!testDirectory.mkdir()) throw new RuntimeException("Cannot create directory for tests.")
  }

  override protected def afterEach(): Unit = {
    IoUtils.deleteRecursively(testDirectory)
  }

  private def getZipFileEntryNames(zipFile: ZipFile): Set[String] = {
    val iterator = zipFile.entries()
    Stream.continually(Try(iterator.nextElement())).takeWhile(_.isSuccess).map(_.get.getName).toSet
  }

  private def getAllFileNames(directory: File): Set[String] = {
    directory.listFiles().map(_.getName).toSet
  }

  "Exporting database" should {

    "create an export zip file with metadata and all database related files." in {
      val fakeDatabase = createFakeDatabase()
      createFakeDatabaseWal()
      val zipFile = exportDatabase(testUserId, userHandle = "TEST", databaseDir = fakeDatabase.getParentFile, targetDir = testDirectory).get

      withClue("Zip file should exist.") { zipFile.exists() mustBe true }
      withResource(new ZipFile(zipFile)) { zip =>
        withClue("Files inside test directory: " + getAllFileNames(testDirectory)) {
          getZipFileEntryNames(zip) mustBe Set(
            backupMetadataFileName,
            getDbFileName(testUserId),
            getDbWalFileName(testUserId)
          )
        }
      }
    }

  }

  "Importing database" should {

    "unzip backup file and fail if metadata file and db file not found." in {
      val fakeBackup = createFakeBackup(metadata = None, database = None)

      an [InvalidBackup] should be thrownBy importDatabase(testUserId, fakeBackup, testDirectory).get
    }

    "unzip backup file and fail if metadata file not found." in {
      val fakeBackup = createFakeBackup(metadata = None)
      an [InvalidBackup.MetadataEntryNotFound.type] should be thrownBy importDatabase(testUserId, fakeBackup, testDirectory).get
    }

    "unzip backup file and fail if db file not found." in {
      val fakeBackup = createFakeBackup(database = None)
      an [InvalidBackup.DbEntryNotFound.type] should be thrownBy importDatabase(testUserId, fakeBackup, testDirectory).get
    }

    "unzip backup file and fail if metadata format is invalid." in {
      val fakeBackup = createFakeBackup(metadata = Some(Array(1,2,3,4,5)))
      an [InvalidMetadata.WrongFormat] should be thrownBy importDatabase(testUserId, fakeBackup, testDirectory).get
    }

    "unzip backup file and fail if user ids are not the same." in {
      val metadataWithRandomUserId = BackupMetadata(UserId())
      val fakeBackup = createFakeBackup(metadata = Some(metadataWithRandomUserId.toJsonString.getBytes("utf-8")))

      an [InvalidMetadata.UserId.type] should be thrownBy importDatabase(testUserId, fakeBackup, testDirectory).get
    }

    "unzip backup file and fail if current database version is less then from metadata." in {
      val metadataWithDbVersionGreaterThenCurrent = BackupMetadata(testUserId, version = BackupMetadata.currentDbVersion + 1)
      val fakeBackup = createFakeBackup(metadata = Some(metadataWithDbVersionGreaterThenCurrent.toJsonString.getBytes("utf-8")))

      an [InvalidMetadata.DbVersion.type] should be thrownBy importDatabase(testUserId, fakeBackup, testDirectory).get
    }

    "unzip backup file successfully if all needed files are present and metadata is valid." in {
      val fakeBackup = createFakeBackup()
      val targetDirectory = new File(testDirectory, "test_target_dir")
      if (!targetDirectory.mkdir()) throw new RuntimeException("Cannot create target directory for test.")

      importDatabase(testUserId, fakeBackup, targetDirectory).get
      withClue("Files inside target directory: " + getAllFileNames(targetDirectory)) {
        getAllFileNames(targetDirectory) mustBe Set(
          getDbFileName(testUserId),
          getDbWalFileName(testUserId)
        )
      }
    }

    "unzip backup file successfully if all needed files (except wal file) are present and metadata is valid." in {
      val fakeBackup = createFakeBackup(databaseWal = None)
      val targetDirectory = new File(testDirectory, "test_target_dir")
      if (!targetDirectory.mkdir()) throw new RuntimeException("Cannot create target directory for test.")

      importDatabase(testUserId, fakeBackup, targetDirectory).get
      withClue("Files inside target directory: " + getAllFileNames(targetDirectory)) {
        getAllFileNames(targetDirectory) mustBe Set(getDbFileName(testUserId))
      }
    }

    "unzip backup file successfully if all needed files are present and metadata is valid (when current db version greater then from metadata)." in {
      val metadataWithDbVersionLessThenCurrent = BackupMetadata(testUserId, version = BackupMetadata.currentDbVersion - 1)
      val fakeBackup = createFakeBackup(metadata = Some(metadataWithDbVersionLessThenCurrent.toJsonString.getBytes("utf-8")))
      val targetDirectory = new File(testDirectory, "test_target_dir")
      if (!targetDirectory.mkdir()) throw new RuntimeException("Cannot create target directory for test.")

      importDatabase(testUserId, fakeBackup, targetDirectory).get
      withClue("Files inside target directory: " + getAllFileNames(targetDirectory)) {
        getAllFileNames(targetDirectory) mustBe Set(
          getDbFileName(testUserId),
          getDbWalFileName(testUserId)
        )
      }
    }

  }




}

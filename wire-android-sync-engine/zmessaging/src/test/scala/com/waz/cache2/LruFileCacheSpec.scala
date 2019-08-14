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
package com.waz.cache2

import java.io.{ ByteArrayInputStream, File }

import com.waz.threading.CancellableFuture
import com.waz.utils.IoUtils
import com.waz.utils.events.EventContext
import com.waz.{ FilesystemUtils, TestData, ZIntegrationSpec }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class LruFileCacheSpec extends ZIntegrationSpec {

  private def createLruFileCache(cacheDirectory: File = FilesystemUtils.createDirectoryForTest(),
                                 directorySizeThreshold: Long = 1024,
                                 sizeCheckingInterval: FiniteDuration = 0.seconds): FileCache[String] = {
    val (dir, threshold, interval) = (cacheDirectory, directorySizeThreshold, sizeCheckingInterval)
    new LruFileCache[String] {
      override protected def cacheDirectory: File                 = dir
      override protected def directorySizeThreshold: Long         = threshold
      override protected def sizeCheckingInterval: FiniteDuration = interval
      override protected implicit def ev: EventContext            = EventContext.Global
      override protected implicit def ec: ExecutionContext        = ExecutionContext.global
      override protected def createFileName(key: String): String  = key
    }
  }

  feature("CacheService") {

    scenario("Putting something in cache and getting back") {
      val key     = "key"
      val content = TestData.bytes(200)

      val cacheDir = FilesystemUtils.createDirectoryForTest()
      val cache    = createLruFileCache(cacheDir)
      for {
        _ <- cache.putBytes(key, content)
        _ = println(s"Cache directory content: ${cacheDir.listFiles().map(_.getName).mkString(" ")}")
        fromCache <- cache.findBytes(key)
      } yield {
        fromCache.nonEmpty shouldBe true
        fromCache.get shouldBe content
      }
    }

    scenario("Putting something in cache and removing it") {
      val key     = "key"
      val content = TestData.bytes(200)

      val cache = createLruFileCache()
      for {
        _         <- cache.putBytes(key, content)
        _         <- cache.remove(key)
        fromCache <- cache.findBytes(key)
      } yield {
        fromCache.isEmpty shouldBe true
      }
    }

    scenario("Putting file directly in cache") {
      val fileKey       = "key"
      val content       = TestData.bytes(200)
      val fileDirectory = FilesystemUtils.createDirectoryForTest()
      val file          = new File(fileDirectory, "temporary_file_name")
      IoUtils.copy(new ByteArrayInputStream(content), file)

      val cache = createLruFileCache()
      for {
        _         <- cache.put(fileKey, file, removeOriginal = true)
        fromCache <- cache.findBytes(fileKey)
      } yield {
        file.exists() shouldBe false
        fromCache.nonEmpty shouldBe true
        fromCache.get shouldBe content
      }
    }

    scenario("Putting file directly in cache in case when cache directory does not exist") {
      val fileKey       = "key"
      val content       = TestData.bytes(200)
      val fileDirectory = FilesystemUtils.createDirectoryForTest()
      val file          = new File(fileDirectory, "temporary_file_name")
      IoUtils.copy(new ByteArrayInputStream(content), file)

      val cacheDirectory = new File(fileDirectory, "not_exist")
      cacheDirectory.mkdirs()
      val cache = createLruFileCache(cacheDirectory)
      for {
        _         <- cache.put(fileKey, file, removeOriginal = true)
        fromCache <- cache.findBytes(fileKey)
      } yield {
        file.exists() shouldBe false
        fromCache.nonEmpty shouldBe true
        fromCache.get shouldBe content
      }
    }

    scenario("Putting file directly in cache should trigger cache cleanup if it is needed") {
      val fileKey       = "key"
      val (key1, key2)  = ("key1", "key2")
      val content       = TestData.bytes(200)
      val fileDirectory = FilesystemUtils.createDirectoryForTest()
      val file          = new File(fileDirectory, "temporary_file_name")
      IoUtils.copy(new ByteArrayInputStream(content), file)

      val cache = createLruFileCache(directorySizeThreshold = content.length * 2, sizeCheckingInterval = 0.seconds)
      for {
        _             <- cache.putBytes(key1, content)
        _             <- CancellableFuture.delay(1.second).future
        _             <- cache.putBytes(key2, content)
        _             <- cache.put(fileKey, file)
        _             <- CancellableFuture.delay(1.second).future //make sure that cache service has enough time to finish cleanup
        fromCache1    <- cache.findBytes(key1)
        fromCache2    <- cache.findBytes(key2)
        fromCacheFile <- cache.findBytes(fileKey)
      } yield {
        fromCache1.nonEmpty shouldBe false
        fromCache2.nonEmpty shouldBe true
        fromCache2.get shouldBe content
        fromCacheFile.nonEmpty shouldBe true
        fromCacheFile.get shouldBe content
      }
    }

    scenario("Lru functionality.") {
      val puttingTimeout                        = 1.second //https://bugs.openjdk.java.net/browse/JDK-8177809
      val directoryMaxSize                      = 1024
      val contentLength                         = 200
      val cacheCapacity                         = directoryMaxSize / contentLength
      val keys                                  = (0 until cacheCapacity).map(i => s"key$i")
      val contents                              = keys.map(_ -> TestData.bytes(contentLength)).toMap
      def timeoutFor(key: String): Future[Unit] = CancellableFuture.delay(puttingTimeout * keys.indexOf(key)).future

      val cache = createLruFileCache(directorySizeThreshold = directoryMaxSize, sizeCheckingInterval = 0.seconds)
      for {
        _          <- Future.sequence { keys.map(key => timeoutFor(key).flatMap(_ => cache.putBytes(key, contents(key)))) }
        fromCache0 <- cache.findBytes(keys(0))
        overflowKey     = "overflow"
        overflowContent = TestData.bytes(contentLength)
        _                 <- cache.putBytes(overflowKey, overflowContent) //this action should trigger cache cleanup process
        _                 <- CancellableFuture.delay(1.second).future //make sure that cache service has enough time to finish cleanup
        fromCache1        <- cache.findBytes(keys(1))
        fromCacheOverflow <- cache.findBytes(overflowKey)
      } yield {
        fromCache0.nonEmpty shouldBe true
        fromCache0.get shouldBe contents(keys(0))
        fromCache1 shouldBe None
        fromCacheOverflow.nonEmpty shouldBe true
        fromCacheOverflow.get shouldBe overflowContent
      }
    }

  }

}

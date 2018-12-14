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

import java.io.{ByteArrayInputStream, File}

import com.waz.cache2.CacheService.{AES_CBC_Encryption, NoEncryption}
import com.waz.model.AESKey
import com.waz.threading.CancellableFuture
import com.waz.utils.IoUtils
import com.waz.{FilesystemUtils, ZIntegrationSpec}

import scala.concurrent.Future
import com.waz.utils.events.EventContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

class CacheServiceSpec extends ZIntegrationSpec {

  private def testContent(size: Int): Array[Byte] = {
    val bytes = Array.ofDim[Byte](size)
    new Random().nextBytes(bytes)
    bytes
  }

  private def createCacheService(directorySizeThreshold: Long = 1024, sizeCheckingInterval: FiniteDuration = 0.seconds): CacheService =
    new LruFileCacheServiceImpl(FilesystemUtils.createDirectoryForTest(), directorySizeThreshold, sizeCheckingInterval)

  feature("CacheService") {

    scenario("Putting something in cache and getting back without encryption") {
      val key = "key"
      val content = testContent(200)

      val cache = createCacheService()
      for {
        _ <- cache.putBytes(key, content)(NoEncryption)
        fromCache <- cache.findBytes(key)(NoEncryption)
      } yield {
        fromCache.nonEmpty shouldBe true
        fromCache.get shouldBe content
      }
    }

    //TODO Remove usage of android Base64 class
    ignore("Putting something in cache and getting back with encryption") {
      val key = "key"
      val content = testContent(200)
      val encryption = AES_CBC_Encryption(AESKey())

      val cache = createCacheService()
      for {
        _ <- cache.putBytes(key, content)(encryption)
        fromCache <- cache.findBytes(key)(encryption)
      } yield {
        fromCache.nonEmpty shouldBe true
        fromCache.get shouldBe content
      }
    }

    scenario("Putting something in cache and removing it") {
      val key = "key"
      val content = testContent(200)

      val cache = createCacheService()
      for {
        _ <- cache.putBytes(key, content)(NoEncryption)
        _ <- cache.remove(key)
        fromCache <- cache.findBytes(key)(NoEncryption)
      } yield {
        fromCache.isEmpty shouldBe true
      }
    }

    scenario("Putting file directly in cache") {
      val fileKey = "key"
      val content = testContent(200)
      val fileDirectory = FilesystemUtils.createDirectoryForTest()
      val file = new File(fileDirectory, "temporary_file_name")
      IoUtils.copy(new ByteArrayInputStream(content), file)

      val cache = createCacheService()
      for {
        _ <- cache.putEncrypted(fileKey, file)
        fromCache <- cache.findBytes(fileKey)(NoEncryption)
      } yield {
        file.exists() shouldBe false
        fromCache.nonEmpty shouldBe true
        fromCache.get shouldBe content
      }
    }

    scenario("Putting file directly in cache should trigger cache cleanup if it is needed") {
      val fileKey = "key"
      val (key1, key2) = ("key1", "key2")
      val content = testContent(200)
      val fileDirectory = FilesystemUtils.createDirectoryForTest()
      val file = new File(fileDirectory, "temporary_file_name")
      IoUtils.copy(new ByteArrayInputStream(content), file)

      val cache = createCacheService(directorySizeThreshold = content.length * 2, sizeCheckingInterval = 0.seconds)
      for {
        _ <- cache.putBytes(key1, content)(NoEncryption)
        _ <- CancellableFuture.delay(1.second).future
        _ <- cache.putBytes(key2, content)(NoEncryption)
        _ <- cache.putEncrypted(fileKey, file)
        _ <- CancellableFuture.delay(1.second).future //make sure that cache service has enough time to finish cleanup
        fromCache1 <- cache.findBytes(key1)(NoEncryption)
        fromCache2 <- cache.findBytes(key2)(NoEncryption)
        fromCacheFile <- cache.findBytes(fileKey)(NoEncryption)
      } yield {
        fromCache1.nonEmpty shouldBe false
        fromCache2.nonEmpty shouldBe true
        fromCache2.get shouldBe content
        fromCacheFile.nonEmpty shouldBe true
        fromCacheFile.get shouldBe content
      }
    }

    scenario("Lru functionality.") {
      val puttingTimeout = 1.second //https://bugs.openjdk.java.net/browse/JDK-8177809
      val directoryMaxSize = 1024
      val contentLength = 200
      val cacheCapacity = directoryMaxSize / contentLength
      val keys = (0 until cacheCapacity).map(i => s"key$i")
      val contents = keys.map(_ -> testContent(contentLength)).toMap
      def timeoutFor(key: String): Future[Unit] = CancellableFuture.delay(puttingTimeout*keys.indexOf(key)).future

      val cache = createCacheService(directoryMaxSize, sizeCheckingInterval = 0.seconds)
      for {
        _ <- Future.sequence { keys.map(key => timeoutFor(key).flatMap(_ => cache.putBytes(key, contents(key))(NoEncryption))) }
        fromCache0 <- cache.findBytes(keys(0))(NoEncryption)
        overflowKey = "overflow"
        overflowContent = testContent(contentLength)
        _ <- cache.putBytes(overflowKey, overflowContent)(NoEncryption) //this action should trigger cache cleanup process
        _ <- CancellableFuture.delay(1.second).future //make sure that cache service has enough time to finish cleanup
        fromCache1 <- cache.findBytes(keys(1))(NoEncryption)
        fromCacheOverflow <- cache.findBytes(overflowKey)(NoEncryption)
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

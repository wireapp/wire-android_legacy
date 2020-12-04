package com.waz.utils.crypto

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import org.libsodium.jni.NaCl

import scala.util.{Failure, Success, Try}

class RandomBytes extends DerivedLogTag {
  import LibSodium._
  LibSodium.loadLibrary
  /**
   * Generates random byte array using libsodium
   * @param count - number of bytes to generate
   * @return - random bytes array
   */
  def apply(count: Int) : Array[Byte] = {
    val buffer = Array.ofDim[Byte](count)

    loadLibrary match {
      case Success(_) => randomBytes(buffer, count)
      case _ =>
        warn(l"Sodium failed to generate $count random bytes. Falling back to SecureRandom")
        ZSecureRandom.nextBytes(buffer)
    }
    buffer
  }

  @native
  protected def randomBytes(buffer: Array[Byte], count: Int) : Unit
}

object LibSodium {
  lazy val loadLibrary: Try[Unit] = {
    try {
      NaCl.sodium() // dynamically load the libsodium library
      System.loadLibrary("sodium")
      System.loadLibrary("randombytes")
      Success(())
    } catch {
      case error: Throwable => Failure(error)
    }
  }
}

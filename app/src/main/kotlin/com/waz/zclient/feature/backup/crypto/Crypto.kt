@file:Suppress("TooManyFunctions")

package com.waz.zclient.feature.backup.crypto

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.feature.backup.crypto.encryption.error.HashWrongSize
import com.waz.zclient.feature.backup.crypto.encryption.error.HashingFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.UnsatisfiedLink

class Crypto(private val cryptoWrapper: CryptoWrapper) {

    internal val loadLibrary: Either<Failure, Unit> by lazy {
        try {
            cryptoWrapper.loadLibrary()
            Either.Right(Unit)
        } catch (ex: UnsatisfiedLinkError) {
            Either.Left(UnsatisfiedLink)
        }
    }

    internal fun generateSalt(): Either<Failure, ByteArray> {
        val count = cryptoWrapper.pWhashSaltBytes()
        val buffer = ByteArray(count)
        cryptoWrapper.randomBytes(buffer)
        return loadLibrary.flatMap { Either.Right(buffer) }
    }

    internal fun generateNonce(): Either<Failure, ByteArray> {
        val count = cryptoWrapper.polyNpubBytes()
        val buffer = ByteArray(count)
        cryptoWrapper.randomBytes(buffer)
        return loadLibrary.flatMap { Either.Right(buffer) }
    }

    internal fun hashWithMessagePart(input: String, salt: ByteArray): Either<Failure, ByteArray> {
        val output = ByteArray(encryptExpectedKeyBytes())
        val passBytes = input.toByteArray()
        val pushMessage = cryptoWrapper.generatePwhashMessagePart(output, passBytes, salt)
        return if (pushMessage == 0) Either.Right(output) else Either.Left(HashingFailed)
    }

    internal fun opsLimit(): Int =
        cryptoWrapper.opsLimitInteractive()

    internal fun memLimit(): Int =
        cryptoWrapper.memLimitInteractive()

    internal fun aBytesLength(): Int =
        cryptoWrapper.polyABytes()

    internal fun encrypt(cipherText: ByteArray, msg: ByteArray, key: ByteArray, nonce: ByteArray) =
        cryptoWrapper.encrypt(cipherText, msg, key, nonce)

    internal fun decrypt(decrypted: ByteArray, cipherText: ByteArray, key: ByteArray, nonce: ByteArray) =
        cryptoWrapper.decrypt(decrypted, cipherText, key, nonce)

    internal fun encryptExpectedKeyBytes() = cryptoWrapper.aedPolyKeyBytes()

    internal fun decryptExpectedKeyBytes() = cryptoWrapper.aedPolyKeyBytes()

    internal fun checkExpectedKeySize(size: Int, expectedKeySize: Int, shouldLog: Boolean = true): Either<Failure, Unit> =
        if (size != expectedKeySize) {
            if (shouldLog) {
                Logger.verbose(TAG, "Key length invalid: $size did not match $expectedKeySize")
            }
            Either.Left(HashWrongSize)
        } else {
            Either.Right(Unit)
        }

    companion object {
        private const val TAG = "Crypto"
    }
}

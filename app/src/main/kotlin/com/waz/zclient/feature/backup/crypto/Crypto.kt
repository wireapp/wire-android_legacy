package com.waz.zclient.feature.backup.crypto

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.feature.backup.crypto.encryption.error.EncryptionInitialisationError
import com.waz.zclient.feature.backup.crypto.encryption.error.HashWrongSize
import com.waz.zclient.feature.backup.crypto.encryption.error.HashingFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.InvalidHeaderLength
import com.waz.zclient.feature.backup.crypto.encryption.error.InvalidKeyLength
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

    internal fun initEncryptState(initKey: ByteArray, initHeader: ByteArray) =
        initializeState(initKey, initHeader) { state: ByteArray, header: ByteArray, key: ByteArray ->
            cryptoWrapper.initPush(state, header, key)
        }

    internal fun initDecryptState(initKey: ByteArray, initHeader: ByteArray) =
        initializeState(initKey, initHeader) { state: ByteArray, header: ByteArray, key: ByteArray ->
            cryptoWrapper.initPull(state, header, key)
        }

    private fun initializeState(
        key: ByteArray,
        header: ByteArray,
        init: (ByteArray, ByteArray, ByteArray) -> Int
    ): Either<Failure, ByteArray> =
        if (header.size != cryptoWrapper.polyHeaderBytes()) {
            Either.Left(InvalidHeaderLength)
        } else if (key.size != decryptExpectedKeyBytes()) {
            Either.Left(InvalidKeyLength)
        } else {
            val state = ByteArray(STATE_BYTE_ARRAY_SIZE)
            if (init(state, header, key) != 0) {
                Either.Left(EncryptionInitialisationError)
            } else {
                Either.Right(state)
            }
        }

    internal fun generateSalt(): Either<Failure, ByteArray> {
        val count = cryptoWrapper.pWhashSaltBytes()
        val buffer = ByteArray(count)
        cryptoWrapper.randomBytes(buffer)
        return loadLibrary.flatMap { Either.Right(buffer) }
    }

    internal fun hash(input: String, salt: ByteArray): Either<Failure, ByteArray> {
        val output = ByteArray(encryptExpectedKeyBytes())
        val passBytes = input.toByteArray()
        val pushMessage = generatePwhashMessagePart(output, passBytes, salt)
        return pushMessage.takeIf { it == 0 }?.let { Either.Right(output) }
            ?: Either.Left(HashingFailed)
    }

    private fun generatePwhashMessagePart(output: ByteArray, passBytes: ByteArray, salt: ByteArray) =
        cryptoWrapper.generatePwhashMessagePart(output, passBytes, salt)

    internal fun opsLimit(): Int =
        cryptoWrapper.opsLimitInteractive()

    internal fun memLimit(): Int =
        cryptoWrapper.memLimitInteractive()

    internal fun streamHeaderLength() =
        cryptoWrapper.polyHeaderBytes()

    internal fun aBytesLength(): Int =
        cryptoWrapper.polyABytes()

    internal fun generatePushMessagePart(messageBytes: ByteArray, cipherText: ByteArray, msg: ByteArray) =
        cryptoWrapper.generatePushMessagePart(messageBytes, cipherText, msg)

    internal fun generatePullMessagePart(state: ByteArray, decrypted: ByteArray, cipherText: ByteArray) =
        cryptoWrapper.generatePullMessagePart(state, decrypted, cipherText)

    internal fun encryptExpectedKeyBytes() =
        cryptoWrapper.aedPolyKeyBytes()

    internal fun decryptExpectedKeyBytes() =
        cryptoWrapper.secretStreamPolyKeyBytes()

    internal fun checkExpectedKeySize(size: Int, expectedKeySize: Int, shouldLog: Boolean = true): Either<Failure, Unit> =
        when (size != expectedKeySize) {
            true -> {
                if (shouldLog) {
                    Logger.verbose(TAG, "Key length invalid: $size did not match $expectedKeySize")
                }
                Either.Left(HashWrongSize)
            }
            false -> Either.Right(Unit)
        }

    companion object {
        //Got this magic number from https://github.com/wearezeta/documentation/blob/master/topics/backup/use-cases/001-export-history-v2.md
        private const val STATE_BYTE_ARRAY_SIZE = 52
        private const val TAG = "Crypto"
    }
}

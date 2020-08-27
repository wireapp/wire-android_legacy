package com.waz.zclient.feature.backup.crypto

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.feature.backup.crypto.encryption.error.EncryptionInitialisationError
import com.waz.zclient.feature.backup.crypto.encryption.error.HashingFailed
import com.waz.zclient.feature.backup.crypto.encryption.error.InvalidHeaderLength
import com.waz.zclient.feature.backup.crypto.encryption.error.InvalidKeyLength
import com.waz.zclient.feature.backup.crypto.encryption.error.UnsatisfiedLink
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium
import java.security.SecureRandom

class Crypto {

    private val secureRandom: SecureRandom by lazy { SecureRandom() }

    internal val loadLibrary: Either<Failure, Unit> by lazy {
        try {
            NaCl.sodium() // dynamically load the libsodium library
            System.loadLibrary("sodium")
            System.loadLibrary("randombytes")
            Either.Right(Unit)
        } catch (ex: UnsatisfiedLinkError) {
            Either.Left(UnsatisfiedLink)
        }
    }

    private fun initializeState(key: ByteArray, header: ByteArray, init: (ByteArray, ByteArray, ByteArray) -> Int): Either<Failure, ByteArray> =
        if (header.size != Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()) {
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

    internal fun initEncryptState(initKey: ByteArray, initHeader: ByteArray) =
        initializeState(initKey, initHeader) { state: ByteArray, header: ByteArray, key: ByteArray ->
            Sodium.crypto_secretstream_xchacha20poly1305_init_push(state, header, key)
        }

    internal fun initDecryptState(initKey: ByteArray, initHeader: ByteArray) =
        initializeState(initKey, initHeader) { state: ByteArray, header: ByteArray, key: ByteArray ->
            Sodium.crypto_secretstream_xchacha20poly1305_init_pull(state, header, key)
        }

    internal fun generateSalt(): ByteArray {
        val count = Sodium.crypto_pwhash_saltbytes()
        val buffer = ByteArray(count)
        loadLibrary
            .onSuccess {
                Sodium.randombytes(buffer, count)
            }.onFailure {
                Logger.warn(TAG, "Libsodium failed to generate $count random bytes. Falling back to SecureRandom")
                secureRandom.nextBytes(buffer)
            }
        return buffer
    }

    internal fun opsLimit(): Int = Sodium.crypto_pwhash_opslimit_interactive()
    internal fun memLimit(): Int = Sodium.crypto_pwhash_memlimit_interactive()
    internal fun streamHeader() = ByteArray(streamHeaderLength())
    internal fun streamHeaderLength() = Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()
    internal fun aBytesLength(): Int = Sodium.crypto_secretstream_xchacha20poly1305_abytes()
    internal fun generatePushMessagePart(messageBytes: ByteArray, cipherText: ByteArray, msg: ByteArray) =
        Sodium.crypto_secretstream_xchacha20poly1305_push(
            messageBytes,
            cipherText,
            intArrayOf(),
            msg,
            msg.size,
            byteArrayOf(),
            0,
            Sodium.crypto_secretstream_xchacha20poly1305_tag_final().toShort()
        )

    internal fun generatePullMessagePart(state: ByteArray, decrypted: ByteArray, cipherText: ByteArray) =
        Sodium.crypto_secretstream_xchacha20poly1305_pull(
            state,
            decrypted,
            intArrayOf(),
            byteArrayOf(1),
            cipherText,
            cipherText.size,
            byteArrayOf(),
            0
        )

    internal fun encryptExpectedKeyBytes() = Sodium.crypto_aead_chacha20poly1305_keybytes()
    internal fun decryptExpectedKeyBytes() = Sodium.crypto_secretstream_xchacha20poly1305_keybytes()
    private fun generatePwhashMessagePart(output: ByteArray, passBytes: ByteArray, salt: ByteArray) =
        Sodium.crypto_pwhash(
            output,
            output.size,
            passBytes,
            passBytes.size,
            salt,
            opsLimit(),
            memLimit(),
            Sodium.crypto_pwhash_alg_default()
        )

    internal fun hash(input: String, salt: ByteArray): Either<Failure, ByteArray> {
        val output = ByteArray(encryptExpectedKeyBytes())
        val passBytes = input.toByteArray()
        val pushMessage = generatePwhashMessagePart(output, passBytes, salt)
        return pushMessage.takeIf { it == 0 }?.let { Either.Right(output) }
            ?: Either.Left(HashingFailed)
    }

    companion object {
        //Got this magic number from https://github.com/joshjdevl/libsodium-jni/blob/master/src/test/java/org/libsodium/jni/crypto/SecretStreamTest.java#L48
        private const val STATE_BYTE_ARRAY_SIZE = 52
        private const val TAG = "Crypto"
    }
}

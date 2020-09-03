@file:Suppress("TooManyFunctions")
package com.waz.zclient.feature.backup.crypto

import com.waz.zclient.core.extension.describe
import com.waz.zclient.core.logging.Logger.Companion.verbose
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium
import java.util.logging.Logger

class CryptoWrapper {

    fun opsLimitInteractive() =
        Sodium.crypto_pwhash_opslimit_interactive()

    fun memLimitInteractive() =
        Sodium.crypto_pwhash_memlimit_interactive()

    fun polyABytes() =
        Sodium.crypto_aead_xchacha20poly1305_ietf_abytes()

    fun polyNpubBytes() = 24
        //Sodium.crypto_aead_chacha20poly1305_ietf_npubbytes()

    fun encrypt(cipherText: ByteArray, msg: ByteArray, key: ByteArray, nonce: ByteArray): Int {
        verbose("CryptoWrapper","CRY encrypt(\nCRY cipherText: ${cipherText.describe()},\nCRY msg: ${msg.describe()},\nCRY key: ${key.describe()},\nCRY nonce: ${nonce.describe()}\nCRY )")

        return Sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
                cipherText,
                intArrayOf(1),
                msg,
                msg.size,
                byteArrayOf(),
                0,
                byteArrayOf(),
                nonce,
                key
        )
    }

    fun decrypt(decrypted: ByteArray, cipherText: ByteArray, key: ByteArray, nonce: ByteArray): Int {
        verbose("CryptoWrapper","CRY decrypt(\nCRY decrypted: ${decrypted.describe()},\nCRY cipherText: ${cipherText.describe()},\nCRY key: ${key.describe()},\nCRY nonce: ${nonce.describe()}\nCRY )")

        return Sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
                decrypted,
                intArrayOf(1),
                byteArrayOf(),
                cipherText,
                cipherText.size,
                byteArrayOf(),
                0,
                nonce,
                key
        )
    }

    fun generatePwhashMessagePart(output: ByteArray, passBytes: ByteArray, salt: ByteArray) =
        Sodium.crypto_pwhash(
            output,
            output.size,
            passBytes,
            passBytes.size,
            salt,
            Sodium.crypto_pwhash_opslimit_interactive(),
            Sodium.crypto_pwhash_memlimit_interactive(),
            Sodium.crypto_pwhash_alg_default()
        )

    fun aedPolyKeyBytes() =
        Sodium.crypto_aead_chacha20poly1305_keybytes()

    fun pWhashSaltBytes() =
        Sodium.crypto_pwhash_saltbytes()

    fun randomBytes(buffer: ByteArray) =
        Sodium.randombytes(buffer, buffer.size)

    fun loadLibrary() {
        NaCl.sodium() // dynamically load the libsodium library
        System.loadLibrary("sodium")
        System.loadLibrary("randombytes")
    }
}

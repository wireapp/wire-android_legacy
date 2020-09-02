@file:Suppress("TooManyFunctions")
package com.waz.zclient.feature.backup.crypto

import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium

class CryptoWrapper {

    fun opsLimitInteractive() =
        Sodium.crypto_pwhash_opslimit_interactive()

    fun memLimitInteractive() =
        Sodium.crypto_pwhash_memlimit_interactive()

    fun polyABytes() =
        Sodium.crypto_aead_xchacha20poly1305_ietf_abytes()

    fun encrypt(cipherText: ByteArray, msg: ByteArray, key: ByteArray) =
        Sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
            cipherText,
            intArrayOf(1),
            msg,
            msg.size,
            byteArrayOf(),
            0,
                byteArrayOf(),
            ByteArray(Sodium.crypto_aead_chacha20poly1305_ietf_npubbytes()),
            key
        )

    fun decrypt(decrypted: ByteArray, cipherText: ByteArray, key: ByteArray) =
        Sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
            decrypted,
            intArrayOf(1),
            byteArrayOf(),
            cipherText,
            cipherText.size,
            byteArrayOf(),
            0,
            ByteArray(Sodium.crypto_aead_chacha20poly1305_ietf_npubbytes()),
            key
        )

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
        Sodium.randombytes(buffer, pWhashSaltBytes())

    fun loadLibrary() {
        NaCl.sodium() // dynamically load the libsodium library
        System.loadLibrary("sodium")
        System.loadLibrary("randombytes")
    }
}

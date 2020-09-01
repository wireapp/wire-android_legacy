package com.waz.zclient.feature.backup.crypto

import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium

class CryptoWrapper {

    fun polyHeaderBytes() =
        Sodium.crypto_secretstream_xchacha20poly1305_headerbytes()

    fun opsLimitInteractive() =
        Sodium.crypto_pwhash_opslimit_interactive()

    fun memLimitInteractive() =
        Sodium.crypto_pwhash_memlimit_interactive()

    fun polyABytes() =
        Sodium.crypto_secretstream_xchacha20poly1305_abytes()

    fun generatePushMessagePart(state: ByteArray, cipherText: ByteArray, msg: ByteArray) =
        Sodium.crypto_secretstream_xchacha20poly1305_push(
            state,
            cipherText,
            intArrayOf(),
            msg,
            msg.size,
            byteArrayOf(),
            0,
            Sodium.crypto_secretstream_xchacha20poly1305_tag_final().toShort()
        )

    fun generatePullMessagePart(state: ByteArray, decrypted: ByteArray, cipherText: ByteArray) =
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

    fun secretStreamPolyKeyBytes() =
        Sodium.crypto_secretstream_xchacha20poly1305_keybytes()

    fun pWhashSaltBytes() =
        Sodium.crypto_pwhash_saltbytes()

    fun initPush(state: ByteArray, header: ByteArray, key: ByteArray) =
        Sodium.crypto_secretstream_xchacha20poly1305_init_push(state, header, key)

    fun initPull(state: ByteArray, header: ByteArray, key: ByteArray) =
        Sodium.crypto_secretstream_xchacha20poly1305_init_pull(state, header, key)

    fun randomBytes(buffer: ByteArray) =
        Sodium.randombytes(buffer, pWhashSaltBytes())

    fun loadLibrary() {
        NaCl.sodium() // dynamically load the libsodium library
        System.loadLibrary("sodium")
        System.loadLibrary("randombytes")
    }

}

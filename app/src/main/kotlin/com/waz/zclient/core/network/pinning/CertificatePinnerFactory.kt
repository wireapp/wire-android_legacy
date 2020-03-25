package com.waz.zclient.core.network.pinning

import com.waz.zclient.core.utilities.base64.Base64Transformer
import okhttp3.CertificatePinner
import java.security.MessageDigest

class CertificatePinnerFactory private constructor() {

    companion object {
        fun create(pin: CertificatePin, pinGenerator: PinGenerator = PinGenerator()): CertificatePinner =
            CertificatePinner.Builder()
                .add(pin.domain, pinGenerator.pin(pin.certificate))
                .build()
    }

    class PinGenerator(
        private val messageDigest: MessageDigest = MessageDigest.getInstance(PUBLIC_KEY_ALGORITHM),
        private val base64Transformer: Base64Transformer = Base64Transformer()
    ) {

        internal fun pin(base64Cert: ByteArray): String? =
            "$PINS${base64Transformer.encode(publicKey(base64Cert))}"

        private fun publicKey(certificate: ByteArray) =
            messageDigest.digest(certificate)

        companion object {
            private const val PINS = "sha256/"
            private const val PUBLIC_KEY_ALGORITHM = "SHA-256"
        }
    }
}

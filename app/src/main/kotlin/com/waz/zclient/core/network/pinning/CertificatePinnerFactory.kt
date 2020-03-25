package com.waz.zclient.core.network.pinning

import com.waz.zclient.core.extension.encodeBase64
import okhttp3.CertificatePinner
import java.security.MessageDigest

class CertificatePinnerFactory private constructor() {

    companion object {

        fun createCertificatePinner(
            pin: CertificatePin,
            pinGenerator: PinGenerator = PinGenerator()
        ): CertificatePinner = CertificatePinner.Builder()
            .add(
                pin.domain,
                pinGenerator.pin(pin.certificate)
            ).build()
    }

    class PinGenerator(
        private val messageDigest: MessageDigest = MessageDigest.getInstance(PUBLIC_KEY_ALGORITHM)
    ) {

        internal fun pin(base64Cert: ByteArray): String? =
            "$PINS${publicKey(base64Cert).encodeBase64()}"

        private fun publicKey(certificate: ByteArray) =
            messageDigest.digest(certificate)

        companion object {
            private const val PINS = "sha256/"
            private const val PUBLIC_KEY_ALGORITHM = "SHA-256"
        }
    }
}

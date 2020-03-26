package com.waz.zclient.core.network.pinning

data class CertificatePin(
    val domain: String,
    val certificate: ByteArray
) {
    override fun equals(other: Any?) =
        this === other &&
            this.javaClass == other.javaClass &&
            domain == other.domain &&
            certificate.contentEquals(other.certificate)

    override fun hashCode(): Int {
        var result = domain.hashCode()
        result = 31 * result + certificate.contentHashCode()
        return result
    }
}

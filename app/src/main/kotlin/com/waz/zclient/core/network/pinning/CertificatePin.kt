package com.waz.zclient.core.network.pinning

data class CertificatePin(
    val domain: String,
    val certificate: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CertificatePin

        if (domain != other.domain) return false
        if (!certificate.contentEquals(other.certificate)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = domain.hashCode()
        result = 31 * result + certificate.contentHashCode()
        return result
    }
}

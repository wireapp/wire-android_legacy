package com.waz.zclient.features.settings.devices

import com.waz.zclient.R
import com.waz.zclient.clients.Client

enum class ClientVerification {
    VERIFIED,
    UNVERIFIED
}

data class ClientItem(val client: Client) {

    val verified = ClientVerification.UNVERIFIED

    fun verificationIcon() =
        if (verified == ClientVerification.VERIFIED) R.drawable.shield_full else R.drawable.shield_half
}

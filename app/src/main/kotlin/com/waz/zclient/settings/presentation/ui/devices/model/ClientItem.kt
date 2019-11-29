package com.waz.zclient.settings.presentation.ui.devices.model

import com.waz.zclient.R

enum class ClientVerification {
    VERIFIED,
    UNVERIFIED
}

data class ClientItem(val time: String,
                      val label: String,
                      val id: String,
                      val verified: ClientVerification = ClientVerification.UNVERIFIED) {

    val verificationIcon = if (verified == ClientVerification.VERIFIED) R.drawable.shield_full else R.drawable.shield_half
}

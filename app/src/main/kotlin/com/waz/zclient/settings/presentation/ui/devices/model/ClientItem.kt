package com.waz.zclient.settings.presentation.ui.devices.model

import android.os.Parcel
import android.os.Parcelable
import com.waz.zclient.R
import kotlinx.android.parcel.Parcelize

enum class ClientVerification {
    VERIFIED,
    UNVERIFIED
}

@Parcelize
data class ClientItem(val time: String,
                      val label: String,
                      val id: String,
                      val verified: ClientVerification = ClientVerification.UNVERIFIED) : Parcelable {

    val verificationIcon = if (verified == ClientVerification.VERIFIED) R.drawable.shield_full else R.drawable.shield_half

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun describeContents(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

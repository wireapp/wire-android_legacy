package com.waz.zclient.core.network

import android.content.Context
import com.waz.zclient.core.extension.networkInfo

/**
 * Class which returns information about the network connection state.
 */
class NetworkHandler(private val context: Context) {
    val isConnected get() = context.networkInfo?.isConnected
}

package com.waz.client

import android.security.NetworkSecurityPolicy
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(NetworkSecurityPolicy::class)
class NetworkSecurityPolicyShadow {
    @Implementation
    fun isCleartextTrafficPermitted() = true

    @Implementation
    fun isCleartextTrafficPermitted(host: String) = true

    companion object {

        @JvmStatic
        @Implementation
        fun getInstance(): NetworkSecurityPolicy = try {
            val shadow = Class.forName("android.security.NetworkSecurityPolicy")
            shadow.newInstance() as NetworkSecurityPolicy
        } catch (e: Exception) {
            throw AssertionError()
        }
    }
}

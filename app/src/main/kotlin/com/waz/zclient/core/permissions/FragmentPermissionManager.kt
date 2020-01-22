package com.waz.zclient.core.permissions

import androidx.fragment.app.Fragment
import com.waz.zclient.core.permissions.requesting.PermissionRequesterFactory
import com.waz.zclient.utilities.device.SdkVersionChecker
import java.lang.ref.WeakReference

class FragmentPermissionManager(
    fragment: Fragment,
    sdkVersionChecker: SdkVersionChecker = SdkVersionChecker()
) : PermissionManager() {

    private val fragmentRef = WeakReference<Fragment>(fragment)

    init {
        fragmentRef.get()?.let {
            it.lifecycle.addObserver(this)
            requester = PermissionRequesterFactory.getPermissionRequester(
                it.requireActivity(),
                sdkVersionChecker
            )
            checker = { permission -> isGranted(it.requireContext(), permission) }
        }
    }
}

package com.waz.zclient.core.permissions

import androidx.fragment.app.Fragment
import com.waz.zclient.utilities.device.SdkVersionChecker
import java.lang.ref.WeakReference

class FragmentPermissionManager(
    fragment: Fragment,
    override val sdkChecker: SdkVersionChecker
): PermissionManager() {

    private val fragmentRef = WeakReference<Fragment>(fragment)

    init {
        fragmentRef.get()?.let{
            it.lifecycle.addObserver(this)
            requester = it::requestPermissions
            checker = { permChecker -> isGranted(permChecker, it.requireContext()) }
        }
    }
}

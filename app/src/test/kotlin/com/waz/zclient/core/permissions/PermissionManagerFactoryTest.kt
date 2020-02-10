package com.waz.zclient.core.permissions

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.waz.zclient.UnitTest
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

class PermissionManagerFactoryTest : UnitTest() {

    @Mock
    private lateinit var activity: AppCompatActivity

    @Mock
    private lateinit var fragment: Fragment

    @Test
    fun `given permission managed has been requested from an Fragment, then return ActivityPermissionManager `() {
        val lifecycle = LifecycleRegistry(Mockito.mock(LifecycleOwner::class.java))
        Mockito.`when`(fragment.lifecycle).thenReturn(lifecycle)

        val permissionManager = PermissionManagerFactory.getPermissionManager(fragment)
        permissionManager shouldBeInstanceOf FragmentPermissionManager::class.java
    }

    @Test
    fun `given permission managed has been requested from an Activity, then return ActivityPermissionManager `() {
        val lifecycle = LifecycleRegistry(Mockito.mock(LifecycleOwner::class.java))
        Mockito.`when`(activity.lifecycle).thenReturn(lifecycle)

        val permissionManager = PermissionManagerFactory.getPermissionManager(activity)
        permissionManager shouldBeInstanceOf ActivityPermissionManager::class.java
    }
}

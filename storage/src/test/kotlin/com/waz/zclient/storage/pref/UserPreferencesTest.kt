package com.waz.zclient.storage.pref

import android.content.Context
import android.content.SharedPreferences
import com.waz.zclient.storage.pref.global.GlobalPreferences
import com.waz.zclient.storage.pref.user.UserPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class UserPreferencesTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var globalPreferences: GlobalPreferences

    private lateinit var userPreferences: UserPreferences

    @Before
    fun setUp() {
        userPreferences = UserPreferences(context, globalPreferences)
    }

    @Test
    fun `given a userId, when get(userId) called, gets sharedPreferences file associated with userId`() {
        val userId = "TEST_ID"
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(mock(SharedPreferences::class.java))

        userPreferences.get(userId)

        verify(context).getSharedPreferences("userPref_$userId", Context.MODE_PRIVATE)
    }

    @Test
    fun `when current() called, gets sharedPreferences file associated with currently active userId in globalPrefs`() {
        val activeUserId = "ACTIVE_ID"
        `when`(globalPreferences.activeUserId).thenReturn(activeUserId)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(mock(SharedPreferences::class.java))

        userPreferences.current()

        verify(context).getSharedPreferences("userPref_$activeUserId", Context.MODE_PRIVATE)
    }
}

package com.waz.zclient.core.ui.backgroundasset

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import com.waz.zclient.UnitTest
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class ActivityBackgroundAssetObserverTest : UnitTest() {

    @Mock
    private lateinit var activity: AppCompatActivity

    @Mock
    private lateinit var backgroundAssetOwner: BackgroundAssetOwner

    @Mock
    private lateinit var view: View

    private lateinit var activityBackgroundAssetObserver: ActivityBackgroundAssetObserver

    @Before
    fun setUp() {
        activityBackgroundAssetObserver = ActivityBackgroundAssetObserver()
    }

    @Test
    fun `given a backgroundAssetOwner, when loadBackground is called, calls assetOwner's fetchBackgroundAsset method`() {
        `when`(backgroundAssetOwner.backgroundAsset).thenReturn(mock(LiveData::class))

        activityBackgroundAssetObserver.loadBackground(activity, backgroundAssetOwner, view)

        verify(backgroundAssetOwner).fetchBackgroundAsset()
    }

    //TODO: when loadBackground is called, sets the asset to view's background
}

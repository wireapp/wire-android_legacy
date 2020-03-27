package com.waz.zclient.core.images

import android.graphics.drawable.Drawable
import android.view.View
import com.bumptech.glide.request.transition.Transition
import com.waz.zclient.UnitTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class ViewBackgroundTargetTest : UnitTest() {

    @Mock
    private lateinit var view: View

    @Mock
    private lateinit var drawable: Drawable

    @Mock
    private lateinit var transition: Transition<in Drawable>

    private lateinit var viewBackgroundTarget: ViewBackgroundTarget

    @Before
    fun setUp() {
        viewBackgroundTarget = ViewBackgroundTarget(view)
    }

    @Test
    fun `given null as error drawable, when onLoadFailed is called, does nothing`() {
        viewBackgroundTarget.onLoadFailed(null)

        verifyNoInteractions(view)
    }

    @Test
    fun `given an error drawable, when onLoadFailed is called, sets the drawable as view's background`() {
        viewBackgroundTarget.onLoadFailed(drawable)

        verify(view).background = drawable
    }

    @Test
    fun `given a drawable and a transition, when onResourceReady is called, sets the drawable as view's background`() {
        viewBackgroundTarget.onResourceReady(drawable, transition)

        verify(view).background = drawable
        verifyNoInteractions(transition)
    }
}

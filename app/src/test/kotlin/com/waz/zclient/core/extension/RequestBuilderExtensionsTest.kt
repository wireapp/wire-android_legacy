package com.waz.zclient.core.extension

import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.request.RequestOptions
import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import org.amshove.kluent.shouldContain
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class RequestBuilderExtensionsTest : UnitTest() {

    @Mock
    private lateinit var requestBuilder: RequestBuilder<Drawable>

    @Captor
    private lateinit var requestOptionsCaptor: ArgumentCaptor<RequestOptions>

    @Test
    fun `given some transformations, when addTransformations is called, adds a requestOption with given transformations`() {
        val transformation1 = mockTransformation()
        val transformation2 = mockTransformation()

        requestBuilder.addTransformations(transformation1, transformation2)

        verify(requestBuilder).apply(capture(requestOptionsCaptor))

        val multiTransformation = MultiTransformation(transformation1, transformation2)
        val options = requestOptionsCaptor.value
        options.transformations.values shouldContain multiTransformation
    }

    companion object {
        private fun mockTransformation() = mock(BitmapTransformation::class.java)
    }
}

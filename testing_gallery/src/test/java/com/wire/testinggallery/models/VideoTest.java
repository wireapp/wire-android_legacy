package com.wire.testinggallery.models;

import android.app.Activity;
import android.content.Intent;

import com.wire.testinggallery.AndroidTest;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class VideoTest extends AndroidTest {

    private Video video;

    @Before
    public void setUp() {
        video = new Video();
    }

    @Test
    public void shouldReturnCorrectPosition() {
        assertThat(video.getPosition()).isEqualTo(1);
    }

    @Test
    public void shouldReturnCorrectName() {
        assertThat(video.getName()).isEqualTo("video");
    }

    @Test
    public void shouldReturnCorrectMimeType() {
        assertThat(video.getMimeType()).isEqualTo("video/mp4");
    }

    @Test
    public void shouldReturnCorrectExtension() {
        assertThat(video.getExtension()).isEqualTo("mp4");
    }

    @Test
    public void shouldHandleActionCorrectly() {
        final Activity activity = mock(Activity.class);
        video.handle(activity);

        verify(activity).setResult(eq(Activity.RESULT_OK), any(Intent.class));
        verify(activity).finish();
        verifyNoMoreInteractions(activity);
    }
}

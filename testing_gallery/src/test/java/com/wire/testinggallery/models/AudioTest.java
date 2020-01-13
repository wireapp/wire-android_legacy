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

public class AudioTest extends AndroidTest {

    private Audio audio;

    @Before
    public void setUp() {
        audio = new Audio();
    }

    @Test
    public void shouldReturnCorrectPosition() {
        assertThat(audio.getPosition()).isEqualTo(2);
    }

    @Test
    public void shouldReturnCorrectName() {
        assertThat(audio.getName()).isEqualTo("audio");
    }

    @Test
    public void shouldReturnCorrectMimeType() {
        assertThat(audio.getMimeType()).isEqualTo("audio/mp4a-latm");
    }

    @Test
    public void shouldReturnCorrectExtension() {
        assertThat(audio.getExtension()).isEqualTo("m4a");
    }

    @Test
    public void shouldHandleActionCorrectly() {
        final Activity activity = mock(Activity.class);
        audio.handle(activity);

        verify(activity).setResult(eq(Activity.RESULT_OK), any(Intent.class));
        verify(activity).finish();
        verifyNoMoreInteractions(activity);
    }
}

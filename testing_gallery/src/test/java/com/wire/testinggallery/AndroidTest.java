package com.wire.testinggallery;

import android.content.Context;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;

/**
 * Base class for tests that require android components mocking.
 * Inherit from this class to create a test.
 */
@RunWith(RobolectricTestRunner.class)
public abstract class AndroidTest {

    @Rule
    public TestRule injectMocksRule = (base, description) -> {
        MockitoAnnotations.initMocks(AndroidTest.this);
        return base;
    };

    public static Context context() {
        return RuntimeEnvironment.systemContext;
    }

    public static File cacheDir() {
        return RuntimeEnvironment.systemContext.getCacheDir();
    }
}

package com.wire.testinggallery;

import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Base class for Unit tests. Inherit from it to create test cases which DO NOT contain android
 * framework dependencies or components.
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class UnitTest { }

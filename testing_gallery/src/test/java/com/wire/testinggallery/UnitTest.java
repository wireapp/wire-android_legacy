package com.wire.testinggallery;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Base class for Unit tests. Inherit from it to create test cases which DO NOT
 * contain android framework dependencies or components. 
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class UnitTest {

    @Rule
    public TestRule injectMocksRule = (base, description) -> {
        MockitoAnnotations.initMocks(UnitTest.this);
        return base;
    };
}

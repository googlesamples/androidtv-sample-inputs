/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sampletvinputlib;

import com.example.android.sampletvinput.model.InternalProviderData;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests that core and custom data objects can be created using the InternalProviderData class
 * and retrieved successfully with proper error handling
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 23, manifest = "src/main/AndroidManifest.xml")
public class InternalProviderDataTest extends TestCase {
    private static final String KEY_SPLASHSCREEN = "splashscreen";
    private static final String KEY_PREMIUM_CHANNEL = "premium";
    private static final String SPLASHSCREEN_URL = "http://example.com/splashscreen.jpg";

    @Test
    public void testEqualityInOrder() throws InternalProviderData.ParseException {
        // Test a variety of use cases that may arise
        // Test equality of two objects exactly the same
        InternalProviderData providerData1 = new InternalProviderData();
        providerData1.setVideoType(0);
        providerData1.put(KEY_SPLASHSCREEN, SPLASHSCREEN_URL);
        InternalProviderData providerData2 = new InternalProviderData();
        providerData2.setVideoType(0);
        providerData2.put(KEY_SPLASHSCREEN, SPLASHSCREEN_URL);
        assertEquals(providerData1, providerData2);
        assertEquals(providerData1.hashCode(), providerData2.hashCode());
    }

    @Test
    public void testEqualityOutOfOrder() throws InternalProviderData.ParseException {
        // Test equality of two objects in different orders
        InternalProviderData providerData1 = new InternalProviderData();
        providerData1.put(KEY_SPLASHSCREEN, SPLASHSCREEN_URL);
        providerData1.put(KEY_PREMIUM_CHANNEL, true);
        providerData1.setRepeatable(false);
        InternalProviderData providerData2 = new InternalProviderData();
        providerData2.setRepeatable(false);
        providerData2.put(KEY_PREMIUM_CHANNEL, true);
        providerData2.put(KEY_SPLASHSCREEN, SPLASHSCREEN_URL);
        // Confirm that they are in a different order
        assertNotSame(providerData1.toString(), providerData2.toString());
        // Confirm they are technically equal
        assertEquals(providerData1, providerData2);
        assertEquals(providerData1.hashCode(), providerData2.hashCode());

        // Confirm that the values of each key do matter and it's not just checking the existence
        // of the key
        providerData1.put(KEY_PREMIUM_CHANNEL, false);
        assertNotSame(providerData1, providerData2);
        assertNotSame(providerData1.hashCode(), providerData2.hashCode());
    }

    @Test
    public void testExceptionThrowing() {
        // Test throwing exceptions
        String brokenJson = "hello world";
        try {
            InternalProviderData internalProviderData = new InternalProviderData(brokenJson);
            // Incorrectly proceeding with generation
            fail("This is not correctly formatted JSON and should not be parsed.");
        } catch (InternalProviderData.ParseException e) {
            // Exception correctly handled
        }
    }

    @Test
    public void testCustomDataMethods() throws InternalProviderData.ParseException {
        // Test custom data methods
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.put(KEY_SPLASHSCREEN, SPLASHSCREEN_URL);
        assertTrue(internalProviderData.has(KEY_SPLASHSCREEN));
        assertNotNull(internalProviderData.get(KEY_SPLASHSCREEN));
        // Test that invalid keys should not throw exceptions but return false and null
        assertFalse(internalProviderData.isRepeatable());
        assertFalse(internalProviderData.has(KEY_PREMIUM_CHANNEL));
        assertNull(internalProviderData.get(KEY_PREMIUM_CHANNEL));
    }
}

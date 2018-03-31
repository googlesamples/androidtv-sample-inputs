/*
 * Copyright 2016 The Android Open Source Project.
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

package com.google.android.media.tv.companionlibrary.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.android.media.tv.companionlibrary.BuildConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Test {@link Advertisement} can be properly generated with builder pattern, copied from another
 * {@link Advertisement} instance and parsed by {@link InternalProviderData}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21,
    manifest = "src/main/AndroidManifest.xml")
public class AdvertisementTest {
    private static final long START_TIME_MS = 0;
    private static final long STOP_TIME_MS = 1;
    private static final String AD_REQUEST_URL = "http://example.com/ad?A=B&C=D";
    private static final Advertisement ADVERTISEMENT = new Advertisement.Builder()
            .setStartTimeUtcMillis(START_TIME_MS)
            .setStopTimeUtcMillis(STOP_TIME_MS)
            .setType(Advertisement.TYPE_VAST)
            .setRequestUrl(AD_REQUEST_URL)
            .build();

    @Test
    public void testBuilder() {
        assertEquals(START_TIME_MS, ADVERTISEMENT.getStartTimeUtcMillis());
        assertEquals(STOP_TIME_MS, ADVERTISEMENT.getStopTimeUtcMillis());
        assertEquals(Advertisement.TYPE_VAST, ADVERTISEMENT.getType());
        assertEquals(AD_REQUEST_URL, ADVERTISEMENT.getRequestUrl());
    }

    @Test
    public void testCopy() {
        Advertisement advertisementCopy = new Advertisement.Builder(ADVERTISEMENT).build();
        assertEquals(ADVERTISEMENT, advertisementCopy);
        compareAdvertisement(ADVERTISEMENT, advertisementCopy);
    }

    @Test
    public void testInvalidType() {
        try {
            new Advertisement.Builder()
                    .setType(Integer.MAX_VALUE)
                    .build();
            fail("This is an invalid type. It should be caught.");
        } catch (IllegalStateException e) {
            // Exception successfully caught
        }
    }

    /**
     * Tests {@link Advertisement} implements {@link Comparable} interface correctly.
     */
    @Test
    public void testComparable() {
        Advertisement advertisementB = new Advertisement.Builder()
                .setStartTimeUtcMillis(0)
                .setStopTimeUtcMillis(3)
                .build();
        Advertisement advertisementC = new Advertisement.Builder()
                .setStartTimeUtcMillis(4)
                .setStopTimeUtcMillis(5)
                .build();

        List<Advertisement> adListA = new ArrayList<>(3);
        List<Advertisement> adListB = new ArrayList<>(3);
        adListA.add(advertisementC);
        adListA.add(advertisementB);
        adListA.add(ADVERTISEMENT);
        adListB.add(advertisementB);
        adListB.add(advertisementC);
        adListB.add(ADVERTISEMENT);
        Collections.sort(adListA);
        Collections.sort(adListB);
        assertEquals(adListA.size(), adListB.size());
        // Two lists with same elements should be in the same order after sorting.
        for (int i = 0; i < adListA.size(); i++) {
            assertEquals(adListA.get(i), adListB.get(i));
            compareAdvertisement(adListA.get(i), adListB.get(i));
        }
        assertEquals(0, adListB.get(0).getStartTimeUtcMillis());
        assertEquals(1, adListB.get(0).getStopTimeUtcMillis());
        assertEquals(3, adListB.get(1).getStopTimeUtcMillis());
        assertEquals(4, adListB.get(2).getStartTimeUtcMillis());
    }

    private void compareAdvertisement(Advertisement advertisementA, Advertisement advertisementB) {
        assertEquals(advertisementA.getStartTimeUtcMillis(),
                advertisementB.getStartTimeUtcMillis());
        assertEquals(advertisementA.getStopTimeUtcMillis(), advertisementB.getStopTimeUtcMillis());
        assertEquals(advertisementA.getRequestUrl(), advertisementB.getRequestUrl());
        assertEquals(advertisementA.getType(), advertisementB.getType());
    }
}

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

import com.example.android.sampletvinput.model.Advertisement;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21, manifest = "src/main/AndroidManifest.xml")
public class AdvertisementTest extends TestCase {
    @Test
    public void testAdComparable() {
        Advertisement advertisementA = new Advertisement.Builder()
                .setStartTimeUtcMillis(0)
                .setStopTimeUtcMillis(1)
                .build();
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
        adListA.add(advertisementA);
        adListB.add(advertisementB);
        adListB.add(advertisementC);
        adListB.add(advertisementA);
        Collections.sort(adListA);
        Collections.sort(adListB);
        assertEquals(adListA.get(0), adListB.get(0));
        assertEquals(adListA.get(1), adListB.get(1));
        assertEquals(adListA.get(2), adListB.get(2));
        assertEquals(0, adListB.get(0).getStartTimeUtcMillis());
        assertEquals(1, adListB.get(0).getStopTimeUtcMillis());
        assertEquals(3, adListB.get(1).getStopTimeUtcMillis());
        assertEquals(4, adListB.get(2).getStartTimeUtcMillis());
    }
}

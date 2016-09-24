<!---
 Copyright 2016 Google Inc. All rights reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Insert Ads into TV Input Service

## Introduction

The Sample Android TV Input project supports linear [VAST]
(http://www.iab.com/guidelines/digital-video-ad-serving-template-vast-3-0/) video ads via
[Google IMA3 SDK](https://developers.google.com/interactive-media-ads/). Video ads can be played
either when tuning to a new channel or at scheduled positions inside programs.

Most traditional mobile ads solutions cannot be applied to TV input service since there is no direct
user interaction for TV input service. Linear video ads behave the same as traditional TV ads, thus
can be used in TV input service. Although the TV watcher cannot click on the ads for more
information, developers can still earn revenue from impressions.

## Getting Started

The TV Input Framework Companion Library supports linear VAST ads playback. All you need to do is
apply for your own VAST Tag (request URL) from your ads provider and insert ad information into your
channels or programs.

### Getting your VAST Tag

Developers can request video ads from the following sources:
* [DoubleClick for Publishers (DFP)](https://www.doubleclickbygoogle.com/solutions/revenue-management/dfp/)
* [Ad Exchange for Video](https://www.doubleclickbygoogle.com/solutions/digital-marketing/ad-exchange/)
* [AdSense for Video (AFV)](https://support.google.com/adsense/answer/1705822?hl=en) and
[AdSense for Games (AFG)](https://support.google.com/adsense/answer/1705831?hl=en)
* Third-party ad servers


To verify whether your video ad response is VAST compliant, install the
[Google Ads Mobile Video Suite Inspector]
(https://developers.google.com/interactive-media-ads/docs/sdks/android/vastinspector).

### Ads Insertion in Channels

Ads in each channel will be played when TV watchers tune to this channel. Information will be
stored in [TvContract.Channels#COLUMN_INTERNAL_PROVIDER_DATA]
(https://developer.android.com/reference/android/media/tv/TvContract.Channels.html#COLUMN_INTERNAL_PROVIDER_DATA).
To retrieve ads data:

```java
List<Advertisement> ads = InternalProviderDataUtil.parseAds(channel.getInternalProviderData());
```

To schedule ads playback in channels, simply add the following advertisement element to your channel
metadata in xmltv.xml. Note: The following request URL is only for testing. You should replace it
with the VAST tag requested from your ads providers.

```xml
<channel id="com.example.android.sampletvinput.2-2" repeat-programs="true">
    ...
    <advertisement
        type="VAST">
        <request-url><![CDATA[https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator=]]></request-url>
    </advertisement>
</channel>
```

You can also insert ads into a channel programmatically during a periodic EPG sync.

```java
public class SampleJobService extends EpgSyncJobService {

    @Override
    public List<Channel> getChannels() {

        // Build advertisement list for the channel.
        Advertisement channelAd = new Advertisement.Builder()
                .setType(Advertisement.TYPE_VAST)
                .setRequestUrl(YOUR_REQUEST_URL)
                .build();
        List<Advertisement> channelAdList = new ArrayList<>();
        channelAdList.add(channelAd);

        // Add a channel programmatically
        InternalProviderData internalProviderData = new InternalProviderData();
        InternalProviderDataUtil.insertAds(internalProviderData, channelAdList);
        Channel channelTears = new Channel.Builder()
                ...
                .setInternalProviderData(internalProviderData)
                .build();
        channelList.add(channelTears);
        return channelList;
    }
}
```

To prevent the annoyance of too many channel ads within a small period of time, BaseTVInputService
will not show two channel ads within 5 minutes of each other on the same channel. The time window
is reset each time a channel ad completes, and does not reset when tuning to other channels.
To change this time interval, use setMinimumOnTuneAdInterval().

### Ads Insertion in Programs

Similar to channel ads insertion, ads data for each program is stored in
[TvContract.Programs#COLUMN_INTERNAL_PROVIDER_DATA]
(https://developer.android.com/reference/android/media/tv/TvContract.Programs.html#COLUMN_INTERNAL_PROVIDER_DATA).
To retrieve ads data:

```java
List<Advertisement> ads = InternalProviderDataUtil.parseAds(program.getInternalProviderData());
```

Note: If you decide to insert ads inside programs, the program duration should be extended to
include the additional ads time. Program duration should be program content duration plus total ads
duration. Also, start/stop time of ads should be within the program duration. The TV input program
may lose the last few seconds due to video buffering delay, so you should not schedule ads at the
end of the program, i.e., no post-roll ads.

To schedule ads playback inside programs, simply add the following advertisement elements to your
program metadata in xmltv.xml. Note: The following request URLs are only for testing. You should
replace these with VAST tags requested from your ads providers.

```xml
<programme
    start="20150817000000 +0000"
    stop="20150817003000 +0000">
    ...
    <advertisement
        start="20150817000010 +0000"
        stop="20150817000020 +0000"
        type="VAST">
        <request-url><![CDATA[https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator=]]></request-url>
    </advertisement>
    <advertisement
        start="20150817000030 +0000"
        stop="20150817000040 +0000"
        type="VAST">
        <request-url><![CDATA[https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dredirectlinear&correlator=]]></request-url>
    </advertisement>
</programme>
```

You can also insert ads into a program programmatically during a periodic EPG sync.

```java
public class SampleJobService extends EpgSyncJobService {

    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel, long startMs, long endMs) {
        // Build Advertisement list for the program.
        Advertisement programAd1 = ...;
        Advertisement programAd2 = ...;
        List<Advertisement> programAdList = new ArrayList<>();
        programAdList.add(programAd1);
        programAdList.add(programAd2);

        // Programmatically add channel
        List<Program> programsTears = new ArrayList<>();
        InternalProviderData internalProviderData = new InternalProviderData();
        ...
        InternalProviderDataUtil.insertAds(internalProviderData, programAdList);
        programsTears.add(new Program.Builder()
                ...
                .setInternalProviderData(internalProviderData)
                .build());
        return programsTears;
    }
}
```

## Limitation

Since there is no direct interaction between TV watchers and the TV input app, ads cannot be skipped
or clicked.

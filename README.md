# Sample for live TV Input Framework (TIF) on Android TV

This app is designed to show how to build live TV apps for Android TV.  The sample is a service that once installed, it's recognized and run by the default TV app.

It consists of:

* Simple TV input: 2 channels from local video files
* Rich TV input: 4 channels served from Google Cloud Storage consisting of MP4 videos, HLS stream and MPEG-DASH stream

Users can set up these TV inputs using the Live Channels app.

## Dependencies
* ExoPlayer: http://developer.android.com/guide/topics/media/exoplayer.html

## Setup Instructions
* Compile the project and install the app to your Android TV device.
* Start the pre-installed system app Live Channels (which does not show up in Apps on Home screen until there is at least one TV input service or a physical input like HDMI1).
* To set up the Rich TV input:
    - Either click Search upon starting Live Channels app to search for channels
    - Or when running Live Channels app, 
        + Click ENTER to bring out Recent Channels
        + Click DOWN to enter TV options
        + Click RIGHT to Channel sources and select it
        + Click DOWN to select Rich Input and hit ENTER
        + Click ADD CHANNELS NOW to add 3 channels of MP4 videos, HLS stream and MPEG-DASH stream
* To watch sample channels, simply toggle UP and DOWN to switch channels
* Visit Channel Sources -> Rich Input -> Settings to see mock options for input settings.

## References and How to report bugs
* [Android TV Developer Documentation: TV Input Framework](http://developer.android.com/training/tv/tif/index.html)

## How to make contributions?
Please read and follow the steps in the CONTRIBUTING.md

## License
See LICENSE

## Google+
Android TV Community Page on Google+ [https://g.co/androidtvdev](https://g.co/androidtvdev)

## Change List
Version 1.0

## Notice

Images/videos used in this sample are courtesy of the Blender Foundation, shared under copyright or Creative Commons license.

* Elephant's Dream: (c) copyright 2006, Blender Foundation / Netherlands Media Art Institute / www.elephantsdream.org
* Sintel: (c) copyright Blender Foundation | www.sintel.org
* Tears of Steel: (CC) Blender Foundation | mango.blender.org
* Big Buck Bunny: (c) copyright 2008, Blender Foundation / www.bigbuckbunny.org

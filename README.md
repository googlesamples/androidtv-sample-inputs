# Basic sample for live TV Input Framework (TIF) on Android TV

This app is designed to show how to build live TV apps for Android TV.  The sample is a service that once installed, it's recognized and run by the default TV app.

It consists of:

* Local inputs: a demo channel listing of 3 local video files
* Online inputs: a configurable listing of 9 channels served from Google Storage in the cloud

Users can set up these inputs from the Live Channels app.

## Dependencies

## Setup Instructions
* Compile the project and install the app to your Android TV device.
* Start the pre-installed system Live Channels (which does not show up in Apps on Home screen until there is at least one TV input service or a physical input like HDMI1).
* Click Search button to search for channels, which sets up 3 local channels. Now you can toggle channels by clicking UP/DOWN buttons.
* To set up the Online inputs, click Enter to bring out TV options and click DOWN and then RIGHT to Channel sources and select it.
* Click DOWN to select Online Inputs Sample and hit ENTER to fetch a configurable listing of 9 channels from Google Storage in the Cloud.
* Again click UP/DOWN buttons to toggle through all channels like a remote control and see simulated TV channels from both local and online inputs.
* Visit Channel Sources -> Local/Online Inputs Sample -> Settings to see mock options for input settings.

## References and How to report bugs
* [Android TV Developer Documentation: TV Input Framework](http://developer.android.com/training/tv/tif/index.html)

## How to make contributions?
Please read and follow the steps in the CONTRIBUTING.md

## License
See LICENSE

## Google+
Android TV Community Page on Google+ [https://g.co/androidtvdev](https://g.co/androidtvdev)
## Change List

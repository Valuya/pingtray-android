# pingtray for Android

The phone side of [pingtray](https://github.com/Valuya/pingtray): network
latency measured where you are, and the phone's GPS for pingtray on the
laptop.

- Pings each destination (real ICMP, through Android's unprivileged ping
  sockets) and probes it over HTTP (a HEAD on a fresh connection, timed per
  phase: DNS, connect, TLS, answer), in a background service.
- Records the phone's GPS with the measurements, and sends the positions to
  pingtray on the laptop in OwnTracks' format, queued while the laptop is out
  of reach.
- A permanent notification shows the current latency and a SmokePing-style
  mini graph of the last 5 minutes.
- Graph: SmokePing-style smoke for the reference series (tap a curve to pick
  it), the others as faint step lines, ping and HTTP on separate scales,
  pinch to zoom, drag to scroll, tap for the details of a moment.
- Map: the track coloured by connection quality, dotted where the route
  between two positions is unknown.
- Notes: what the graph cannot know ("crowded train").

## Setup

Install, allow location (precise) and notifications. To send positions to
pingtray on the laptop, scan the "set up pingtray for Android" QR code in
pingtray's settings (Phone positions) with the phone's camera.

## Build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

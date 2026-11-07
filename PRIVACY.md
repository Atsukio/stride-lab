# Stride Lab privacy policy

Effective 7 November 2026.

Stride Lab analyses your running form on your phone. **It has no internet access**: the app doesn't request
Android's internet permission, so nothing you record can leave your phone through the app. There are no
accounts, ads, analytics or tracking.

## What the app stores

All of this is kept in the app's private storage on your phone. Other apps can't read it.

- **Videos** you record, or copies of videos you import from your gallery.
- **Pose data and results** worked out from each video: body landmark positions, form measurements and coaching comments.
- **Watch and sensor readings** taken while you record, if you connect a watch or sensor: heart rate, cadence and speed.
  These are health and fitness readings. They're used only to show your stats and compare them with your form.
- **A crash log**: if the app crashes, the error's technical trace (code locations, the time and your Android version, but no videos or results) is saved so it can be fixed. It's never sent anywhere. You can share it yourself from **About > Storage and crash log**, or clear it there.
- **Spoken cues** (off by default): if you turn them on during a whole run, a short coaching phrase is spoken by the phone's own text-to-speech. The app only uses voices that work without a network, and stores nothing from it.
- **Settings**: your height, and the name and Bluetooth address of the watch or sensor you connected, so it can reconnect.

The app turns off Android's cloud backup and device-to-device transfer for its data, so none of it is copied to
Google Drive or to a new phone.

## Sharing

Nothing is shared unless you choose to. **Share** on a result makes a summary image of your measurements and,
if you tick them, a still from your video and the clip itself. It then opens Android's share sheet, and you pick
the app that receives them, such as a messaging or email app. From then on that app's own privacy policy applies.
Only the app you pick can read the shared files. A copy stays in the app's private cache until your next share,
or until Android clears the cache.

## Deleting your data

- Delete a clip or a whole run from its screen in the app.
- Remove old videos but keep their results, or clear the crash log, from **About > Storage and crash log**.
- **Forget** a connected watch from the watch dialog.
- Uninstall the app, or clear its storage in Android settings, to remove everything.

## Permissions

| Permission | Why |
|---|---|
| Camera | To film your running. |
| Nearby devices (Bluetooth scan and connect) | To find and connect to your watch, heart-rate strap or foot pod. Scanning is declared as not used to work out your location. |
| Notifications (Android 13 and later) and a foreground service | So a clip keeps being analysed when you leave the app, with a progress notification and a "ready" notification. The notifications show only a percentage and a message, and are never sent anywhere. |
| Location (Android 11 and older only) | Android requires it on these versions to scan for Bluetooth devices. The app never reads your location. |

Importing a video uses Android's photo picker, which gives the app only the video you choose.

## Third-party code

The app uses open-source libraries, listed in the app under **About → Open-source licences** and in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Google's MediaPipe library brings in a Google data-transport
component that some Google SDKs use to send diagnostics. Without the internet permission it can't send anything.

## Children

The app isn't aimed at children and doesn't knowingly collect anything from anyone. Everything it stores stays on the phone.

## Changes and contact

Changes to this policy will be published in this repository with a new effective date.
Questions: open an issue at https://github.com/Atsukio/stride-lab/issues.

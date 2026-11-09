# Stride Lab

An Android app that watches a runner on a treadmill, measures their running form, and suggests improvements.
Everything runs on the phone: there is no internet permission, no cloud service, and no backup of videos.

## How it works

1. **Record** a side-on clip (10–30 seconds, set in Settings; 15 by default) after a 5-second countdown, or **import** one from the gallery.
   The app asks for 60 fps where the camera supports it. A Bluetooth camera remote (volume, Enter or
   play/pause key) starts the countdown, cancels it, or stops recording. No Bluetooth permission is needed.
2. **Pose detection.** [MediaPipe Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker)
   (full model, bundled in the APK) finds 33 body landmarks in every frame. The video is decoded in one pass by the
   hardware decoder while earlier frames are still being analysed; files it can't handle fall back to a slower path.
   Each frame is detected on its own, then the whole track is cleaned: swapped legs are put back, one-off jumps
   removed and jitter smoothed without lagging behind fast feet (`analysis/PoseCleaner.kt`).
   **Analyse while recording** (on by default, in Settings) runs pose detection on a second camera stream during
   the clip, so results are ready almost as soon as it ends. Tiny thumbnails of every camera frame are matched to the
   saved video to line the poses up frame for frame (`pose/LiveAlignment.kt`). If live analysis can't keep up, the
   recording drops below 60 fps or the phone gets hot, the clip is analysed after recording and the app offers to
   turn the setting off.
3. **Gait analysis** (`analysis/GaitAnalyzer.kt`) finds each foot landing and measures:
   cadence, overstride (ankle ahead of hips at landing), shin angle at landing, knee bend at landing and during stance,
   vertical bounce, trunk lean, elbow angle, and the left/right difference in landing distance.
   Pixel distances are converted to centimetres from the treadmill belt when a speed is known: a planted foot
   moves backwards at belt speed, which also gives an **estimated height** you can save to Settings.
   Without a speed, the runner's height from Settings is used instead.
4. **Coaching** (`analysis/Coach.kt`) turns the numbers into comments, cues and drills using fixed rules.
   Some thresholds adjust to the treadmill speed if you enter it. Results are compared with the previous session.
5. **Replay** the clip at 1×, ½× or ¼× speed, or frame by frame, with the detected skeleton drawn on top
   (cleaned, or **Raw** to see exactly what the detector found). Each foot landing is ringed.
6. **See what the numbers mean.** Each measurement has a bar showing it against its good range, and an
   **At landing** card shows every foot landing with the overstride, shin angle, knee bend and lean drawn on it.
   **Analyse again** reruns a clip with the current version and settings.

## Recording and practice

- **Setup guide** on first launch (and from the home screen): where to put the phone and why.
- **Framing check:** before recording, the camera screen draws your skeleton live and says what to fix
  (head or feet cut off, facing the camera, too small, off-centre, too dark).
- **Metronome** at a chosen step rate, on the camera and run screens; the cadence card can set it to your target.
- **Progress:** every measurement charted across your clips against its good range.
- **Steady part only:** the first steps while your pace settles, and any slowdown at the end, are left out
  of the measurements (`GaitAnalyzer.steadyStretch`); the results say which seconds were used.
- **Milestones:** a measurement shows a star the first time it reaches its good range, or when it is closer
  than in any earlier clip (`analysis/PersonalBests.kt`).
- **Last time:** the replay can draw the previous clip's skeleton, dashed, at the same step of the stride and the same
  size (`analysis/GhostAligner.kt`), so differences in form show.
- **Focus plan:** start a three-week plan from a result's main focus. Home shows the day, the cue and drill, and how
  your latest check-in clip measured up (`analysis/FocusPlan.kt`).
- **Background analysis:** leave the screen or the app while a clip is analysed. A progress notification shows
  while it runs and another says when it's ready (`data/AnalysisRunner.kt`, `AnalysisService.kt`). Home shows
  clips still being analysed.
- **Spoken cues:** optional, during a whole run, the phone's own offline voice says your focus cue after a minute
  and then every five minutes (`audio/SpokenCues.kt`). Nothing is sent online; with no offline voice it stays silent.
- **Camera and run screens:** a viewfinder frame, a skeleton that glows and pulses at each foot landing
  (`analysis/LiveStrikeDetector.kt`), a countdown ring and a ring that fills as the clip records. Figures count up on
  the results.
- **Appearance:** a dark "lab" look by default, with light and system options in Settings.
- **Storage:** remove old videos but keep their results; a local crash log you can share yourself.
- **Share results** from the results screen: a summary image of every measurement, optionally with an annotated
  landing still, a written PDF report, the clip, or the clip re-encoded with your skeleton and landing measurements
  drawn on, sent through Android's share sheet to the app you choose. The app itself never uploads.
- **Compare two clips** from the results screen: both videos side by side, looped over the same landings and
  played at matching speeds so the strides line up, with a table of how each measurement changed.

## Watch and sensors

The app connects to standard Bluetooth heart-rate and running speed/cadence sensors. That covers a Garmin
Forerunner (965 etc.) in its **Virtual Run** activity, other watches broadcasting heart rate, chest straps
and foot pods. Tap **Connect watch** on the capture or run screen. The device is remembered and reconnects automatically.

- **Live readings** (heart rate, cadence, pace) appear while recording and are saved with each clip.
- **Treadmill speed** is taken from the watch when you leave the speed field empty.
- **Cadence cross-check:** if the video and the watch disagree by more than 8%, the clip is flagged.
- **Whole run mode:** start a run from the home screen and take a clip every 10 minutes or so (a Bluetooth remote
  press on the run screen opens the camera and starts the countdown). The run summary charts each form metric
  over the run against heart rate and says what changed, e.g. "Overstride rose from 12 to 19 cm between
  minute 5 and minute 40 (heart rate 142 → 168)."

Readings never leave the phone. Bluetooth scanning is declared as not used for location (Android 12+).
Keep the app open with the screen on during a whole run; it doesn't record in the background.

## Filming tips

- Treadmill, with the phone side-on at hip height, about 2–3 m away.
- Whole body, head to feet, in frame for the whole clip.
- 10–15 seconds of steady running.

## Limitations (v0.1)

- Side-on footage only. Front and rear views (knee collapse, hip drop, crossover) aren't analysed yet.
- Heel versus forefoot strike can't be judged reliably from pose landmarks.
- Comments are coaching suggestions, not medical advice.

## Building

Requires JDK 21 and the Android SDK (compileSdk 36). Built with Gradle 9.6 and Android Gradle Plugin 9.4. Gradle runs on JDK 21 whichever JDK starts it
(see `gradle/gradle-daemon-jvm.properties`), so Android Studio's bundled JDK is fine. App code targets Java 17.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # analysis tests on a synthetic runner
./gradlew assembleRelease      # shrunk release build (sign it before installing)
./gradlew connectedDebugAndroidTest   # instrumented tests: share image and intent, storage, crash log, screens
```

The instrumented tests need an emulator or phone and don't use pose detection. If more than one device is
attached, set `ANDROID_SERIAL` to the one to use, so they never run on a phone you didn't mean (`export ANDROID_SERIAL=emulator-5554`).

The first build downloads the pose model (~9 MB) into `app/src/main/assets/` and checks its SHA-256, so a changed
or corrupted file stops the build. It's ignored by git. GitHub Actions tests, lints and builds every push.
The app itself never connects to the internet.

## Project layout

```
app/src/main/java/com/atsukio/runningcoach/
  analysis/   Pure Kotlin: pose track, gait metrics, coaching rules (unit-tested)
  pose/       MediaPipe frame-by-frame pose extraction
  data/       Session storage in app-private files, settings
  share/      Summary image, PDF report, skeleton-overlay video and text for sharing a result
  ui/         Compose screens: home, capture, analysis progress, results
```

## Licence and privacy

Stride Lab is released under the [MIT License](LICENSE). The libraries and pose model it bundles keep their own
licences, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and shown in the app under **About**.
See [PRIVACY.md](PRIVACY.md) for what the app stores and why.

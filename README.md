# Visual Running Coach

An Android app that watches a runner on a treadmill, measures their running form, and suggests improvements.
Everything runs on the phone: there is no internet permission, no cloud service, and no backup of videos.

## How it works

1. **Record** a 15-second side-on clip with a 5-second countdown, or **import** one from the gallery.
   The app asks for 60 fps where the camera supports it. A Bluetooth camera remote (volume, Enter or
   play/pause key) starts the countdown, cancels it, or stops recording. No Bluetooth permission is needed.
2. **Pose detection.** [MediaPipe Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker)
   (full model, bundled in the APK) finds 33 body landmarks in every frame.
3. **Gait analysis** (`analysis/GaitAnalyzer.kt`) finds each foot landing and measures:
   cadence, overstride (ankle ahead of hips at landing), shin angle at landing, knee bend at landing and during stance,
   vertical bounce, trunk lean, elbow angle, and the left/right difference in landing distance.
   Pixel distances are converted to centimetres using the runner's height (set in Settings).
4. **Coaching** (`analysis/Coach.kt`) turns the numbers into comments, cues and drills using fixed rules.
   Some thresholds adjust to the treadmill speed if you enter it. Results are compared with the previous session.
5. **Replay** the clip at 1×, ½× or ¼× speed with the detected skeleton drawn on top. Each foot landing is ringed.

## Filming tips

- Treadmill, with the phone side-on at hip height, about 2–3 m away.
- Whole body, head to feet, in frame for the whole clip.
- 10–15 seconds of steady running.

## Limitations (v0.1)

- Side-on footage only. Front and rear views (knee collapse, hip drop, crossover) aren't analysed yet.
- Heel versus forefoot strike can't be judged reliably from pose landmarks.
- Comments are coaching suggestions, not medical advice.

## Building

Requires JDK 17 and the Android SDK (compileSdk 36).

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # analysis tests on a synthetic runner
```

The first build downloads the pose model (~9 MB) into `app/src/main/assets/`. It's ignored by git.
The app itself never connects to the internet.

## Project layout

```
app/src/main/java/com/atsukio/runningcoach/
  analysis/   Pure Kotlin: pose track, gait metrics, coaching rules (unit-tested)
  pose/       MediaPipe frame-by-frame pose extraction
  data/       Session storage in app-private files, settings
  ui/         Compose screens: home, capture, analysis progress, results
```

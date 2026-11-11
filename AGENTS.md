# Stride Lab: notes for coding agents

Stride Lab is an Android app that films a runner side-on on a treadmill, detects their pose with MediaPipe,
measures their running form and gives coaching suggestions. Read [README.md](README.md) for what the app does.
This file covers how to work on it.

## Rules that never bend

1. **Everything stays on the phone.** No internet permission, no cloud service, no LLM or other API calls,
   no analytics and no crash upload. The manifest strips `INTERNET` and `ACCESS_NETWORK_STATE` with
   `tools:node="remove"`, so leave those lines in place. Sharing works only through Android's share sheet, where
   the person picks the app the result goes to.
2. **Keep licences and privacy current.** When a dependency, model or bundled asset is added or removed, update
   [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `app/src/main/res/raw/open_source_licences.txt` (shown
   under About). To find new transitive libraries, run
   `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
   When the app's data, permissions or sharing change, update [PRIVACY.md](PRIVACY.md) and its effective date.
3. **Never uninstall the app from the user's phone.** Uninstalling deletes their recorded sessions. Use
   `adb install -r`. If an install fails with a signature mismatch, stop and ask the user.
4. **No secrets or client data.** This repo has neither. Never read, print or commit `.env` files or
   credentials, or `local.properties` beyond `sdk.dir`.
5. **Coaching is not medical advice.** Any user-facing text that judges form must keep that framing.
6. **Always follow Conventional Commits.** Every commit message must strictly follow Conventional Commits
   (e.g. `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`) in lowercase imperative format
   (e.g. `feat: add ...`, `fix: resolve ...`). Commit and push only when instructed by the user and once verification passes.

## Build, test and verify

Gradle runs on JDK 21 automatically (`gradle/gradle-daemon-jvm.properties`), even though `JAVA_HOME` on this
machine points at JDK 17. App code targets Java 17. compileSdk and targetSdk are 36, minSdk is 28.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # run before every commit
./gradlew assembleRelease                             # also run when touching proguard, manifest or dependencies
```

- The first build downloads the pose model into `app/src/main/assets/` and checks its SHA-256. The model is
  git-ignored. Never change `poseModelSha256` to make a build pass.
- **Lint baseline: 0 errors, 26 warnings, 2 hints (28 entries).** Nearly all are "newer version available"
  notices. A change must not add any; read `app/build/reports/lint-results-debug.txt` to check.
- Unit tests are in `app/src/test/`. The analysis code is pure Kotlin and is tested against a synthetic
  runner. Add or update tests whenever logic in `analysis/`, `pose/`, `sensors/` or `share/` changes.
- CI (`.github/workflows/build.yml`) runs `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest`
  on every push (the instrumented tests are only compiled there).
- **Instrumented tests** are in `app/src/androidTest/`. Run them with
  `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`. Always set `ANDROID_SERIAL`: Gradle otherwise
  runs them on every attached device, including the user's Pixel, which holds their real clips. The tests are written
  not to touch real data (old-dated fixtures, the crash log is restored), but still never run them on the Pixel
  without being asked. Fixtures live in `Fixtures.kt`; add a test there when you add a screen or a share path.
- **README GIF** (`docs/demo.gif`): the real app screen-recorded on the **emulator only**, never from the user's
  footage (they don't want their clips public). `DemoSeeder` (androidTest) saves two synthetic clips; it skips
  itself unless run by hand: install both debug APKs on the emulator with `adb install -r`, then
  `am instrument -w -e demo true -e class com.atsukio.runningcoach.DemoSeeder com.atsukio.runningcoach.test/androidx.test.runner.AndroidJUnitRunner`.
  Record with `adb shell screenrecord`, scrolling with a slow `input swipe ... 1500` (a fast swipe flings to a
  different place each time), and convert with ffmpeg (crop the status and nav bars, 360 px wide, 12 fps,
  `palettegen`/`paletteuse`), keeping it under about 2 MB.
- Use the Gradle quiet flag `-q` to keep output short. Stop the daemon when you're done (`./gradlew --stop`).

## Devices

- adb isn't on PATH: use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`.
- The user's phone is a **Google Pixel 10 Pro**, usually connected over wireless debugging. Run
  `adb devices` to get its serial, then target it with `-s <serial>`, because an emulator may also be attached.
- Emulator AVD: `Medium_Phone_API_37.0` (`$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe -avd Medium_Phone_API_37.0`).
  Shut it down afterwards with `adb emu kill`.
- Real-device behaviour (camera, 60 fps, GPU delegate, heat, Bluetooth) can't be checked on the emulator. Say so
  rather than claiming it works.
- **Pose detection cannot run on the emulator at all**: the AVD is x86_64 and MediaPipe ships ARM libraries only.
  Analysis ends in the "processor can't run pose detection" error there. That is expected; it still exercises the
  runner, notifications and Home. Anything involving real poses needs the Pixel, and installing there needs the user's say-so.
- Android shell paths get mangled by Git Bash: run adb with `MSYS_NO_PATHCONV=1`. To skip the first-run guide on
  the emulator, write `guide_seen` into `shared_prefs/settings.xml` with `adb shell run-as com.atsukio.runningcoach`.
- A shell heredoc containing Kotlin or Python with quotes can fail to parse. Write the script with the Write tool
  and run it instead.

## Code map

```text
app/src/main/java/com/atsukio/runningcoach/
  analysis/  Pure Kotlin, no Android imports. PoseTrack, PoseCleaner (leg swaps, jumps, smoothing), LiveStrikeDetector
             (the landing pulse on the camera preview),
             GaitAnalyzer (landings, metrics), Coach (rules -> Feedback, mainFocus), Framing, RunInsights,
             LandingAnnotation
  pose/      MediaPipe extraction: PoseExtractor (decoder + two-pass), LivePoseAnalyzer (analyse while
             recording), LiveAlignment (matches live frames to the saved video), GpuCheck, FrameTiming, Yuv
  data/      Session (kotlinx.serialization), SessionRepository (app-private files), CoachSettings, RunRecorder,
             AnalysisRunner (analyses run app-wide, not per screen), CrashLog
  sensors/   Bluetooth heart-rate and running speed/cadence sensors (GATT parsers, SensorHub)
  audio/     Metronome, SpokenCues (offline text-to-speech) and CueSchedule
  share/     ShareSummary (text), ShareCard (image drawn off-screen), ResultSharer (FileProvider + share sheet)
  ui/        Compose Material 3 screens. CoachApp holds navigation; ResultVisuals holds the shared drawing helpers
```

## Gotchas already hit

- **Compose `drawText(measurer, text, topLeft)` throws** ("maxWidth must be >= minWidth") when `topLeft` is past
  the canvas edge. Use `drawLabel(measurer, text, topLeft, style)` from `ui/ResultVisuals.kt`, or measure first
  and call `drawText(layoutResult, topLeft)`.
- Pose timing: video can have a variable frame rate. Map frames by timestamp (`FrameTiming`), never by
  assuming a constant fps. `Session.fps` is the capture rate and `Session.playbackFps` is the file's rate.
  Landing frame indices in `GaitMetrics.contactFrames` are in playback frames.
- Never mix GPU and CPU detections within one clip. `GpuCheck` compares GPU with CPU once per phone and Android version (some drivers, including the Pixel 10 Pro's, gave 10% low cadence) and falls back to CPU.
- Left and right legs get swapped by the detector. `PoseCleaner` fixes this; check that UI work uses the cleaned
  track unless it is deliberately showing the Raw view.
- Speed and height are per clip. `Session.scaleEdited` marks a clip whose scale was changed on the results screen;
  `SessionRepository.recalculate` re-measures from the saved poses (no detection), and a full re-analysis keeps that
  clip's height. Build sessions only through `SessionBuilder.build` so both paths stay in step.
- GPU and CPU poses agree to about 0.3% of frame height, but landing figures (foot ahead of hips, shin, balance)
  moved by several cm between them on a 30 fps clip, because landings are whole frames and the belt moves the foot
  ~5.7 cm per frame. Checked on the Pixel with `GpuDiagnosticTest` (run by hand with `am instrument`, never with
  `connectedDebugAndroidTest`, which uninstalls the app). Landings are now measured at an interpolated moment
  (`GaitMetrics.contactTimes`), which cut the GPU/CPU gap on that clip from 2.6 cm to 0.6 cm (foot ahead of hips)
  and 4.4° to 0.5° (shin). Left/right balance (15 vs 27 cm) came from picking the trailing foot, still flat on
  the belt, as the landing foot on a third of landings ("lowest heel"); the landing foot is now the front one and
  balance compares medians (2.5 vs 2.1 cm after). The synthetic runner can't show either problem, so use a real clip.
- `Session.measureVersion` / `SessionBuilder.MEASURE_VERSION`: raise it whenever a change moves the figures, so
  Storage > "Older results" offers to re-measure old clips from their saved poses.
- MediaPipe given a model *path* re-copies it into `cache/mediapipe_asset_cache` on every detector created, and
  parallel detectors crashed the app with SIGBUS (seen on the Pixel 4, 5 and 7 Nov). `PoseModel` maps the model
  from the APK once and passes a buffer; never go back to `setModelAssetPath`.
- To find the Pixel run's crash, read `adb logcat -d -b crash`: native crashes skip the in-app crash log.
- The share FileProvider exposes only `cacheDir/shared/`, and that folder is cleared on each share. Keep it that way.
- The setup diagram must clearly show the camera **side-on** to the runner, never facing them.
- `MediaMetadataRetriever` frame grabs are slow. Do them off the main thread (`Dispatchers.IO`).
- Lint's `ObsoleteSdkInt` warning on `res/mipmap-anydpi-v26` is a false positive. The adaptive icon must stay in that
  folder: moving `ic_launcher.xml` to `mipmap-anydpi` fails the build with "resource mipmap/ic_launcher not found".
  Leave it, and don't count it as a new warning.

## Design system

The look is a dark-first "lab" style: near-black green background, mint accent, flat outlined panels, monospace
figures. It lives in `ui/Theme.kt` (colours, typography, shapes, `Appearance` setting) and `ui/LabComponents.kt`.

- Use `LabCard`, never Material `Card`/`ElevatedCard`. Use `SectionLabel` for the small capitals tags,
  `StatChip` for a figure with a unit, `StatusDot` and `signalColour(good)` for status (green in range, amber to
  work on, blue for a note), and `monoValue(size)` for any measurement or timer. Pick colours from
  `MaterialTheme.colorScheme`, not hex values, so the light theme keeps working.
- No dynamic (wallpaper) colour: the brand palette is fixed. The share card draws its own always-dark palette in
  `share/ShareCard.kt`; keep the two in step if the palette changes.
- Motion: `CountUpText` counts a leading figure up once per screen visit (monospace keeps it steady, and it
  follows the system's animation setting); `ScanPanel` is the analysing animation; `ViewfinderPanel` frames the main
  call to action. The camera screen is always dark whatever the theme, so it uses fixed dark colours.
- Check both themes on the emulator (Settings > Appearance) after UI changes. Screenshots: `adb exec-out screencap -p`.

## Style

- Kotlin, Compose Material 3, no new architecture libraries (no DI framework, no Room) without asking.
- User-facing text and docs use plain British/Australian spelling: licence, analyse, colour, centimetres.
  Keep sentences short and concrete, and avoid jargon in the UI.
- Comments explain why, not what. Keep KDoc on public and internal functions short.
- Commit messages must follow **Conventional Commits** (e.g. `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`)
  describing the change in an imperative sentence. Commit and push only when instructed by the user and once verification passes.

## How to work with this user

- **After each change, suggest a few ideas for what to do next.** Base them on what you just built and on the
  backlog below, give one line each, and say which you'd pick first.
- When the user approves a list ("action on all the things you think is good"), do them all, then verify once.
- When finished, report what you verified (tests, lint count, builds, device or emulator) and what you couldn't.
- Offer to install on the Pixel after a user-visible change, since the user tests on real treadmill runs.

### Idea backlog (not started)

Done so far: share image, storage controls, crash log, dark lab redesign, steady-part trimming, milestones, last-time
ghost, focus plan, spoken cues, background analysis, instrumented tests, video overlay MP4 export, PDF report, side-by-side clip comparison, foot strike ratio tracking and trends, audio cue controls, synchronized strike scrubbing, interactive foot strike target overlay, intuitive lab icons.

- Front and rear view analysis (knee collapse, hip drop, crossover). `GaitAnalyzer` rejects those views today; this
  needs different landmarks and metrics, a camera-placement guide, and real footage to check against before it can be trusted.
- Pose detection fallback for phones with no ARM libraries (today clips can't be analysed on them).
- Light-theme pass: screenshots of every screen in light mode.
- A Play Store release pass using the `android-play-release` skill (version bump, notes, screenshots).

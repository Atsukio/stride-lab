# Third-party notices

Stride Lab is released under the [MIT License](LICENSE). It includes the open-source software below, which keeps
its own licence. The app shows these notices, with the full licence texts, under **About → Open-source licences**
(source: [`app/src/main/res/raw/open_source_licences.txt`](app/src/main/res/raw/open_source_licences.txt)).

When you add or remove a dependency, update this file and that one. To list what ends up in the app, run
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.

| Component | Licence |
|---|---|
| AndroidX: Core, Activity, Lifecycle, Navigation, Compose, Material 3, CameraX, Media3 | Apache 2.0 |
| [MediaPipe](https://github.com/google-ai-edge/mediapipe) Tasks Vision and Tasks Core | Apache 2.0 |
| MediaPipe Pose Landmarker (full) model, BlazePose GHUM 3D, downloaded at build time ([model card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf)) | Apache 2.0 |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache 2.0 |
| Guava, failureaccess, listenablefuture, Flogger, AutoValue annotations, Error Prone annotations, J2ObjC annotations, JetBrains annotations, JSpecify, javax.inject | Apache 2.0 |
| Google Android Data Transport, Firebase Encoders (pulled in by MediaPipe) | Apache 2.0 |
| Protocol Buffers (protobuf-javalite) | BSD 3-Clause |
| JSR 305 annotations | BSD 3-Clause |
| Checker Framework qualifiers | MIT |

The pose model isn't stored in this repository. The build downloads it from Google and bundles it in the APK.

Build-only tools (Gradle, the Android Gradle Plugin, JUnit) aren't shipped in the app.

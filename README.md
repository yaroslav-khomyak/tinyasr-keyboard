# TinyASR Voice Keyboard

An Android voice-only keyboard (input method) that types what you say, offline, using
[tinyasr](https://github.com/dkaraush/tinyasr) and its `en-ru-uk` model. Ukrainian, English and Russian can be selected.

- Live text while you speak, with punctuation and capitalization from the model.
- Keys: previous keyboard (hold: keyboard picker), language (cycles through the languages checked in the app, plus AUTO), microphone, delete, enter,
  `,` `.` space `?` `!`.
- Nothing leaves the phone: the app has no internet permission.
- Android 9+ (API 28), arm64 only.

`AUTO` lets the recognizer pick among the checked languages per sentence; it costs that many times the CPU of a fixed language.

## Get the APK

Every push to `main` builds the app with GitHub Actions and publishes it under **Releases → Latest build**
(`tinyasr-keyboard.apk`). Install it, open the app and follow the three steps.

## Build it yourself

Needs the Android SDK (platform 35, NDK 27.0.12077973, CMake 3.22.1), JDK 17 and rustup.

```bash
scripts/prepare.sh          # fetches tinyasr at the pinned commit, builds libtinyasr.a, unpacks the models
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

To use another tinyasr version or model, change `TINYASR_REV` / `MODEL` in `scripts/prepare.sh`
(and `ASR_ASSET` in `VoiceEngine.java`, `LANGS` in `Prefs.java`).

## Tests

```bash
scripts/prepare.sh && scripts/host-bridge.sh   # the second builds the JNI bridge for this machine
./gradlew testReleaseUnitTest
```

- `TranscriptTest`, `PrefsTest`, `TextWriterTest`: the event-to-text logic, the language settings, and how text is
  written into a (fake) text field.
- `BridgeHostTest`: loads the real model through the JNI bridge and transcribes `hello_en.wav` (synthetic English
  speech) into the fake text field. Skipped locally if the host bridge is not built; required in CI.

Not covered: the keyboard view, the microphone and anything else that needs a device.

## How it works

- `app/src/main/cpp/tinyasr_jni.c`: JNI bridge to the tinyasr C API. Pins the recognizer thread to the big cores.
- `VoiceEngine`: one recognizer thread; reads the microphone at 16 kHz and feeds a tinyasr streaming session.
- `Transcript`: turns the session's JSON events into committed and partial text.
- `TextWriter`: writes into the editor. Partial text is composing text, replaced as the recognizer refines it.
- `VoiceIme`: the keyboard view, keys and lifecycle.
- `SetupActivity`: enable the keyboard, grant the microphone, select it.

## Signing

`signing/sideload.jks` is a throwaway key kept in the repository so that each build installs over the previous
one. It is public: use your own key before distributing the app to anyone else.

## License

GPL-3.0-or-later, the same as tinyasr and its models, which this app links and ships. Voice activity detection
uses Silero VAD (MIT).

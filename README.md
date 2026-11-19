# TinyASR Voice Keyboard

[![Build APK](https://github.com/yaroslav-khomyak/tinyasr-keyboard/actions/workflows/build.yml/badge.svg)](https://github.com/yaroslav-khomyak/tinyasr-keyboard/actions/workflows/build.yml)

An offline voice keyboard for Android. Tap the microphone, speak, and the text appears in whatever app you are
typing in. Speech is recognized on the phone by [tinyasr](https://github.com/dkaraush/tinyasr) with its `en-ru-uk`
model: Ukrainian, English and Russian.

**[Download the latest APK](https://github.com/yaroslav-khomyak/tinyasr-keyboard/releases/tag/latest)**
(42 MB, Android 9+, arm64)

> Status: early. The recognition path is covered by automated tests, but the keyboard has had little testing on
> real devices. Reports are welcome.

## Features

- **Offline and private.** The app has no internet permission; audio never leaves the phone.
- **Live text.** Words appear while you speak and are refined as the sentence goes on, with punctuation and
  capitalization from the model.
- **Language key.** Cycles through the languages you checked in the app's settings, plus an optional `AUTO` mode
  that picks among them per sentence.
- **Just enough keys.** Previous keyboard, language, microphone, delete, enter, and `,` `.` space `?` `!`. It is
  meant to be used next to your normal keyboard, not to replace it.
- **Light and dark** themes follow the system. The interface is in English and Ukrainian.

## Install

1. Download `tinyasr-keyboard.apk` from the link above and install it (allow installing from unknown sources).
2. Open **TinyASR Voice Keyboard** and follow the three steps: enable the keyboard, allow the microphone, switch
   to it.
3. In any text field, tap the microphone and speak. Tap again to stop.

## Using it

| Key | Tap | Hold |
|---|---|---|
| ⌨ | back to the previous keyboard | keyboard picker |
| `UK` / `EN` / `RU` / `AUTO` | next language | |
| Microphone | start / stop listening | |
| ⌫ | delete a character (or the selection) | repeat |
| ⏎ | the field's action (send, search, …) or a new line | |

- Listening stops by itself after 30 s without speech, and when the keyboard closes.
- Punctuation and space keys can be used while dictating; dictation continues after them.
- Enter stops the dictation, and so does delete while unfinished words are on screen, so that you can send or
  correct.

## Settings

Open the app to change them.

- **Languages on the language key**: check any of Ukrainian, English, Russian. At least one stays on.
- **AUTO**: adds a mode in which the recognizer detects the language among the checked ones. Each sentence is then
  decoded once per language, so it costs that many times the CPU of a fixed language. If you mostly speak one
  language, a fixed one is faster and more accurate.
- **Start listening as soon as the keyboard opens.**

## Accuracy

Word error rates of the `en-ru-uk` model from the tinyasr README (final text / live text):

| Language | FLEURS | Common Voice |
|---|---|---|
| Ukrainian | 6.13 / 8.22 | 7.78 / 10.29 |
| English | 7.62 / 10.46 | 13.53 / 17.54 |
| Russian | 5.47 / 7.22 | 4.89 / 7.04 |

## Build it yourself

Needs the Android SDK (platform 35, NDK 27.0.12077973, CMake 3.22.1), JDK 17 and rustup.

```bash
scripts/prepare.sh          # fetches tinyasr at the pinned commit, builds libtinyasr.a, unpacks the models
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Every push to `main` does the same in GitHub Actions, runs the tests, and replaces the APK of the
[`latest` release](https://github.com/yaroslav-khomyak/tinyasr-keyboard/releases/tag/latest).

To use another tinyasr version or model, change `TINYASR_REV` / `MODEL` in `scripts/prepare.sh`, `ASR_ASSET` in
`VoiceEngine.java` and `LANGS` in `Prefs.java`.

## Tests

```bash
scripts/prepare.sh && scripts/host-bridge.sh   # the second builds the JNI bridge for this machine
./gradlew testReleaseUnitTest
```

- `TranscriptTest`, `PrefsTest`, `TextWriterTest`: the event-to-text logic, the language settings, and how text is
  written into a (fake) text field.
- `BridgeHostTest`: loads the real model through the JNI bridge and transcribes `hello_en.wav` (synthetic English
  speech) into the fake text field. Skipped locally if the host bridge is not built; required in CI.

Not covered: the keyboard view, the microphone, the settings screen, and anything else that needs a device.

## How it works

```
microphone (16 kHz) -> VoiceEngine -> tinyasr session (JNI) -> JSON events -> Transcript -> TextWriter -> editor
```

- `app/src/main/cpp/tinyasr_jni.c`: JNI bridge to the tinyasr C API. Pins the recognizer thread to the big cores.
- `VoiceEngine`: one recognizer thread; reads the microphone and feeds a tinyasr streaming session.
- `Transcript`: turns the session's JSON events into committed and partial text.
- `TextWriter`: writes into the editor. Partial text is composing text, replaced as the recognizer refines it.
- `VoiceIme`: the keyboard view, keys and lifecycle.
- `SetupActivity`: the setup steps and the settings.
- `Prefs`: the stored settings and the modes of the language key.

The models (38 MB + 2 MB) are stored uncompressed in the APK and mapped from it at load time.

## Signing

`signing/sideload.jks` is a throwaway key kept in the repository so that each build installs over the previous
one. It is public, so anyone can sign an APK with it: install only from this repository's releases, and use your
own key if you distribute a fork.

## License

GPL-3.0-or-later, the same as tinyasr and its models, which this app links and ships. The models were trained
mainly on YODAS v3 (CC BY 3.0). Voice activity detection uses Silero VAD (MIT).

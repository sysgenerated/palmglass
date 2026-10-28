# PalmGlass

Turn a Pixel into a trackpad, keyboard and launcher for Xreal glasses.

The glasses become the monitor through Android's native DisplayPort output and desktop
windowing. PalmGlass fills the gaps Android leaves open: a pointer driven from the phone
screen, typing from the phone, opening apps straight onto the glasses, and keeping the
phone panel black and as dim as possible while it stays awake.

No root. No screen reading. The accessibility service is used only to draw the cursor and
inject touches on the external display.

## Requirements

- Pixel 8 or newer on Android 16 QPR3 or later. Developed and tested on GrapheneOS; stock Pixel
  firmware has the same display output and desktop windowing features.
- Xreal glasses on the USB-C cable. Any DisplayPort Alt Mode display works the same way.
- A Bluetooth gamepad is optional. Its input stays with the window on the glasses while the
  touchpad is open.

## Install

1. Install [Obtainium](https://github.com/ImranR98/Obtainium) and allow it to install apps.
2. Add `https://github.com/sysgenerated/palmglass` as an app source.
3. Open PalmGlass and complete the three setup steps: enable the accessibility service,
   enable the PalmGlass keyboard, allow notifications.

## Use

1. Plug the glasses in. A silent **Glasses connected** notification appears; tap it to open
   the touchpad. The **Open touchpad** button on the setup screen does the same. If you
   prefer, turn on **Open touchpad when glasses connect** in settings and skip this step.
2. The phone goes black and a cursor appears on the glasses.
3. To type, pick the PalmGlass keyboard once with **Choose keyboard** (on the setup screen or
   in the launcher panel). After that the keyboard panel appears on the phone whenever a
   text field on the glasses takes focus. By default PalmGlass hands the keyboard back to
   your usual one when the touchpad is closed.

## Gestures

| On the phone                       | On the glasses                              |
|------------------------------------|---------------------------------------------|
| One finger move                    | Move cursor                                 |
| Tap                                | Click                                       |
| Tap, then press and move           | Drag                                        |
| Two-finger move                    | Scroll                                      |
| Two-finger tap                     | Long press / context menu                   |
| Three-finger tap                   | Lock or unlock input                        |
| Three-finger swipe down            | Exit touchpad                               |
| Three-finger swipe up              | App launcher                                |
| Three-finger swipe left or right   | Show or hide keyboard panel                 |

- **Lock.** Two short buzzes mean input is locked, one means unlocked. While locked the
  cursor is hidden and every touch except the three-finger tap is ignored, so the phone can
  go in a pocket during a movie. There is no on-screen reminder; if the touchpad seems
  unresponsive, three-finger tap.
- **Cursor.** Hides after three seconds without input and reappears on the next touch.
- **Panels.** While the launcher or keyboard is showing, the phone screen is at normal
  brightness. A three-finger swipe on a panel closes it; sideways on the launcher swaps it
  for the keyboard. The launcher also has a Close bar, and the keyboard a hide key.
- **Stopping.** Three-finger swipe down, the Exit button in the launcher, or Stop on the
  session notification. Unplugging the glasses also ends the session.

## Settings

- **Open touchpad when glasses connect.** Off by default.
- **Dim phone screen while touchpad is open.** On by default. Panels are always readable.
- **Natural scrolling.** On by default.
- **Hand keyboard back when touchpad is closed.** On by default.
- **Cursor speed.**

## Known limits

- The phone screen must stay on. Android ends the desktop session when the phone sleeps.
  PalmGlass keeps the panel black at minimum brightness instead.
- Pointer actions are injected as touches, so there is no hover. Drags lag slightly behind
  the finger because they are sent in short chunks.
- The desktop taskbar on the glasses belongs to Android. It hides only when the focused
  window is fullscreen and the app is immersive, and it occasionally misbehaves on its own.
- The glasses draw power from the phone. Use a hub with power-delivery passthrough for
  long sessions.

## Build

```sh
./gradlew assembleDebug           # debug APK, installable with adb
./gradlew assembleRelease         # release APK, signed if keystore.properties exists
```

Create a release key once and keep it safe; updates must be signed with the same key:

```sh
keytool -genkeypair -v -keystore palmglass-release.jks -alias palmglass \
  -keyalg RSA -keysize 4096 -validity 10000
```

Local signing reads `keystore.properties` in the project root (git-ignored):

```
storeFile=/absolute/path/palmglass-release.jks
storePassword=...
keyAlias=palmglass
keyPassword=...
```

CI signing on a `v*` tag reads these repository secrets: `KEYSTORE_BASE64`
(`base64 -w0 palmglass-release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
The release workflow attaches `PalmGlass-<tag>.apk` to a GitHub release, which Obtainium
picks up.

## License

MIT

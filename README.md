# Mapper

Play touch-only Android games with a physical keyboard. Mapper turns key presses into screen
touches through an accessibility service, so it needs no root.

## Features

- The overlay opens by itself when a game comes to the front. Games are detected by the Play
  Store game category or by a game engine library inside the APK (Unity, Unreal, SDL, Godot,
  Cocos, libGDX, FMOD, HashLink). Any other app can be mapped by hand and is remembered.
- Buttons: one key holds one finger down for as long as the key is held.
- Joysticks: four keys move one finger around a center, with diagonals.
- Several keys can be held at once, for example moving while attacking.
- One layout per game.
- Idle overlay lets touches through and shows markers at 50%. Edit mode is 80% over a light
  tint so the game stays visible. Markers can be hidden while the keys keep working.
- Unmapped keys go to the game as normal.
- Battery: no polling, no timers, no animations. Work happens only on app switches and mapped
  key presses.

## Shortcuts

| Keys | Action |
| --- | --- |
| Ctrl+Alt+K | Edit the mapping for the current app |
| Ctrl+Alt+H | Hide or show markers and the floating bubble |
| Ctrl+Alt+P | Pause mapping, for typing in game chat |

The floating keyboard bubble also opens edit mode. Drag it to move it.

## Editing

| Input | Action |
| --- | --- |
| Tap empty space | Add a button, then press its key |
| Hold empty space | Add a joystick, then press up, left, down, right |
| Drag a control | Move it |
| Tap a control | Select it and rebind its keys |
| Del | Remove the selected control |
| `[` and `]` | Make the selected control smaller or bigger |
| Esc | Deselect, or save and exit |

The bottom bar has Cancel, Delete, Smaller, Bigger and Save for touch use.
Ctrl, Alt and Meta cannot be bound because they start the shortcuts.

## Build

Builds on Termux with no Gradle.

```sh
pkg install openjdk-21 aapt aapt2 d8 apksigner zipalign android-tools
scripts/build.sh            # build/mapper.apk
scripts/build.sh --install  # also installs over adb (uses the adbon helper)
```

Then turn on Mapper in Settings > Accessibility.

## Test

```sh
javac -d build/test src/app/mapper/Btn.java tmp/BtnTest.java && java -ea -cp build/test app.mapper.BtnTest
```

## Layout

- `src/app/mapper/MapperService.java` accessibility service: game detection, key filter,
  touch injection, overlay editor, floating bubble
- `src/app/mapper/Btn.java` one control, its saved format and joystick math
- `src/app/mapper/MainActivity.java` settings screen
- `res/` accessibility config and the adaptive icon

## Limits

- A real touch on the screen while a key is held can drop the injected finger. Mapper puts
  held fingers back once.
- At most 10 fingers at a time, the Android gesture limit.

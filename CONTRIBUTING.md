# Contributing

Thanks for helping with KGameBoy. This is a learning project, so clear code and small, well-explained changes are
preferred over clever ones. Read the [README](README.md) first: it explains the architecture and where each piece
of hardware lives.

## Setup

- **JDK 17 or newer.** The Gradle wrapper downloads everything else (use `./gradlew`, never a system Gradle).
- **Android SDK**, only if you build `:android`: create a git-ignored `local.properties` with `sdk.dir=/path/to/sdk`.
- Any IDE with Kotlin support; IntelliJ IDEA or Android Studio work out of the box.

## Build and run

```sh
./gradlew :desktop:run -Prom=roms/dmg-acid2.gb          # desktop app with a ROM
./gradlew :desktop:run                                  # desktop app, file dialog
./gradlew :desktop:run -Prom=roms/dmg-acid2.gb -Pframes=120 -Pscreenshot=docs/img/out.png   # headless screenshot
./gradlew :web:wasmJsBrowserDevelopmentRun              # browser dev server
./gradlew :web:wasmJsBrowserDistribution                # static web bundle
./gradlew :android:assembleDebug                        # debug APK
```

## Tests

```sh
./gradlew :core:allTests    # everything: JVM, plus the common tests on Kotlin/Wasm (Node.js)
./gradlew :core:jvmTest     # JVM only, includes the ROM integration tests
```

- Unit tests live in `core/src/commonTest` and must be platform independent, so they also run on Wasm.
- ROM integration tests (`RomIntegrationTest`) live in `core/src/jvmTest`. They run Blargg's `cpu_instrs` and
  `instr_timing` headless and read the verdict from the serial port. They are **skipped** if the ROMs are not on
  disk, so download them first (command in [`roms/README.md`](roms/README.md)) and confirm they actually ran.
- `dmg-acid2` and `cgb-acid2` are checked visually with the headless screenshot command above: a correct emulator
  draws a smiling face.
- A bug fix should come with a test that fails before the fix. Hardware behavior changes should say which
  Pan Docs section or test ROM they follow.

Run `./gradlew :core:allTests` before opening a pull request.

## Code style

- Kotlin [official code style](https://kotlinlang.org/docs/coding-conventions.html) (`kotlin.code.style=official` in
  `gradle.properties`): 4-space indent, `UpperCamelCase` types, `lowerCamelCase` members.
- Keep `:core` pure Kotlin in `commonMain`: no platform APIs. Reach the outside world through the ports.
- One hardware block per package, mirroring the real machine. Prefer extending the existing classes (for example the
  `cgb` flag) over duplicating them.
- Comment the *why* (hardware quirks, timing), not the *what*.

## ROMs and licenses

See [`roms/README.md`](roms/README.md).

- Only ROMs with a clear redistribution license may be committed (today: `dmg-acid2`, `cgb-acid2`, both MIT). Add
  the source and license to the table in `roms/README.md` in the same PR.
- Blargg's test ROMs have no clear license: keep them in `roms/third-party/` (git-ignored), never commit them.
- Never commit commercial games or BIOS/boot ROMs, and do not link to places that distribute them.

## Pull requests

1. Fork and branch from `main` (`fix-mbc3-bank-wrap`, `add-mbc2`).
2. Keep the PR focused on one change; unrelated cleanups go in their own PR.
3. Make sure `./gradlew :core:allTests` passes and the affected frontend still builds.
4. Update the README (tests table, limitations, layout) when behavior or structure changes.
5. Describe what changed and why, and how you verified it (tests, test ROM, screenshot).
6. Version bumps and tags are done by maintainers following [`VERSIONING.md`](VERSIONING.md); do not change
   version numbers in a normal PR.

## Commit messages

- A short imperative subject, about 72 characters, no trailing period: `Fix MBC1 bank 0 promotion`,
  `Add tests for Game Boy Color mode`.
- Add a body, wrapped at 72 columns, when the reason is not obvious: what was wrong, and which hardware behavior
  or document you followed.
- One logical change per commit; the build and tests should pass at each commit.
- Reference issues or PRs with `(#123)` or `Fixes #123`.

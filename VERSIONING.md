# Versioning

KGameBoy follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html): `MAJOR.MINOR.PATCH`,
with an optional pre-release suffix (`1.2.0-rc.1`).

An emulator has no network API, so the "public API" is defined by what users and embedders can depend on:

1. The **behavior of the emulated machine**: which ROMs run, and how (see [Known limitations](README.md#known-limitations)).
2. The **ports in `:core`** (`VideoSink`, `AudioSink`, `RomSource`, `SaveStore`) and the `GameBoy` class that frontends call.
3. The **on-disk formats** users keep: battery saves (`.sav` files).
4. The **frontends' user-facing controls** (key bindings, menu items).

## What bumps which number

| Bump | When | Examples |
|------|------|----------|
| **MAJOR** | A change that can break existing users or embedders | Removing or changing a port interface or a public `GameBoy` member; changing the `.sav` format so old saves no longer load; dropping a supported platform or raising the minimum JDK / Android API level; removing a supported cartridge type |
| **MINOR** | Backwards-compatible new capability | A new MBC (e.g. MBC2), save states, a new frontend, a new menu item or setting, a new optional port method with a default; a fix that makes previously unsupported ROMs run |
| **PATCH** | Backwards-compatible bug fix, no new capability | Correcting a CPU flag, PPU timing or APU glitch so the emulator matches hardware; crash fixes; performance work; dependency updates; docs and tests |

Judgement calls specific to emulation:

- Making the emulator **more accurate** is a PATCH (or MINOR if it unlocks whole new classes of ROMs), even though
  the output changes: the old output was a bug. A game that only worked because of an inaccuracy is not a
  compatibility promise.
- Internal refactors of `Cpu`, `Ppu`, `Apu`, `Mmu` and other classes that frontends do not touch are never MAJOR.
- While the version is `0.y.z`, anything may change at any time; we still bump MINOR for features and PATCH for fixes.

## Tags

Every release is an annotated git tag `vMAJOR.MINOR.PATCH` on `main` (for example `v1.4.2`). Pre-releases use the
SemVer suffix: `v1.5.0-alpha.1`, `v1.5.0-beta.2`, `v1.5.0-rc.1`. Tags are immutable: never move or reuse one;
publish a new PATCH instead.

## Where the version lives

The tag is the source of truth, and the version lives in one place: `kgameboy.version` in `gradle.properties`
(without the `v`). Everything else derives from it:

| Consumer | Derived value |
|----------|---------------|
| `cli` | `version`, printed by `kgameboy --version` |
| `android` | `versionName` is the full SemVer string; `versionCode` is `MAJOR * 10000 + MINOR * 100 + PATCH` (`1.4.2` -> `10402`). Pre-releases of the same `X.Y.Z` share a code, so bump the patch number if one must be installed over another |
| `desktop` | `packageVersion` is `MAJOR.MINOR.PATCH` without suffix; macOS `.dmg` requires `MAJOR >= 1`, so `0.y.z` ships as `1.y.z` |

`:core` and `:web` do not carry a version of their own.

## Release flow

A release is triggered by pushing a tag; one workflow runs per tag. There is no release workflow in the repository
yet (only `.github/dependabot.yml`), so this section describes the intended flow and the contract that workflow
should follow.

1. Make sure `main` is green: `./gradlew :core:allTests`.
2. Bump `kgameboy.version` in `gradle.properties` in a commit named `Release vX.Y.Z`, through a normal pull request.
3. After the PR is merged, tag the merge commit and push the tag:

   ```sh
   git tag -a vX.Y.Z -m "KGameBoy X.Y.Z"
   git push origin vX.Y.Z
   ```

4. The workflow triggers on `push: tags: ['v*']`. It should:
   - check that the tag matches `kgameboy.version`, and fail otherwise;
   - run `./gradlew :core:allTests`;
   - build the artifacts (`:desktop:packageDistributionForCurrentOS`, `:android:assembleRelease`,
     `:web:wasmJsBrowserDistribution`);
   - create a GitHub Release with the artifacts and generated notes, marked as *pre-release* when the tag contains a
     `-` suffix.
5. Never create the GitHub Release by hand for a tag that the workflow handles.

## Pre-releases

Use a suffix when a build needs testing before it is declared stable:

| Suffix | Meaning |
|--------|---------|
| `-alpha.N` | Incomplete; features and behavior may still change |
| `-beta.N` | Feature complete; testing and fixes only |
| `-rc.N` | Release candidate: ships as-is unless a blocker is found |

Pre-releases sort below the final version (`1.5.0-rc.1 < 1.5.0`), are marked as pre-releases on GitHub and are not
announced as the latest version. The final `vX.Y.Z` is a new tag on the commit that passed the candidates.

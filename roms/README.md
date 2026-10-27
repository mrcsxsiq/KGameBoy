# ROMs

## Included

| File | Source | License |
|------|--------|---------|
| `dmg-acid2.gb` | [mattcurrie/dmg-acid2](https://github.com/mattcurrie/dmg-acid2) | MIT |

`dmg-acid2` is a PPU rendering test: a correct emulator draws a smiling face.

## Not included (download locally)

Blargg's hardware test ROMs have no clear redistribution license, so they are not committed.
Download them into `roms/third-party/` (git-ignored) to enable the ROM integration tests:

```sh
mkdir -p roms/third-party && cd roms/third-party
for f in cpu_instrs/cpu_instrs.gb instr_timing/instr_timing.gb; do
  curl -sLO "https://github.com/retrio/gb-test-roms/raw/master/$f"
done
```

`./gradlew :core:jvmTest` then runs them headless; without the files those tests are skipped.

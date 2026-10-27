# ROMs

## Included

| File | Source | License |
|------|--------|---------|
| `dmg-acid2.gb` | [mattcurrie/dmg-acid2](https://github.com/mattcurrie/dmg-acid2) | MIT |
| `cgb-acid2.gbc` | [mattcurrie/cgb-acid2](https://github.com/mattcurrie/cgb-acid2) v1.1 | MIT |

`dmg-acid2` and `cgb-acid2` are PPU rendering tests: a correct emulator draws a smiling face, in green shades for DMG and in colour for CGB.

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

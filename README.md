# Chronicle — Native Android Life Simulator

An offline-first, deep text life simulator built natively for Android with **Kotlin + Jetpack Compose (Material 3)**, featuring a paper-and-ink journal & dark mode, seeded determinism, adaptive pacing, and multi-system life choices from infancy through old age.

**Current Build:** `v0.2.7-qa` (`com.chronicle.lifesim.qa`, `minSdk = 26`, `targetSdk = 36`)

---

## Features

- **18 Birth Countries & Adaptive Turn Pacing:** Local currency, tax brackets, cost-of-living multipliers, legal adult/marriage/retirement ages, and switchable Monthly (`+1m`), Bi-monthly (`+2m`), Quarterly (`+3m`), or Yearly (`+1y`) pacing.
- **After School, Projects & Portfolio:** Age-aware **Activities** sheet with after-school clubs (`Robotics Club`, `Theatre Troupe`, `Youth Soccer`), practice hours, term-end showcases, side projects, and a career portfolio that boosts interview & promotion odds.
- **Career Tracks & Structured Interviews:** 8 part-time jobs (starting at age 14) and 7 multi-tier full-time tracks with education prerequisites, tenure rules, and 3-answer job interviews.
- **People, Dating & Household Continuity:** Intentional friend-making, multi-turn adult dating with match preferences and shared outings, family planning, gifts, favors, boundary setting, and persistent relationship memories.
- **Assets, Credit, Loans & Hardship Recovery:** Investments, housing rent/mortgage, net-worth history chart, marketplace gear, 4 lenders with distinct rates/terms, and financial hardship recovery routes (shared rental, aid, overdraft settlement).
- **168-Hour Weekly Schedule & Wellness:** Interactive hour sliders with commit-on-release responsiveness, preset trade-off previews, focus skill training, medical conditions, doctor/therapy visits, and habit management.
- **Atomic Saves & Director Mode:** 5 local save slots with `.tmp` + `.bak` recovery, off-UI-thread saves, and a sandbox **Director Mode** (unless locked by Ironman).

---

## Project Layout

```text
app/src/main/kotlin/com/chronicle/lifesim/
├── MainActivity.kt              # Compose entry point & modal orchestration
├── engine/                      # Pure Kotlin simulation core, EraLoader, SaveManager
└── ui/                          # ChronicleViewModel, LifeAndShell, PillarTabs, SetupScreen,
                                 # ActivitiesSheet, DatingDialog, ScheduleEditor, Modals, theme
app/src/main/assets/eras/modern_2020s/   # JSON Era Pack (countries, careers, events, assets, names)
app/src/main/res/drawable-nodpi/         # Journal chapter illustrations
app/src/test/kotlin/com/chronicle/lifesim/engine/  # JVM engine & feature unit tests
GAME_PLAN.md                             # Design notes & release history
```

---

## Build & Test

Requires Android SDK (`compileSdk = 36`) and JDK 17.

```bash
# Run JVM engine unit tests (19 tests)
./gradlew testDebugUnitTest --no-daemon --console=plain

# Build debug APK
./gradlew assembleDebug --no-daemon --console=plain

# Build minified playtest APK
./gradlew assemblePlaytest --no-daemon --console=plain

# Install debug APK to connected device via adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

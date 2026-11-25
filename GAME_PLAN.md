# Chronicle — Master Game Design & Architecture Plan

> **Purpose:** Single source of truth for all humans and AI coding agents (Antigravity, Claude, Codex, etc.) contributing to this project. Read this file before making architectural, data-model, or UI decisions.

---

## 1. Vision & Core Philosophy

**Working Title:** *Chronicle* (placeholder — renameable anytime)  
**Genre:** Deep, fine-tuned personal Life Simulator (inspired by *BitLife*'s fun and immediacy, elevated with real systemic simulation, meaningful trade-offs, and deep customization).  
**Audience:** Personal flagship Android game — zero ads, zero paywalls, zero energy-wait timers, 100% offline-first.

### Non-Negotiable Principles
1. **Real Simulation Over Shallow RNG:** Outcomes stem from interconnected systems (sleep debt hurts cognition and mood; burnout strains marriages; economic cycles hit startups and stocks; parenting shapes kids' psychology).
2. **Engine / Data / UI Strict Separation:** The simulation engine is pure, deterministic (seeded RNG), and UI-agnostic. All era-specific content lives in structured JSON **Era Packs**. This guarantees a clean 1:1 port from our Phase 1 Web prototype to Phase 2 Native Android (Kotlin + Jetpack Compose).
3. **100% Offline by Default:** Zero mandatory network calls or API costs. Optional BYO AI narration is strictly a pluggable presentation-layer enhancer.
4. **Fine-Tuning First:** The player has deep control before starting a life (DNA, country, family wealth, chaos/difficulty) and an optional live **Director Mode** drawer mid-run (with an Ironman toggle for locked legit runs).
5. **Crafted Aesthetic ("Sleek Dark + BitLife Fun, Zero AI-Slop"):** Tactile dark surfaces, crisp typography, expressive personality, and snappy feedback — never generic purple-neon glassmorphism.

---

## 2. Two-Phase Platform Strategy

### Phase 1 (v0.1 – v0.3): Mobile-First Web Prototype
- **Stack:** React + TypeScript (strict) + Vite + Tailwind CSS (or CSS Modules with design tokens) + local persistence (`localStorage` / `IndexedDB` save slots).
- **Goal:** Rapidly iterate, balance simulation math, playtest full lives in desktop browser (9:16 mobile frame) and directly on Android phone over local Wi-Fi.
- **Portability Rule:** Everything inside `src/engine/` and `data/eras/` must have **zero React/DOM/browser dependencies** so it can be unit-tested in isolation and ported directly to Kotlin.

### Phase 2 (v1.0+): Native Android App
- **Stack:** Kotlin + Jetpack Compose + Room (SQLite) + Coroutines/Flow.
- **Goal:** Compile to a fast, native, offline installable Android APK.
- **Reuse:** Imports the exact same `data/eras/*.json` Era Packs and mirrors the pure simulation formulas and state schemas verified in Phase 1.

### Target Repository Structure (Phase 1)
```text
/
├── GAME_PLAN.md                 # This master plan (single source of truth)
├── data/
│   └── eras/
│       └── modern_2020s/        # Self-contained Modern Day Era Pack
│           ├── era.json         # Era metadata, timeline, macro rules
│           ├── countries.json   # Countries, currencies, taxes, healthcare, COL
│           ├── education.json   # School tracks, degrees, tuition, durations
│           ├── careers.json     # Job ladders, requirements, salaries, stress
│           ├── assets.json      # Stocks, funds, real estate, vehicles
│           ├── conditions.json  # Physical/mental conditions, treatments, mortality
│           ├── events.json      # Handcrafted & systemic event definitions
│           └── names.json       # Regional name pools
├── src/
│   ├── engine/                  # PURE TypeScript — ZERO UI/React imports
│   │   ├── types.ts             # Canonical state, NPC, DNA, Finance, Event types
│   │   ├── rng.ts               # Seeded PRNG (mulberry32 / xoshiro128**)
│   │   ├── time.ts              # monthsPerTurn delta scaling & life stages
│   │   ├── schedule.ts          # 168-hour weekly allocation math & trade-offs
│   │   ├── health.ts            # Genetics, physical/mental health, burnout, mortality
│   │   ├── career.ts            # Education, jobs, promotions, startups, finance
│   │   ├── relationships.ts     # NPCs, personalities, memory logs, autonomous ticks
│   │   ├── events.ts            # Trigger evaluator, story arcs, choice resolver
│   │   ├── director.ts          # Live Director Mode mutations & validation
│   │   ├── turn.ts              # Master pure function: advanceTurn(state, inputs)
│   │   └── __tests__/           # Vitest unit & multi-life soak tests
│   ├── narrative/               # Presentation-layer text formatting
│   │   ├── NarrativeEnhancer.ts # Interface: offline default + optional BYO AI hook
│   │   └── OfflineFormatter.ts  # Rich template interpolation with NPC/memory context
│   ├── store/                   # App state wrapper & multi-slot save/load
│   └── ui/                      # Mobile-first React UI ("Sleek Dark + BitLife Fun")
│       ├── theme/               # Design tokens (colors, typography, spacing)
│       ├── components/          # Reusable tactile cards, stat bars, modals, avatars
│       └── screens/             # Setup, Life Feed, Schedule, Career, People, Assets, Director, Death Summary
```

---

## 3. Time & Turn Architecture (`monthsPerTurn` Delta Math)

- **Default Granularity:** **Bi-monthly (6 turns/year, `monthsPerTurn = 2`)**.
- **Configurable Live Setting:** Player can switch granularity per save or mid-run:
  - `YEARLY` (`monthsPerTurn = 12`, 1 turn/year)
  - `QUARTERLY` (`monthsPerTurn = 3`, 4 turns/year)
  - `BIMONTHLY` (`monthsPerTurn = 2`, 6 turns/year) — *default*
  - `MONTHLY` (`monthsPerTurn = 1`, 12 turns/year)

### Delta Scaling Rules (`dt = monthsPerTurn / 12`)
All simulation rates are defined on an **annual basis** in Era Packs and scaled cleanly by `dt`:
- **Age:** Stored in integer `ageMonths` (`displayAge = Math.floor(ageMonths / 12)`). Each turn adds `monthsPerTurn`.
- **Cashflow:** `turnIncome = annualSalary * dt`, `turnExpenses = annualLivingCost * dt`.
- **Compound Growth (Investments/Debt):** `factor = Math.pow(1 + annualRate, dt)`.
- **Stat Drift & Skill XP:** `delta = annualRate * dt`.
- **Event & Mortality Probabilities:** Converted via Poisson survival scaling so changing turn speed never inflates or deflates annual odds:
  $$p_{\text{turn}} = 1 - (1 - p_{\text{annual}})^{dt}$$
- **Action Points (AP) per Turn:** Scales with `monthsPerTurn` (e.g., Monthly: 3 AP, Bi-monthly: 5 AP, Quarterly: 7 AP, Yearly: 12 AP), modified by Energy/Burnout.

### Life Stages
| Stage | Age (Years) | Unlocked Mechanics |
|---|---|---|
| **Infancy** | 0 – 4 | Family environment, genetics reveal, early childhood events |
| **Childhood** | 5 – 12 | Primary school, hobbies, early friendships, family dynamics, simplified schedule |
| **Adolescence** | 13 – 17 | High school, GPA, first crushes/dating, part-time jobs, habits forming, full 168h schedule |
| **Young Adult** | 18 – 29 | University/trades, full careers, moving out, credit score, startups, investing, marriage |
| **Prime Adult** | 30 – 49 | Senior/exec ladders, real estate, parenting, peak wealth & burnout risk |
| **Mature Adult** | 50 – 64 | Legacy building, wills, chronic health management, mentorship/retirement planning |
| **Elder** | 65+ | Retirement, pension/investments, grandchildren, rising mortality curve, heir handoff |

---

## 4. Core Turn Loop: 168-Hour Weekly Schedule + Action Points

Each turn combines **continuous lifestyle habits** with **discrete major decisions**:

### A. 168-Hour Weekly Focus Allocation
The player allocates their 168 hours/week across 6 buckets (with smart presets like *Balanced*, *Hustle Grind*, *Family First*, *Health Kick*):
1. **Sleep** (recommended 49–56h/wk; `< 49h` builds Sleep Debt, hurting Cognition, Mood, and Immune Health; `< 35h` risks severe burnout/accidents).
2. **School / Work** (mandatory floor when enrolled/employed; extra hours count as Overtime/Study, boosting GPA/Performance & promotion odds at the cost of Stress).
3. **Hustle & Skills** (builds chosen skills, side-projects, or startup progress).
4. **Fitness & Self-Care** (improves Fitness, Physical Health, Looks, and reduces Stress).
5. **Family & Social** (maintains/grows active Relationships passively each turn; neglecting this causes natural relationship decay).
6. **Leisure & Rest** (primary Stress relief & Happiness recovery; zero leisure accelerates Burnout).

*(For ages 0–12, the schedule is simplified into age-appropriate buckets like Sleep, School, Play, Family, and Extracurriculars.)*

### B. Discrete Action Points (AP)
Spent during the turn on active, high-impact decisions (1–3 AP each):
- **Career/Education:** Apply for jobs, request raise/promotion, network with boss, switch major, launch startup, pitch investors.
- **Relationships:** Deep conversation, date night, propose, confront/apologize, family trip, break up, make new friend.
- **Health:** Doctor checkup, therapy session, rehab/quit habit, cosmetic/dental care.
- **Assets:** Buy/sell stocks, purchase/rent property, take/pay off loan, update will.

### C. Advancing the Turn (`advanceTurn`)
1. Apply 168-hour schedule effects (income, expenses, skill XP, stat drift, stress/burnout, passive relationship maintenance).
2. Tick World Macro-Economy (inflation, market returns, recession/boom state).
3. Tick Health, Habits & Age-Weighted Mortality check.
4. Tick Autonomous NPCs (aging, NPC life events, memory decay, NPC-initiated drama).
5. Roll & Resolve Turn Events (0–2 systemic/story-arc events weighted by Chaos Slider, state triggers, and cooldowns).
6. Append structured entries to the **Life Timeline** and refresh AP for the next turn.

---

## 5. The Three Flagship Simulation Pillars

### Pillar 1: Career, Education & Business Empire
- **Education:** Primary → High School → Trade/Vocational / University (specific majors: CS, Finance, Medicine, Law, Arts, Engineering, etc.) → Grad/Professional School. Tracks GPA, academic stress, scholarships, and student loans.
- **Careers:** Multi-tier ladders (e.g., Junior → Mid → Senior → Director → VP → C-Suite) with salary bands, skill requirements, workplace politics (boss/coworker relationships), performance reviews, raises, and layoff risk during recessions.
- **Business & Startups (v0.1 basic side-hustle/founder → v0.2 full company sim):** Pick industry, bootstrap or raise capital, track revenue, burn rate, morale, product quality, market cycles, and exit via acquisition/IPO or shut down.
- **Personal Finance:** Cash balance, country-specific income tax brackets, living expenses (rent/mortgage/food/dependents), **Credit Score (300–850)**, loans, stocks/index funds/crypto (v0.1–v0.2), real estate ownership & rental yield (v0.2), and Net Worth history chart.

### Pillar 2: Health, Genetics & Psychology
- **Core Visible Stats (0–100):** Physical Health, Mental Health, Happiness, Energy, Stress (0–100, lower is better), Looks, Intelligence, Charisma, Discipline.
- **Hidden / Derived Metrics:** Burnout Meter, Sleep Debt, Immune Resilience, Reputation, Fertility.
- **Genetics (DNA Vector):** Generated at birth (or customized in Pre-Game Setup / Director Mode). Stores baseline ceilings and predispositions for Longevity, Intellect, Athleticism, Mental Resilience, Appearance, and heritable medical traits. Passed to children via blended parental inheritance + small mutation variance.
- **Psychology, Habits & Addictions:**
  - Positive habits (Reading, Gym, Meditation, Journaling) build compounding bonuses.
  - Negative habits/coping mechanisms (Smoking, Heavy Drinking, Gambling, Doomscrolling) spike under high Stress/low Mental Health, create temporary mood relief, and develop dependency that requires active effort/therapy to break.
- **Medical Conditions:** Acute (flu, injury, burnout collapse) and chronic (hypertension, depression, diabetes) with severity, healthcare costs (country-dependent), and treatments.

### Pillar 3: Relationships, Family & Dynasty
- **Rich NPC Model:** Every NPC has:
  - Identity (name, age, gender, role: parent, sibling, friend, partner, boss, coworker, rival, child).
  - Personality axes (Warmth, Ambition, Loyalty, Volatility, Honesty — 0–100).
  - Own life state (health, job, wealth, alive/deceased).
  - 4 Relationship Meters: **Affection**, **Trust**, **Respect**, **Tension**.
  - **Memory Log:** Array of timestamped memories (`{ id, tag, label, sentimentDelta, ageMonths, decayRate }`) — e.g., *"Helped pay my rent"*, *"Missed our anniversary"*, *"Got me promoted"*. Memories directly influence NPC dialogue, acceptance of proposals/favors, and grudge events.
- **Autonomous NPCs:** Each turn, NPCs age, advance their own lives (new jobs, moving, illness, marriage), and can initiate events based on their personality and memories.
- **Parenting & Dynasty (v0.1 family + death summary → v0.3 full heir continuation):** Choose parenting style (Authoritative, Strict, Permissive, Neglectful), write a Will splitting estate, view Family Tree, and continue playing as a chosen heir upon death.

---

## 6. Modular Era Pack System

All era-dependent data lives in `data/eras/<era_id>/`. The engine loads an `EraPack` object at runtime and never hardcodes modern-day assumptions (like dollars, universities, or tech jobs) into core logic.

- **Launch Pack:** `modern_2020s` (18 playable countries with flags, distinct currencies, tax rates, healthcare costs, legal adult/marriage ages, and starting economies — USA, India, UK, Japan, Germany, Brazil, Canada, Australia, France, South Korea, Mexico, Italy, Spain, Singapore, UAE, Sweden, Nigeria, South Africa).
- **Future Packs (Post-v1.0):** `1920s_industrial`, `medieval_feudal`, `2080s_cyberpunk` — dropped in as new JSON folders following the same schema.

---

## 7. Event Engine & Optional AI Narrative Hook

### Offline Systemic Event Engine (Default)
- Events are defined in `events.json` with:
  - `id`, `category` (`career`, `health`, `relationship`, `finance`, `world`, `milestone`, `chaos`)
  - `conditions`: age range, life stage, stat bounds, career/education tags, relationship queries, flags, min Chaos Slider value
  - `weight`, `cooldownTurns`, `oneShot`
  - `title`, `body` (supports template tokens like `{{npc.firstName}}`, `{{job.title}}`, `{{currency(amount)}}`)
  - `choices[]`: each with optional requirements (stat/cash/AP), deterministic + weighted outcomes, stat/money/memory/flag mutations, and optional `followUpEventId` + `delayTurns` for **multi-stage story arcs**.

### Pluggable `NarrativeEnhancer` Interface
```ts
export interface NarrativeEnhancer {
  readonly id: string;
  enhanceEventText(context: EventNarrativeContext): Promise<EnhancedEventText>;
  enhanceMilestoneSummary(context: MilestoneContext): Promise<string>;
}
```
- **Default:** `OfflineNarrativeEnhancer` (instant, zero network, 100% deterministic).
- **Optional (v0.3+):** `BYOAINarrativeEnhancer` (user enters their own Gemini/OpenAI API key in settings; strictly enhances prose/flavor and falls back silently to offline text on any error/timeout).

---

## 8. Fine-Tuning & Director Mode

### Pre-Game World & Character Setup
- Select **Era Pack** (`Modern Day 2020s`) and **Country**.
- Choose **Turn Granularity** (Bi-monthly default, or Yearly / Quarterly / Monthly).
- Configure **Character**: Randomize or fine-tune Name, Gender, Starting DNA/Stats, Family Wealth Tier (Struggling / Working / Middle / Upper-Middle / Wealthy).
- Configure **World Sliders**:
  - **Difficulty** (Forgiving / Realistic / Hardcore)
  - **Chaos Slider (0–100):** 0 = strictly grounded realism; 50 = balanced drama; 100 = wild unpredictable chaos.
  - **Economy Volatility (0–100)**
  - **Ironman Mode Toggle:** Locks out live Director Mode for the run.

### Live Director Mode Drawer (Mid-Run)
Accessible anytime from the top bar (unless Ironman is enabled):
- Live sliders for all Player Stats, Stress, Burnout, Cash, and Credit Score.
- Inspect hidden DNA & NPC Memory Logs.
- Edit any NPC's Affection/Trust/Respect/Tension or trigger life changes.
- Switch Turn Granularity (`monthsPerTurn`) on the fly.
- Shift Macro-Economy state (Boom / Normal / Recession) or force-trigger an event category.

---

## 9. Visual Design & UX Guidelines

### Aesthetic Direction: "BitLife Ergonomics × Sleek Matte Dark Craft"
- **Palette (Matte Graphite Dark — Zero Blue-Slate SaaS / Neon Clutter):**
  - Outer Frame: `#0B0D10`
  - Main App Canvas (Life Journal): `#14171D`
  - Raised Surfaces / Rows / Modals: `#1C2029` with hairline `1px solid #262C38` borders
  - Elevated Surface / Modals: `#232834`
  - Pressed / Hover Surface: `#2A303E`
  - Primary Text: `#F4F6FA` | Secondary Text: `#9CA3B4` | Muted: `#6C7486`
  - Tactile Game Accents (purposeful, never rainbow pill spam):
    - **Primary `+ Age` CTA & Finance:** Vibrant Game Green `#22C55E` (with tactile bevel `#15803D`)
    - **Health Bar / Alerts:** Crisp Coral-Red `#EF4444` (healthy green when high)
    - **Mental / Intellect:** Clean Sky `#38BDF8`
    - **Happiness:** Warm Gold `#FACC15`
    - **Stress / Warning:** Amber-Orange `#F59E0B`
    - **Director Mode:** Soft Violet `#A78BFA`
- **Strict Anti-Patterns (DO NOT DO):**
  - No generic purple-blue neon gradients, glowing borders, or `<Sparkles />` AI clichés.
  - No "SaaS admin dashboard" chip-soup (dozens of tiny colored pills stacked on top of content).
  - No cramped 10px–11px text walls or heavy bordered boxes around every timeline sentence.
- **BitLife-Inspired 3-Zone Mobile Layout (9:16 Phone Frame on Desktop, Fullscreen on Mobile):**
  - **Zone 1 — Slim Top Header:** Character avatar, Name + 1-line subtitle (`Age · Job/Country`) on left; Bold Cash balance + compact AP & Saves/Director buttons on right.
  - **Zone 2 — Airy Main Canvas:**
    - **Home = Life Journal:** Clean chronological biography grouped by Age (`Age 0 · 2025`, `Age 1 · 2026`), generous `14.5px` readable prose, subtle dividers, zero redundant filter/context chip clutter.
    - **Sub-Screens (`Career`, `Assets`, `People`, `Schedule`):** Clean native-style list rows with bold `16px` titles, generous padding, and a crisp `← Life` return header.
  - **Zone 3 — Signature Bottom Dock:**
    - **Bottom Stat Tray:** 4 crisp horizontal stat bars (`Health`, `Mental`, `Happy`, `Stress`) anchored right above the nav bar (tap to open a clean Full Stats & DNA modal sheet instead of crowding the screen).
    - **BitLife-Style 5-Slot Action Dock with Raised Center `+ Age` Button:** `Career` | `Assets` | **`[ +2m ]`** | `People` | `Schedule`. Tapping `+ Age` advances the turn and returns smoothly to the Life Journal.

---

## 10. Phased Roadmap

### v0.1 — Playable Birth-to-Death Vertical Slice (Web Prototype)
- [x] Project scaffold (TypeScript + Vitest + React + Vite) with strict `engine/` vs `ui/` split.
- [x] `modern_2020s` Era Pack JSON (18 countries with flags & legal age rules, education tracks, 7 career ladders / 24+ tiers, conditions & habits, 65+ dynamic/branching events tuned for bi-monthly turns, regional name pools).
- [x] Core pure simulation engine (`turn.ts`, `time.ts`, `schedule.ts`, `health.ts`, `career.ts`, `relationships.ts`, `events.ts`, `director.ts`, `createLife.ts`) + legal age gates + Vitest unit & multi-life soak tests.
- [x] Pre-Game Character & World Setup screen (searchable flag country selector, DNA, family wealth, granularity, chaos slider, Ironman toggle, Quick Random Life).
- [x] Full birth-to-death loop: Infancy → Childhood → Adolescence → Adult → Elder → Death Summary & Legacy Scorecard (BitLife-inspired matte dark UI).
- [x] 168-hour Weekly Focus Allocation UI + Action Points system.
- [x] Memory-based NPCs (parents, siblings, friends, partners, spouses, children) with interactive actions & legal marriage age enforcement.
- [x] Live Director Mode drawer & local multi-slot save/load.

### v0.2 — Deep Business, Investing, Real Estate & Drama
- [ ] Full Startup & Company management (hiring, funding rounds, revenue/burn, IPO/acquisition).
- [ ] Stock market, index funds, crypto & real estate (mortgages, rental income, market cycles).
- [ ] Expanded multi-stage story arcs (100+ events), workplace politics, deeper habit/addiction & therapy loops.
- [ ] Net worth & life-stat history charts.

### v0.3 — Multi-Gen Dynasty & Optional AI Polish
- [ ] Full Wills, estate distribution, interactive Family Tree, and playing on as your child/heir with inherited DNA.
- [ ] Optional BYO AI `NarrativeEnhancer` setting (Gemini API) with graceful offline fallback.
- [ ] Balance tuning pass from playtest feedback.

### v1.0 — Native Android Port (Kotlin + Jetpack Compose)
- [ ] Android Gradle project using Kotlin, Jetpack Compose, and Room DB.
- [ ] 1:1 port of `src/engine/` to Kotlin module, directly bundling `data/eras/` JSON packs.
- [ ] Native Android polish, haptics, offline APK build & sideload verification via `adb`.

---

## 11. Multi-Agent Working Agreement

For any agent (Antigravity, Claude, Codex, etc.) working on this repo:
1. **Read `GAME_PLAN.md` first** before adding files or changing schemas.
2. **Never import UI/React/DOM code inside `src/engine/`** — keep the engine 100% pure TypeScript so it stays deterministic, testable, and Kotlin-portable.
3. **All era content goes in `data/eras/` JSON** — never hardcode country names, job lists, or event text inside engine logic.
4. **Scale every rate by `monthsPerTurn`** — never assume a turn is 1 year or 2 months.
5. **Write/run unit tests (`npm test`)** whenever modifying `src/engine/` formulas (especially `time.ts`, `schedule.ts`, `health.ts`, and `turn.ts`).
6. **Respect the Aesthetic Rules** — keep the UI dark, crisp, tactile, and playful without generic neon-gradient clutter.
7. **Update Section 10 (Roadmap)** checkboxes as milestones are completed so all agents stay synchronized.

# Chronicle — Life Simulator (`v0.1`)

A deep, fine-tuned, 100% offline-first personal life simulator built with a pure TypeScript simulation engine, modular JSON Era Packs, and a mobile-first React UI — architected for a clean 1:1 port to native Android (Kotlin + Jetpack Compose).

See [`GAME_PLAN.md`](./GAME_PLAN.md) for the full design specification, roadmap, and multi-agent working agreement.

## Highlights (`v0.1`)
- **Pure, Deterministic Simulation Engine (`src/engine/`):** Zero React/DOM dependencies, seeded Mulberry32 PRNG, `monthsPerTurn` delta & Poisson survival scaling (Yearly / Quarterly / Bi-monthly / Monthly).
- **168-Hour Weekly Schedule + Action Points:** Continuous lifestyle trade-offs (sleep debt, burnout, overtime, skill XP, passive relationship drift) paired with discrete Action Points.
- **Three Interconnected Pillars:**
  - **Career, Education & Finance:** Auto-enrolled school, degrees & student loans, 7 career ladders, promotions, layoffs, progressive income taxes, investments, housing & home equity.
  - **Health, Genetics & Psychology:** Inheritable DNA profile, positive/negative habits, acute & chronic conditions, burnout, and age/health mortality curves.
  - **Relationships & Memory-Based NPCs:** Personality axes, Affection/Trust/Respect/Tension meters, decaying memory logs, autonomous aging, parenting styles, and legal marriage age checks.
- **Modular Era Pack (`data/eras/modern_2020s/`):** 18 countries with flags, currencies, tax brackets, legal adult/marriage ages, and 65+ dynamic/branching events.
- **Fine-Tuning & Director Mode:** Pre-game DNA, wealth, country, chaos & difficulty sliders, Ironman mode lock, live mid-run Director Mode drawer, and 5 local auto-save slots.

## Quick Start

```bash
npm install
npm run dev -- --host
```

- Desktop preview (mobile frame): `http://localhost:5173/`
- Mobile playtest (same Wi-Fi): use the LAN `Network:` URL printed by Vite

## Scripts

```bash
npm test       # Run Vitest unit & full birth-to-death soak tests
npm run typecheck
npm run build  # Typecheck + production Vite bundle
```

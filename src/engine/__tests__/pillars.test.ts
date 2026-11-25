import { describe, expect, it } from 'vitest';
import {
  addNpcMemory,
  advanceTurn,
  applyDirectorMutation,
  calculateAnnualIncomeTax,
  calculateNetWorth,
  createNewLife,
  createNpc,
  getCountry,
  inheritDna,
  isChoiceAvailable,
  loadEraPack,
  performPlayerAction,
  resolveEventChoice,
  SeededRng,
} from '../index';
import { OfflineNarrativeEnhancer } from '../../narrative/OfflineFormatter';

describe('Career, Education & Finance Pillar', () => {
  const pack = loadEraPack('modern_2020s');

  it('calculates progressive income tax accurately', () => {
    const usa = getCountry(pack, 'usa');
    expect(calculateAnnualIncomeTax(0, usa)).toBe(0);
    // 15,000 * 0.10 = 1,500; 35,000 * 0.18 = 6,300 -> 7,800
    expect(calculateAnnualIncomeTax(50000, usa)).toBe(7800);
  });

  it('auto-enrolls into primary & high school and allows university & job progression', () => {
    let state = createNewLife(
      {
        seed: 101,
        countryId: 'usa',
        granularity: 'YEARLY',
        familyWealthTier: 'middle',
        statOverrides: { intelligence: 85, charisma: 75, discipline: 80 },
      },
      pack,
    );

    // Advance to age 18
    for (let i = 0; i < 18; i++) {
      state = advanceTurn(state, pack);
      if (state.pendingEvent) {
        const firstValid =
          state.pendingEvent.choices.find((c) => isChoiceAvailable(state, c)) ??
          state.pendingEvent.choices[0]!;
        state = resolveEventChoice(state, firstValid.id, pack);
      }
    }

    expect(state.player.education.completedProgramIds).toContain('primary');
    expect(state.player.education.completedProgramIds).toContain('high_school');

    // Enroll in CS degree
    const enroll = performPlayerAction(state, { type: 'ENROLL_EDUCATION', programId: 'uni_cs' }, pack);
    expect(enroll.success).toBe(true);
    state = enroll.state;

    // Complete 4 years of CS
    for (let i = 0; i < 4; i++) {
      state = advanceTurn(state, pack);
      if (state.pendingEvent) {
        const firstValid =
          state.pendingEvent.choices.find((c) => isChoiceAvailable(state, c)) ??
          state.pendingEvent.choices[0]!;
        state = resolveEventChoice(state, firstValid.id, pack);
      }
    }
    expect(state.player.education.completedProgramIds).toContain('uni_cs');

    // Boost coding skill slightly if needed and apply for jr_dev
    state = {
      ...state,
      player: {
        ...state.player,
        skills: { ...state.player.skills, coding: 30 },
        finance: { ...state.player.finance, cash: 10000 },
      },
    };
    const jobRes = performPlayerAction(
      state,
      { type: 'APPLY_JOB', trackId: 'software_eng', tierId: 'jr_dev' },
      pack,
    );
    expect(jobRes.success).toBe(true);
    expect(jobRes.state.player.job?.title).toBe('Junior Software Engineer');

    // Invest & verify net worth preserves value while shifting cash into holdings
    const netWorthBefore = calculateNetWorth(jobRes.state, pack);
    const inv = performPlayerAction(
      jobRes.state,
      { type: 'INVEST', assetId: 'index_global', amount: 2000 },
      pack,
    );
    expect(inv.success).toBe(true);
    expect(inv.state.player.finance.investments[0]?.balance).toBe(2000);
    expect(calculateNetWorth(inv.state, pack)).toBe(netWorthBefore);
  });
});

describe('Relationships, Genetics & Director Mode', () => {
  const pack = loadEraPack('modern_2020s');

  it('tracks NPC memories and decays sentiment over time', () => {
    let state = createNewLife({ seed: 202, granularity: 'YEARLY' }, pack);
    const parent = state.npcs[0]!;
    const withMem = addNpcMemory(parent, 'helped_out', 'Helped during tough time', 20, 0, 0.2);
    expect(withMem.memories).toHaveLength(1);
    expect(withMem.affection).toBeGreaterThanOrEqual(parent.affection);

    state = { ...state, npcs: [withMem, ...state.npcs.slice(1)] };
    state = advanceTurn(state, pack);
    const updatedParent = state.npcs.find((n) => n.id === parent.id)!;
    expect(updatedParent.memories[0]!.sentimentDelta).toBeLessThan(20);
  });

  it('blends parental DNA cleanly for children', () => {
    const rng = new SeededRng(99);
    const dnaA = {
      longevity: 80,
      intellectCeiling: 90,
      athleticism: 70,
      mentalResilience: 75,
      appearance: 65,
      predispositions: ['anxiety'],
    };
    const dnaB = {
      longevity: 60,
      intellectCeiling: 70,
      athleticism: 50,
      mentalResilience: 65,
      appearance: 75,
      predispositions: [],
    };
    const child = inheritDna(rng, dnaA, dnaB);
    expect(child.longevity).toBeGreaterThanOrEqual(15);
    expect(child.longevity).toBeLessThanOrEqual(100);
    expect(child.intellectCeiling).toBeGreaterThanOrEqual(15);
  });

  it('enforces Ironman lock on Director Mode while allowing granularity changes', () => {
    const ironmanState = createNewLife({ seed: 303, ironman: true }, pack);
    const blocked = applyDirectorMutation(ironmanState, { type: 'SET_CASH', amount: 999999 }, pack);
    expect(blocked.applied).toBe(false);
    expect(blocked.state.player.finance.cash).toBe(ironmanState.player.finance.cash);

    const allowedSpeed = applyDirectorMutation(
      ironmanState,
      { type: 'SET_GRANULARITY', granularity: 'MONTHLY' },
      pack,
    );
    expect(allowedSpeed.applied).toBe(true);
    expect(allowedSpeed.state.config.granularity).toBe('MONTHLY');

    const sandboxState = createNewLife({ seed: 303, ironman: false }, pack);
    const cashEdit = applyDirectorMutation(sandboxState, { type: 'SET_CASH', amount: 500000 }, pack);
    expect(cashEdit.applied).toBe(true);
    expect(cashEdit.state.player.finance.cash).toBe(500000);
  });

  it('formats offline narrative templates cleanly', async () => {
    const state = createNewLife({ seed: 404, firstName: 'Rohan', lastName: 'Mehta' }, pack);
    const enhancer = new OfflineNarrativeEnhancer();
    const out = await enhancer.enhanceEventText({
      state,
      event: {
        ...pack.events[0]!,
        title: 'Hello {{player.firstName}}',
        body: 'Welcome, {{player.firstName}} {{player.lastName}}!',
      },
    });
    expect(out.title).toBe('Hello Rohan');
    expect(out.body).toBe('Welcome, Rohan Mehta!');
  });

  it('preserves home equity when selling an owned home to rent or buy another property', () => {
    const buyable = pack.assets.housing.find((h) => h.purchasePrice > 0)!;
    const country = getCountry(pack, 'usa');
    const price = Math.round(buyable.purchasePrice * country.costOfLivingMultiplier);
    const downPayment = Math.round(price * 0.2);

    let state = createNewLife({ seed: 505, countryId: 'usa' }, pack);
    state = {
      ...state,
      player: {
        ...state.player,
        ageMonths: 22 * 12,
        actionPoints: 6,
        finance: {
          ...state.player.finance,
          cash: downPayment + 50000,
        },
      },
    };

    const nwBefore = calculateNetWorth(state, pack);

    // Rent first, then buy the same property (rent-to-own)
    const rented = performPlayerAction(
      state,
      { type: 'CHANGE_HOUSING', housingId: buyable.id, buy: false },
      pack,
    );
    expect(rented.success).toBe(true);
    expect(rented.state.player.finance.ownsHousing).toBe(false);

    const bought = performPlayerAction(
      rented.state,
      { type: 'CHANGE_HOUSING', housingId: buyable.id, buy: true },
      pack,
    );
    expect(bought.success).toBe(true);
    expect(bought.state.player.finance.ownsHousing).toBe(true);
    expect(calculateNetWorth(bought.state, pack)).toBe(nwBefore);

    // Move back to parents_home -> should credit equity back to cash (preserving net worth)
    const sold = performPlayerAction(
      bought.state,
      { type: 'CHANGE_HOUSING', housingId: 'parents_home', buy: false },
      pack,
    );
    expect(sold.success).toBe(true);
    expect(sold.state.player.finance.ownsHousing).toBe(false);
    expect(sold.state.player.finance.mortgageBalance).toBe(0);
    expect(sold.state.player.finance.cash).toBe(downPayment + 50000);
    expect(calculateNetWorth(sold.state, pack)).toBe(nwBefore);
  });

  it('provides 18 countries with flags, legal ages, and regional name pools', () => {
    expect(pack.countries.length).toBeGreaterThanOrEqual(18);
    for (const country of pack.countries) {
      expect(country.flag.length).toBeGreaterThan(0);
      expect(country.legalAdultAge).toBeGreaterThanOrEqual(18);
      expect(country.legalMarriageAge).toBeGreaterThanOrEqual(18);
      const pool = pack.names[country.id];
      expect(pool, `missing name pool for ${country.id}`).toBeDefined();
      expect(pool!.male.length).toBeGreaterThan(0);
      expect(pool!.female.length).toBeGreaterThan(0);
      expect(pool!.last.length).toBeGreaterThan(0);
    }
  });

  it('enforces legal and logical age gates on housing, investing, habits, retirement, and marriage', () => {
    const rng = new SeededRng(606);
    const sixYearOld = {
      ...createNewLife({ seed: 606, countryId: 'usa' }, pack),
    };
    const childState = {
      ...sixYearOld,
      player: {
        ...sixYearOld.player,
        ageMonths: 6 * 12,
        actionPoints: 6,
        finance: { ...sixYearOld.player.finance, cash: 100000 },
      },
    };

    const moveOut = performPlayerAction(
      childState,
      { type: 'CHANGE_HOUSING', housingId: 'shared_apt', buy: false },
      pack,
    );
    expect(moveOut.success).toBe(false);

    const buyHome = performPlayerAction(
      childState,
      { type: 'CHANGE_HOUSING', housingId: 'studio_apt', buy: true },
      pack,
    );
    expect(buyHome.success).toBe(false);

    const invest = performPlayerAction(
      childState,
      { type: 'INVEST', assetId: 'index_global', amount: 1000 },
      pack,
    );
    expect(invest.success).toBe(false);

    const retire = performPlayerAction(childState, { type: 'RETIRE' }, pack);
    expect(retire.success).toBe(false);

    const smoke = performPlayerAction(
      childState,
      { type: 'ADOPT_HABIT', habitId: 'smoking' },
      pack,
    );
    expect(smoke.success).toBe(false);

    // 16-year-old cannot marry in USA (legalMarriageAge 18), nor can 19-year-old in India (legalMarriageAge 21)
    const teenBase = createNewLife({ seed: 607, countryId: 'usa' }, pack);
    const teenPartner = createNpc(rng, pack, 'usa', 'partner', 16 * 12);
    const teenState = {
      ...teenBase,
      player: { ...teenBase.player, ageMonths: 16 * 12, actionPoints: 5 },
      npcs: [
        ...teenBase.npcs,
        { ...teenPartner, affection: 95, trust: 95, tension: 0 },
      ],
    };
    const teenPropose = performPlayerAction(
      teenState,
      { type: 'INTERACT_NPC', npcId: teenPartner.id, interaction: 'propose' },
      pack,
    );
    expect(teenPropose.success).toBe(false);

    // 19-year-old in India (legalMarriageAge = 21) is blocked; 22-year-old is allowed
    const indiaBase = createNewLife({ seed: 608, countryId: 'india' }, pack);
    const indiaPartner = createNpc(rng, pack, 'india', 'partner', 22 * 12);
    const nineteenIndia = {
      ...indiaBase,
      player: { ...indiaBase.player, ageMonths: 19 * 12, actionPoints: 5 },
      npcs: [{ ...indiaPartner, affection: 95, trust: 95, tension: 0 }],
    };
    const underageIndiaPropose = performPlayerAction(
      nineteenIndia,
      { type: 'INTERACT_NPC', npcId: indiaPartner.id, interaction: 'propose' },
      pack,
    );
    expect(underageIndiaPropose.success).toBe(false);

    const adultIndia = {
      ...nineteenIndia,
      player: { ...nineteenIndia.player, ageMonths: 22 * 12 },
    };
    const legalIndiaPropose = performPlayerAction(
      adultIndia,
      { type: 'INTERACT_NPC', npcId: indiaPartner.id, interaction: 'propose' },
      pack,
    );
    expect(legalIndiaPropose.success).toBe(true);
  });
});

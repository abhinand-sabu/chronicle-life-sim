import { describe, expect, it, beforeEach } from 'vitest';
import { createNewLife, loadEraPack } from '../../engine';
import {
  computeStatDeltas,
  deleteSaveSlot,
  formatCalendar,
  formatExactMoney,
  formatGranularityShort,
  formatMoney,
  getLastActiveSlotId,
  loadAllSaveSlots,
  pickSlotForNewLife,
  saveToSlot,
} from '../saveStore';

function installMockLocalStorage() {
  const store = new Map<string, string>();
  const mockStorage = {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, val: string) => {
      store.set(key, val);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
  };
  Object.defineProperty(globalThis, 'window', {
    value: { localStorage: mockStorage },
    configurable: true,
  });
  return store;
}

describe('saveStore & UI formatting helpers', () => {
  const pack = loadEraPack('modern_2020s');
  const usa = pack.countries.find((c) => c.id === 'usa')!;

  beforeEach(() => {
    installMockLocalStorage();
  });

  it('formats money cleanly across negative, thousands, millions, and billions', () => {
    expect(formatMoney(0, usa)).toBe('$0');
    expect(formatMoney(-0.3, usa)).toBe('$0');
    expect(formatExactMoney(-0.4, usa)).toBe('$0');
    expect(formatMoney(-450, usa)).toBe('-$450');
    expect(formatMoney(12500, usa)).toBe('$12,500');
    expect(formatMoney(2_500_000, usa)).toBe('$2.50M');
    expect(formatMoney(1_250_000_000, usa)).toBe('$1.25B');
  });

  it('picks an empty save slot for a new life instead of overwriting an occupied slot', () => {
    const state = createNewLife({ seed: 77 }, pack);
    saveToSlot(1, state);
    const slots = loadAllSaveSlots();
    expect(pickSlotForNewLife(slots, 1)).toBe(2);

    saveToSlot(2, state);
    saveToSlot(3, state);
    saveToSlot(4, state);
    saveToSlot(5, state);
    const fullSlots = loadAllSaveSlots();
    expect(pickSlotForNewLife(fullSlots, 3)).toBe(3);
  });

  it('formats calendar months and granularity labels', () => {
    expect(formatCalendar(2026, 1)).toBe('Jan 2026');
    expect(formatCalendar(2026, 12)).toBe('Dec 2026');
    expect(formatGranularityShort('BIMONTHLY')).toBe('+2 mos');
    expect(formatGranularityShort('YEARLY')).toBe('+1 yr');
  });

  it('computes rounded non-zero stat deltas accurately', () => {
    const base = createNewLife({ seed: 42 }, pack).player.stats;
    const next = { ...base, happiness: base.happiness + 4.4, stress: base.stress - 3 };
    const deltas = computeStatDeltas(base, next);
    expect(deltas.happiness).toBe(4);
    expect(deltas.stress).toBe(-3);
    expect(deltas.physicalHealth).toBeUndefined();
  });

  it('saves, loads, switches active slot, and deletes slots safely', () => {
    const state = createNewLife({ seed: 99, firstName: 'Maya', lastName: 'Patel' }, pack);
    saveToSlot(2, state);

    const loaded = loadAllSaveSlots();
    expect(loaded[2]?.state.player.firstName).toBe('Maya');
    expect(getLastActiveSlotId()).toBe(2);

    deleteSaveSlot(2);
    expect(loadAllSaveSlots()[2]).toBeUndefined();
  });

  it('handles corrupted localStorage JSON gracefully without throwing', () => {
    const store = installMockLocalStorage();
    store.set('chronicle_life_sim_saves_v1', '{not-valid-json');
    expect(loadAllSaveSlots()).toEqual({});
  });
});

import type { CountryDef, GameState, LifeStage, PlayerStats, TurnGranularity } from '../engine';

const STORAGE_KEY = 'chronicle_life_sim_saves_v1';
const ACTIVE_SLOT_KEY = 'chronicle_life_sim_active_slot_v1';
export const MAX_SAVE_SLOTS = 5;

export interface SaveSlot {
  slotId: number;
  updatedAt: number;
  state: GameState;
}

export function loadAllSaveSlots(): Record<number, SaveSlot> {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, SaveSlot>;
    const result: Record<number, SaveSlot> = {};
    for (const [k, v] of Object.entries(parsed)) {
      const id = Number(k);
      if (Number.isInteger(id) && id >= 1 && id <= MAX_SAVE_SLOTS && v?.state?.player) {
        result[id] = v;
      }
    }
    return result;
  } catch {
    return {};
  }
}

export function saveToSlot(slotId: number, state: GameState): Record<number, SaveSlot> {
  const all = loadAllSaveSlots();
  all[slotId] = {
    slotId,
    updatedAt: Date.now(),
    state,
  };
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
    window.localStorage.setItem(ACTIVE_SLOT_KEY, String(slotId));
  } catch {
    // Ignore storage quota errors gracefully
  }
  return all;
}

export function deleteSaveSlot(slotId: number): Record<number, SaveSlot> {
  const all = loadAllSaveSlots();
  delete all[slotId];
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
  } catch {
    // Ignore
  }
  return all;
}

export function getLastActiveSlotId(): number {
  try {
    const raw = window.localStorage.getItem(ACTIVE_SLOT_KEY);
    const id = Number(raw);
    if (Number.isInteger(id) && id >= 1 && id <= MAX_SAVE_SLOTS) return id;
  } catch {
    // Ignore
  }
  return 1;
}

export function pickSlotForNewLife(
  slots: Record<number, SaveSlot>,
  preferredSlotId: number,
): number {
  if (!slots[preferredSlotId] && preferredSlotId >= 1 && preferredSlotId <= MAX_SAVE_SLOTS) {
    return preferredSlotId;
  }
  for (let id = 1; id <= MAX_SAVE_SLOTS; id += 1) {
    if (!slots[id]) return id;
  }
  return preferredSlotId >= 1 && preferredSlotId <= MAX_SAVE_SLOTS ? preferredSlotId : 1;
}

export function formatMoney(amount: number, country: CountryDef): string {
  const rounded = Math.round(amount);
  const abs = Math.abs(rounded);
  const sign = rounded < 0 ? '-' : '';
  if (abs >= 1_000_000_000) {
    return `${sign}${country.currencySymbol}${(abs / 1_000_000_000).toFixed(2)}B`;
  }
  if (abs >= 1_000_000) {
    return `${sign}${country.currencySymbol}${(abs / 1_000_000).toFixed(2)}M`;
  }
  return `${sign}${country.currencySymbol}${abs.toLocaleString('en-US')}`;
}

export function formatExactMoney(amount: number, country: CountryDef): string {
  const rounded = Math.round(amount);
  const abs = Math.abs(rounded);
  const sign = rounded < 0 ? '-' : '';
  return `${sign}${country.currencySymbol}${abs.toLocaleString('en-US')}`;
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function formatCalendar(year: number, month: number): string {
  const m = MONTH_NAMES[(month - 1) % 12] ?? 'Jan';
  return `${m} ${year}`;
}

export function formatLifeStage(stage: LifeStage): string {
  const map: Record<LifeStage, string> = {
    infancy: 'Infancy',
    childhood: 'Childhood',
    adolescence: 'Adolescence',
    young_adult: 'Young Adult',
    prime_adult: 'Prime Adult',
    mature_adult: 'Mature Adult',
    elder: 'Elder',
  };
  return map[stage];
}

export function formatGranularityShort(granularity: TurnGranularity): string {
  switch (granularity) {
    case 'MONTHLY':
      return '+1 mo';
    case 'BIMONTHLY':
      return '+2 mos';
    case 'QUARTERLY':
      return '+3 mos';
    case 'YEARLY':
      return '+1 yr';
  }
}

export function formatGranularityLabel(granularity: TurnGranularity): string {
  switch (granularity) {
    case 'MONTHLY':
      return 'Monthly (12/yr)';
    case 'BIMONTHLY':
      return 'Bi-monthly (6/yr)';
    case 'QUARTERLY':
      return 'Quarterly (4/yr)';
    case 'YEARLY':
      return 'Yearly (1/yr)';
  }
}

export type StatDeltas = Partial<Record<keyof PlayerStats, number>>;

export function computeStatDeltas(prev: PlayerStats, next: PlayerStats): StatDeltas {
  const keys: Array<keyof PlayerStats> = [
    'physicalHealth',
    'mentalHealth',
    'happiness',
    'energy',
    'stress',
    'looks',
    'intelligence',
    'charisma',
    'discipline',
  ];
  const out: StatDeltas = {};
  for (const k of keys) {
    const diff = Math.round(next[k]) - Math.round(prev[k]);
    if (diff !== 0) {
      out[k] = diff;
    }
  }
  return out;
}

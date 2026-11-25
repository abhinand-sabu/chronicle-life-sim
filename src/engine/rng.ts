/**
 * Deterministic, serializable PRNG (Mulberry32).
 * Zero dependency on Math.random() so every save & soak test is 100% reproducible.
 */
export class SeededRng {
  private state: number;

  constructor(seed: number) {
    this.state = seed >>> 0;
  }

  getState(): number {
    return this.state >>> 0;
  }

  /** Returns a float in [0, 1). */
  next(): number {
    let t = (this.state += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  /** Returns an integer in [min, max] inclusive. */
  nextInt(min: number, max: number): number {
    if (max <= min) return min;
    return Math.floor(this.next() * (max - min + 1)) + min;
  }

  /** Returns a float in [min, max). */
  nextRange(min: number, max: number): number {
    return min + this.next() * (max - min);
  }

  /** Box-Muller approximate standard normal distribution N(0, 1). */
  nextGaussian(): number {
    const u1 = Math.max(1e-7, this.next());
    const u2 = this.next();
    return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
  }

  /** Returns true with probability p in [0, 1]. */
  chance(p: number): boolean {
    if (p <= 0) return false;
    if (p >= 1) return true;
    return this.next() < p;
  }

  /** Picks a random element from a non-empty array. */
  pick<T>(items: readonly T[]): T {
    if (items.length === 0) {
      throw new Error('SeededRng.pick called with empty array');
    }
    const idx = Math.floor(this.next() * items.length);
    return items[idx] as T;
  }

  /** Weighted pick from items with positive weight. */
  weightedPick<T>(items: readonly T[], getWeight: (item: T) => number): T | null {
    let total = 0;
    for (const item of items) {
      const w = Math.max(0, getWeight(item));
      total += w;
    }
    if (total <= 0) return null;
    let roll = this.next() * total;
    for (const item of items) {
      roll -= Math.max(0, getWeight(item));
      if (roll <= 0) return item;
    }
    return items[items.length - 1] ?? null;
  }
}

export function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

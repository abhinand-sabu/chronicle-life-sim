import React, { useState } from 'react';
import { ChevronDown, ChevronUp, Dices, Dna, FolderOpen, Lock, Play, Search, Shuffle } from 'lucide-react';
import { listEraPacks, pickNameForCountry, SeededRng } from '../../engine';
import type {
  CreateLifeOptions,
  Difficulty,
  EraPack,
  FamilyWealthTier,
  Gender,
  TurnGranularity,
} from '../../engine';
import { formatGranularityLabel } from '../../store/saveStore';

interface SetupScreenProps {
  pack: EraPack;
  hasSaves: boolean;
  onStartLife: (options: CreateLifeOptions) => void;
  onOpenSaves: () => void;
}

const WEALTH_TIERS: Array<{ id: FamilyWealthTier; label: string }> = [
  { id: 'struggling', label: 'Struggling' },
  { id: 'working', label: 'Working' },
  { id: 'middle', label: 'Middle' },
  { id: 'upper_middle', label: 'Upper-Mid' },
  { id: 'wealthy', label: 'Wealthy' },
];

const DIFFICULTIES: Array<{ id: Difficulty; label: string }> = [
  { id: 'forgiving', label: 'Forgiving' },
  { id: 'realistic', label: 'Realistic' },
  { id: 'hardcore', label: 'Hardcore' },
];

const GRANULARITIES: TurnGranularity[] = ['MONTHLY', 'BIMONTHLY', 'QUARTERLY', 'YEARLY'];

export const SetupScreen: React.FC<SetupScreenProps> = ({
  pack,
  hasSaves,
  onStartLife,
  onOpenSaves,
}) => {
  const eras = listEraPacks();
  const [countryId, setCountryId] = useState(pack.meta.defaultCountryId);
  const [countrySearch, setCountrySearch] = useState('');
  const [gender, setGender] = useState<Gender>('male');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [familyWealthTier, setFamilyWealthTier] = useState<FamilyWealthTier>('middle');
  const [granularity, setGranularity] = useState<TurnGranularity>('BIMONTHLY');
  const [difficulty, setDifficulty] = useState<Difficulty>('realistic');
  const [chaosLevel, setChaosLevel] = useState(35);
  const [economyVolatility, setEconomyVolatility] = useState(40);
  const [ironman, setIronman] = useState(false);

  const [showDna, setShowDna] = useState(false);
  const [customDna, setCustomDna] = useState(false);
  const [dnaLongevity, setDnaLongevity] = useState(60);
  const [dnaIntellect, setDnaIntellect] = useState(65);
  const [dnaAthleticism, setDnaAthleticism] = useState(60);
  const [dnaResilience, setDnaResilience] = useState(60);
  const [dnaAppearance, setDnaAppearance] = useState(60);

  const handleRandomizeName = () => {
    const rng = new SeededRng(Date.now() ^ Math.floor(Math.random() * 100000));
    const picked = pickNameForCountry(rng, pack, countryId, gender);
    setFirstName(picked.firstName);
    setLastName(picked.lastName);
  };

  const handleQuickRandomLife = () => {
    const rng = new SeededRng(Date.now() ^ Math.floor(Math.random() * 100000));
    const nextCountry = rng.pick(pack.countries).id;
    const nextGender = rng.pick<Gender>(['male', 'female', 'non_binary']);
    const nextWealth = rng.pick<FamilyWealthTier>([
      'struggling',
      'working',
      'middle',
      'upper_middle',
      'wealthy',
    ]);
    onStartLife({
      countryId: nextCountry,
      gender: nextGender,
      familyWealthTier: nextWealth,
      granularity,
      difficulty,
      chaosLevel,
      economyVolatility,
      ironman,
    });
  };

  const handleStart = () => {
    onStartLife({
      countryId,
      gender,
      firstName: firstName.trim() || undefined,
      lastName: lastName.trim() || undefined,
      familyWealthTier,
      granularity,
      difficulty,
      chaosLevel,
      economyVolatility,
      ironman,
      dnaOverrides: customDna
        ? {
            longevity: dnaLongevity,
            intellectCeiling: dnaIntellect,
            athleticism: dnaAthleticism,
            mentalResilience: dnaResilience,
            appearance: dnaAppearance,
          }
        : undefined,
    });
  };

  const selectedCountry = pack.countries.find((c) => c.id === countryId) ?? pack.countries[0];
  const filteredCountries = pack.countries.filter((c) => {
    const q = countrySearch.trim().toLowerCase();
    if (!q) return true;
    return c.name.toLowerCase().includes(q) || c.currencyCode.toLowerCase().includes(q);
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Clean Game Header */}
      <header
        style={{
          padding: '20px 18px 14px',
          borderBottom: '1px solid var(--border-subtle)',
          backgroundColor: 'var(--bg-card)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexShrink: 0,
        }}
      >
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 800, letterSpacing: '-0.03em' }}>Chronicle</h1>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 2 }}>
            {eras[0]?.name} · Deep Life Simulator
          </p>
        </div>

        {hasSaves && (
          <button className="btn btn-sm" onClick={onOpenSaves}>
            <FolderOpen size={14} /> Saves
          </button>
        )}
      </header>

      {/* Scrollable Setup Form */}
      <div className="main-scroll" style={{ paddingBottom: 20 }}>
        {/* Character Identity */}
        <section className="card">
          <div className="section-header">
            <span className="section-title">Your Character</span>
            <button className="btn btn-sm btn-ghost" onClick={handleRandomizeName}>
              <Dices size={14} /> Roll Name
            </button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 12 }}>
            <input
              type="text"
              placeholder="First name (auto)"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
            />
            <input
              type="text"
              placeholder="Last name (auto)"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 14 }}>
            {(['male', 'female', 'non_binary'] as Gender[]).map((g) => (
              <button
                key={g}
                className={`btn btn-sm ${gender === g ? 'btn-sky' : ''}`}
                onClick={() => setGender(g)}
                style={{ textTransform: 'capitalize' }}
              >
                {g === 'non_binary' ? 'Non-binary' : g}
              </button>
            ))}
          </div>

          <div className="section-title" style={{ marginBottom: 8 }}>Family Background</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 6 }}>
            {WEALTH_TIERS.map((w) => (
              <button
                key={w.id}
                className={`btn btn-sm ${familyWealthTier === w.id ? 'btn-amber' : ''}`}
                onClick={() => setFamilyWealthTier(w.id)}
              >
                {w.label}
              </button>
            ))}
          </div>
        </section>

        {/* Starting Country */}
        <section className="card">
          <div className="section-header">
            <span className="section-title">Birth Country ({pack.countries.length})</span>
            {selectedCountry && (
              <span className="chip chip-emerald">
                {selectedCountry.flag} {selectedCountry.name}
              </span>
            )}
          </div>

          <div style={{ position: 'relative', marginBottom: 10 }}>
            <Search
              size={14}
              color="#6C7486"
              style={{ position: 'absolute', left: 11, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }}
            />
            <input
              type="text"
              placeholder="Search country or currency..."
              value={countrySearch}
              onChange={(e) => setCountrySearch(e.target.value)}
              style={{ paddingLeft: 32, paddingTop: 9, paddingBottom: 9, fontSize: 13 }}
            />
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 7,
              maxHeight: 204,
              overflowY: 'auto',
              paddingRight: 2,
            }}
          >
            {filteredCountries.map((c) => {
              const active = c.id === countryId;
              return (
                <button
                  key={c.id}
                  onClick={() => setCountryId(c.id)}
                  className="btn btn-sm"
                  style={{
                    justifyContent: 'space-between',
                    padding: '8px 10px',
                    borderColor: active ? 'var(--accent-emerald)' : 'var(--border-subtle)',
                    backgroundColor: active ? 'var(--accent-emerald-soft)' : 'var(--bg-input)',
                    color: active ? '#4ADE80' : 'var(--text-primary)',
                  }}
                >
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0, textAlign: 'left' }}>
                    <span style={{ fontSize: 15, lineHeight: 1 }}>{c.flag}</span>
                    <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.name}</span>
                  </span>
                  <span className="tabular" style={{ opacity: 0.7, fontSize: 11, flexShrink: 0 }}>{c.currencySymbol}</span>
                </button>
              );
            })}
            {filteredCountries.length === 0 && (
              <div style={{ gridColumn: '1 / -1', textAlign: 'center', fontSize: 12.5, color: 'var(--text-muted)', padding: '12px 0' }}>
                No countries match "{countrySearch}"
              </div>
            )}
          </div>

          {selectedCountry && (
            <div
              className="tabular"
              style={{
                display: 'flex',
                gap: 8,
                flexWrap: 'wrap',
                marginTop: 10,
                fontSize: 11.5,
                color: 'var(--text-secondary)',
              }}
            >
              <span>COL {Math.round(selectedCountry.costOfLivingMultiplier * 100)}%</span>
              <span>·</span>
              <span>Adult {selectedCountry.legalAdultAge}y</span>
              <span>·</span>
              <span>Marry {selectedCountry.legalMarriageAge}y</span>
              <span>·</span>
              <span>Retire {selectedCountry.retirementAge}y</span>
            </div>
          )}
        </section>

        {/* Pacing & World Sliders */}
        <section className="card">
          <div className="section-header">
            <span className="section-title">Pacing &amp; World Rules</span>
          </div>

          <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', marginBottom: 8 }}>
            Turn Speed (changeable anytime)
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 14 }}>
            {GRANULARITIES.map((g) => (
              <button
                key={g}
                className={`btn btn-sm ${granularity === g ? 'btn-violet' : ''}`}
                onClick={() => setGranularity(g)}
              >
                {formatGranularityLabel(g)}
              </button>
            ))}
          </div>

          <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', marginBottom: 8 }}>
            Difficulty
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 16 }}>
            {DIFFICULTIES.map((d) => (
              <button
                key={d.id}
                className={`btn btn-sm ${difficulty === d.id ? 'btn-rose' : ''}`}
                onClick={() => setDifficulty(d.id)}
              >
                {d.label}
              </button>
            ))}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 6 }}>
                <span style={{ color: 'var(--text-secondary)' }}>Chaos Level</span>
                <strong className="tabular">{chaosLevel}</strong>
              </div>
              <input
                type="range"
                min={0}
                max={100}
                value={chaosLevel}
                onChange={(e) => setChaosLevel(Number(e.target.value))}
              />
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 6 }}>
                <span style={{ color: 'var(--text-secondary)' }}>Economy Volatility</span>
                <strong className="tabular">{economyVolatility}</strong>
              </div>
              <input
                type="range"
                min={0}
                max={100}
                value={economyVolatility}
                onChange={(e) => setEconomyVolatility(Number(e.target.value))}
              />
            </div>

            <button
              onClick={() => setIronman((v) => !v)}
              className="btn"
              style={{
                justifyContent: 'space-between',
                backgroundColor: ironman ? 'rgba(245, 158, 11, 0.14)' : 'var(--bg-input)',
                borderColor: ironman ? 'rgba(245, 158, 11, 0.45)' : 'var(--border-subtle)',
              }}
            >
              <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                <Lock size={14} color={ironman ? '#FBBF24' : '#6C7486'} />
                Ironman Mode (Locks Director Mode)
              </span>
              <span className={`chip ${ironman ? 'chip-amber' : ''}`}>
                {ironman ? 'ON' : 'OFF'}
              </span>
            </button>
          </div>
        </section>

        {/* Optional DNA Fine-Tuning */}
        <section className="card">
          <button
            onClick={() => setShowDna((s) => !s)}
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 13.5, fontWeight: 700, color: 'var(--accent-violet)' }}>
              <Dna size={16} /> Fine-Tune Starting DNA (Optional)
            </span>
            {showDna ? <ChevronUp size={16} color="#9CA3B4" /> : <ChevronDown size={16} color="#9CA3B4" />}
          </button>

          {showDna && (
            <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <button
                className={`btn btn-sm ${customDna ? 'btn-violet' : ''}`}
                onClick={() => setCustomDna((v) => !v)}
              >
                {customDna ? 'Using Custom DNA Values' : 'Using Natural Random Genetics (Tap to Customize)'}
              </button>

              {customDna && (
                <>
                  {[
                    { label: 'Longevity', val: dnaLongevity, set: setDnaLongevity },
                    { label: 'Intellect Ceiling', val: dnaIntellect, set: setDnaIntellect },
                    { label: 'Athleticism', val: dnaAthleticism, set: setDnaAthleticism },
                    { label: 'Mental Resilience', val: dnaResilience, set: setDnaResilience },
                    { label: 'Appearance', val: dnaAppearance, set: setDnaAppearance },
                  ].map((item) => (
                    <div key={item.label}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 5 }}>
                        <span style={{ color: 'var(--text-secondary)' }}>{item.label}</span>
                        <strong className="tabular">{item.val}</strong>
                      </div>
                      <input
                        type="range"
                        min={15}
                        max={98}
                        value={item.val}
                        onChange={(e) => item.set(Number(e.target.value))}
                      />
                    </div>
                  ))}
                </>
              )}
            </div>
          )}
        </section>
      </div>

      {/* Tactile Footer Actions */}
      <div className="bottom-dock">
        <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 10 }}>
          <button
            className="btn"
            onClick={handleQuickRandomLife}
            title="Start a random life immediately"
            style={{ padding: '14px 14px', borderRadius: 14 }}
          >
            <Shuffle size={16} /> Random
          </button>
          <button className="advance-cta" onClick={handleStart}>
            <Play size={16} fill="currentColor" /> Begin New Life
          </button>
        </div>
      </div>
    </div>
  );
};

import React, { useState } from 'react';
import { Dna, ShieldAlert, X } from 'lucide-react';
import type { GameState, PlayerStats } from '../../engine';
import type { StatDeltas } from '../../store/saveStore';

interface StatStripProps {
  state: GameState;
  deltas: StatDeltas;
}

const PRIMARY_BARS: Array<{
  key: keyof PlayerStats;
  label: string;
  invert?: boolean;
  getColor: (val: number) => string;
}> = [
  {
    key: 'physicalHealth',
    label: 'Health',
    getColor: (v) => (v >= 60 ? '#22C55E' : v >= 35 ? '#F59E0B' : '#EF4444'),
  },
  {
    key: 'mentalHealth',
    label: 'Mental',
    getColor: (v) => (v >= 55 ? '#38BDF8' : v >= 30 ? '#F59E0B' : '#EF4444'),
  },
  {
    key: 'happiness',
    label: 'Happiness',
    getColor: (v) => (v >= 55 ? '#FACC15' : v >= 30 ? '#F59E0B' : '#EF4444'),
  },
  {
    key: 'stress',
    label: 'Stress',
    invert: true,
    getColor: (v) => (v <= 40 ? '#22C55E' : v <= 70 ? '#F59E0B' : '#EF4444'),
  },
];

const SECONDARY_STATS: Array<{ key: keyof PlayerStats; label: string; color: string }> = [
  { key: 'energy', label: 'Energy', color: '#22C55E' },
  { key: 'intelligence', label: 'Intellect', color: '#38BDF8' },
  { key: 'charisma', label: 'Charisma', color: '#F472B6' },
  { key: 'looks', label: 'Looks', color: '#FACC15' },
  { key: 'discipline', label: 'Discipline', color: '#A78BFA' },
];

export const StatStrip: React.FC<StatStripProps> = ({ state, deltas }) => {
  const [sheetOpen, setSheetOpen] = useState(false);
  const { stats, hidden, dna } = state.player;

  return (
    <>
      <button
        type="button"
        className="stat-tray"
        onClick={() => setSheetOpen(true)}
        aria-label="Open Full Stats and DNA"
      >
        <div className="stat-tray-grid">
          {PRIMARY_BARS.map((bar) => {
            const val = Math.round(stats[bar.key]);
            const delta = deltas[bar.key];
            const isGoodDelta = delta ? (bar.invert ? delta < 0 : delta > 0) : false;

            return (
              <div key={bar.key} className="stat-row-mini">
                <div className="stat-row-head tabular">
                  <span>{bar.label}</span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    {delta !== undefined && delta !== 0 && (
                      <span className={`delta-badge ${isGoodDelta ? 'delta-pos' : 'delta-neg'}`}>
                        {delta > 0 ? `+${delta}` : delta}
                      </span>
                    )}
                    <span style={{ color: 'var(--text-primary)', fontWeight: 700 }}>{val}%</span>
                  </span>
                </div>
                <div className="bar-track">
                  <div
                    className="bar-fill"
                    style={{
                      width: `${val}%`,
                      backgroundColor: bar.getColor(val),
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
        <div className="stat-tray-hint">Tap bars for full stats, burnout &amp; DNA</div>
      </button>

      {sheetOpen && (
        <div
          className="overlay-backdrop"
          role="dialog"
          aria-modal="true"
          aria-label="Full Stats and DNA"
          onClick={() => setSheetOpen(false)}
        >
          <div
            className="modal-card"
            onClick={(e) => e.stopPropagation()}
            style={{ display: 'flex', flexDirection: 'column', gap: 16 }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <h2 style={{ fontSize: 18, fontWeight: 800 }}>Character Profile</h2>
                <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', marginTop: 2 }}>
                  Attributes, hidden strain &amp; genetics
                </div>
              </div>
              <button
                className="icon-btn"
                onClick={() => setSheetOpen(false)}
                aria-label="Close Character Profile"
              >
                <X size={16} />
              </button>
            </div>

            {/* Secondary Stats */}
            <div>
              <div className="section-title" style={{ marginBottom: 8 }}>Attributes</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6 }}>
                {SECONDARY_STATS.map((s) => (
                  <div
                    key={s.key}
                    style={{
                      backgroundColor: 'var(--bg-elevated)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 10,
                      padding: '8px 4px',
                      textAlign: 'center',
                    }}
                  >
                    <div style={{ fontSize: 10.5, color: 'var(--text-secondary)' }}>{s.label}</div>
                    <div
                      className="tabular"
                      style={{ fontSize: 15, fontWeight: 800, color: s.color, marginTop: 2 }}
                    >
                      {Math.round(stats[s.key])}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Hidden Metrics */}
            <div>
              <div className="section-title" style={{ marginBottom: 8 }}>Hidden Strain</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>
                <div className="card tabular" style={{ padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12.5, color: 'var(--text-secondary)' }}>
                    <ShieldAlert size={13} color="#EF4444" /> Burnout
                  </span>
                  <strong style={{ color: hidden.burnout >= 65 ? '#F87171' : 'var(--text-primary)' }}>
                    {Math.round(hidden.burnout)}%
                  </strong>
                </div>
                <div className="card tabular" style={{ padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>Sleep Debt</span>
                  <strong style={{ color: hidden.sleepDebt >= 50 ? '#FBBF24' : 'var(--text-primary)' }}>
                    {Math.round(hidden.sleepDebt)}%
                  </strong>
                </div>
                <div className="card tabular" style={{ padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>Immunity</span>
                  <strong>{Math.round(hidden.immuneResilience)}</strong>
                </div>
                <div className="card tabular" style={{ padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>Reputation</span>
                  <strong>{Math.round(hidden.reputation)}</strong>
                </div>
              </div>
            </div>

            {/* DNA Profile */}
            <div className="card" style={{ backgroundColor: 'var(--bg-elevated)' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 12.5,
                  fontWeight: 700,
                  color: 'var(--accent-violet)',
                  marginBottom: 10,
                }}
              >
                <Dna size={14} /> Genetic Profile (DNA)
              </div>
              <div
                className="tabular"
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(5, 1fr)',
                  gap: 4,
                  fontSize: 11,
                  textAlign: 'center',
                }}
              >
                <div>
                  <div style={{ color: 'var(--text-muted)' }}>Longevity</div>
                  <div style={{ fontWeight: 800, fontSize: 14, marginTop: 2 }}>{Math.round(dna.longevity)}</div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)' }}>Intellect</div>
                  <div style={{ fontWeight: 800, fontSize: 14, marginTop: 2 }}>{Math.round(dna.intellectCeiling)}</div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)' }}>Athletic</div>
                  <div style={{ fontWeight: 800, fontSize: 14, marginTop: 2 }}>{Math.round(dna.athleticism)}</div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)' }}>Resilience</div>
                  <div style={{ fontWeight: 800, fontSize: 14, marginTop: 2 }}>{Math.round(dna.mentalResilience)}</div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)' }}>Looks</div>
                  <div style={{ fontWeight: 800, fontSize: 14, marginTop: 2 }}>{Math.round(dna.appearance)}</div>
                </div>
              </div>
              {dna.predispositions.length > 0 && (
                <div style={{ marginTop: 10, fontSize: 11.5, color: 'var(--accent-amber)' }}>
                  Heritable traits: {dna.predispositions.join(', ')}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
};

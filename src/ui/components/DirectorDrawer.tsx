import React, { useEffect, useState } from 'react';
import { HeartPulse, Lock, Sliders, Sparkles, X } from 'lucide-react';
import { getCountry } from '../../engine';
import type {
  DirectorMutation,
  EraPack,
  GameState,
  MacroStateId,
  PlayerStats,
  TurnGranularity,
} from '../../engine';
import { formatGranularityLabel } from '../../store/saveStore';

interface DirectorDrawerProps {
  state: GameState;
  pack: EraPack;
  onMutate: (mutation: DirectorMutation) => void;
  onClose: () => void;
}

interface CommitSliderProps {
  label: string;
  min: number;
  max: number;
  value: number;
  disabled?: boolean;
  capitalizeLabel?: boolean;
  onCommit: (nextValue: number) => void;
}

const CommitSlider: React.FC<CommitSliderProps> = ({
  label,
  min,
  max,
  value,
  disabled,
  capitalizeLabel,
  onCommit,
}) => {
  const [draft, setDraft] = useState(value);

  useEffect(() => {
    setDraft(value);
  }, [value]);

  const commit = () => {
    if (!disabled && draft !== value) {
      onCommit(draft);
    }
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 12,
          marginBottom: 4,
          textTransform: capitalizeLabel ? 'capitalize' : undefined,
        }}
      >
        <span style={{ color: 'var(--text-secondary)' }}>{label}</span>
        <strong className="tabular">{draft}</strong>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        disabled={disabled}
        value={draft}
        onChange={(e) => setDraft(Number(e.target.value))}
        onPointerUp={commit}
        onKeyUp={commit}
        onBlur={commit}
      />
    </div>
  );
};

const STAT_KEYS: Array<{ key: keyof PlayerStats; label: string }> = [
  { key: 'physicalHealth', label: 'Physical Health' },
  { key: 'mentalHealth', label: 'Mental Health' },
  { key: 'happiness', label: 'Happiness' },
  { key: 'energy', label: 'Energy' },
  { key: 'stress', label: 'Stress' },
  { key: 'intelligence', label: 'Intelligence' },
  { key: 'charisma', label: 'Charisma' },
  { key: 'looks', label: 'Looks' },
  { key: 'discipline', label: 'Discipline' },
];

const GRANULARITIES: TurnGranularity[] = ['MONTHLY', 'BIMONTHLY', 'QUARTERLY', 'YEARLY'];
const MACRO_STATES: MacroStateId[] = ['boom', 'normal', 'recession'];

export const DirectorDrawer: React.FC<DirectorDrawerProps> = ({
  state,
  pack,
  onMutate,
  onClose,
}) => {
  const locked = state.config.ironman;
  const country = getCountry(pack, state.config.countryId);
  const [cashInput, setCashInput] = useState(String(Math.round(state.player.finance.cash)));
  const [selectedEventId, setSelectedEventId] = useState(pack.events[0]?.id ?? '');
  const [selectedNpcId, setSelectedNpcId] = useState(state.npcs[0]?.id ?? '');

  useEffect(() => {
    setCashInput(String(Math.round(state.player.finance.cash)));
  }, [state.player.finance.cash]);

  const selectedNpc = state.npcs.find((n) => n.id === selectedNpcId) ?? state.npcs[0];

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer-panel" onClick={(e) => e.stopPropagation()}>
        <div
          style={{
            padding: '14px 16px',
            borderBottom: '1px solid var(--border-subtle)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: 'var(--bg-card)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Sliders size={17} color="#A78BFA" />
            <h3 style={{ fontSize: 16, fontWeight: 800 }}>Director Mode</h3>
            {locked && (
              <span className="chip chip-amber">
                <Lock size={10} /> Ironman
              </span>
            )}
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="Close Director Mode">
            <X size={16} />
          </button>
        </div>

        <div className="main-scroll" style={{ padding: 14 }}>
          {locked && (
            <div
              className="card"
              style={{
                borderColor: 'rgba(245, 158, 11, 0.35)',
                backgroundColor: 'rgba(245, 158, 11, 0.08)',
                fontSize: 12,
                color: '#FCD34D',
                lineHeight: 1.45,
              }}
            >
              Ironman Mode is active. Stat edits and world cheats are locked, but you can still switch Turn Speed below.
            </div>
          )}

          {/* Turn Granularity — always allowed */}
          <div className="card">
            <div className="section-header">
              <span className="section-title">Turn Speed</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
              {GRANULARITIES.map((g) => (
                <button
                  key={g}
                  className={`btn btn-sm ${state.config.granularity === g ? 'btn-violet' : ''}`}
                  onClick={() => onMutate({ type: 'SET_GRANULARITY', granularity: g })}
                >
                  {formatGranularityLabel(g)}
                </button>
              ))}
            </div>
          </div>

          {/* World Controls */}
          <div className="card" style={{ opacity: locked ? 0.5 : 1 }}>
            <div className="section-header">
              <span className="section-title">World & Macro Economy</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 6, marginBottom: 12 }}>
              {MACRO_STATES.map((m) => (
                <button
                  key={m}
                  disabled={locked}
                  className={`btn btn-sm ${state.world.macroState === m ? 'btn-primary' : ''}`}
                  onClick={() => onMutate({ type: 'SET_MACRO_STATE', macroState: m })}
                  style={{ textTransform: 'capitalize' }}
                >
                  {m}
                </button>
              ))}
            </div>

            <CommitSlider
              label="World Chaos Slider"
              min={0}
              max={100}
              disabled={locked}
              value={state.config.chaosLevel}
              onCommit={(chaosLevel) => onMutate({ type: 'SET_CHAOS_LEVEL', chaosLevel })}
            />
          </div>

          {/* Cash & Credit */}
          <div className="card" style={{ opacity: locked ? 0.5 : 1 }}>
            <div className="section-header">
              <span className="section-title">Finance & Health</span>
            </div>
            <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
              <input
                type="number"
                disabled={locked}
                value={cashInput}
                onChange={(e) => setCashInput(e.target.value)}
                placeholder={`Cash (${country.currencySymbol})`}
              />
              <button
                className="btn btn-primary btn-sm"
                disabled={locked}
                onClick={() => onMutate({ type: 'SET_CASH', amount: Number(cashInput) || 0 })}
                style={{ flexShrink: 0 }}
              >
                Set Cash
              </button>
            </div>

            <CommitSlider
              label="Credit Score"
              min={300}
              max={850}
              disabled={locked}
              value={state.player.finance.creditScore}
              onCommit={(score) => onMutate({ type: 'SET_CREDIT_SCORE', score })}
            />

            <button
              className="btn btn-amber btn-sm"
              disabled={locked || state.player.conditions.length === 0}
              onClick={() => onMutate({ type: 'CURE_ALL_CONDITIONS' })}
              style={{ width: '100%', marginTop: 10 }}
            >
              <HeartPulse size={14} /> Cure All Conditions ({state.player.conditions.length})
            </button>
          </div>

          {/* Player Stat Sliders */}
          <div className="card" style={{ opacity: locked ? 0.5 : 1 }}>
            <div className="section-header">
              <span className="section-title">Player Stats</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {STAT_KEYS.map(({ key, label }) => {
                const val = Math.round(state.player.stats[key]);
                return (
                  <CommitSlider
                    key={key}
                    label={label}
                    min={0}
                    max={100}
                    disabled={locked}
                    value={val}
                    onCommit={(value) => onMutate({ type: 'SET_PLAYER_STAT', stat: key, value })}
                  />
                );
              })}
            </div>
          </div>

          {/* NPC Editor */}
          {state.npcs.length > 0 && selectedNpc && (
            <div className="card" style={{ opacity: locked ? 0.5 : 1 }}>
              <div className="section-header">
                <span className="section-title">NPC Meters</span>
              </div>
              <select
                disabled={locked}
                value={selectedNpc.id}
                onChange={(e) => setSelectedNpcId(e.target.value)}
                style={{ marginBottom: 10 }}
              >
                {state.npcs.map((n) => (
                  <option key={n.id} value={n.id}>
                    {n.firstName} {n.lastName} ({n.role}{!n.alive ? ', Deceased' : ''})
                  </option>
                ))}
              </select>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(['affection', 'trust', 'respect', 'tension'] as const).map((meter) => (
                  <CommitSlider
                    key={`${selectedNpc.id}_${meter}`}
                    label={meter}
                    capitalizeLabel
                    min={0}
                    max={100}
                    disabled={locked}
                    value={Math.round(selectedNpc[meter])}
                    onCommit={(val) =>
                      onMutate({
                        type: 'SET_NPC_METERS',
                        npcId: selectedNpc.id,
                        [meter]: val,
                      })
                    }
                  />
                ))}
              </div>
            </div>
          )}

          {/* Force Trigger Event */}
          <div className="card" style={{ opacity: locked ? 0.5 : 1 }}>
            <div className="section-header">
              <span className="section-title">Trigger Event</span>
            </div>
            <select
              disabled={locked}
              value={selectedEventId}
              onChange={(e) => setSelectedEventId(e.target.value)}
              style={{ marginBottom: 8 }}
            >
              {pack.events.map((evt) => (
                <option key={evt.id} value={evt.id}>
                  [{evt.category}] {evt.title}
                </option>
              ))}
            </select>
            <button
              className="btn btn-violet btn-sm"
              disabled={locked || !selectedEventId}
              onClick={() => {
                onMutate({ type: 'TRIGGER_EVENT', eventId: selectedEventId });
                onClose();
              }}
              style={{ width: '100%' }}
            >
              <Sparkles size={13} /> Fire Event Now
            </button>
          </div>
        </div>
      </aside>
    </div>
  );
};

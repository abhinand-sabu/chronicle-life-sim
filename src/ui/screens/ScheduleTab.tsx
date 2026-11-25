import React from 'react';
import { Activity, Brain, Check, Flame, Heart, Moon, Plus, Sparkles, Stethoscope, X } from 'lucide-react';
import {
  evaluateScheduleTick,
  getRequiredWorkOrSchoolHours,
  HOURS_PER_WEEK,
  normalizeSchedule,
  sumScheduleHours,
} from '../../engine';
import type {
  EraPack,
  GameState,
  PlayerAction,
  SchedulePresetId,
  SkillId,
  WeeklySchedule,
} from '../../engine';

interface ScheduleTabProps {
  state: GameState;
  pack: EraPack;
  onUpdateSchedule: (schedule: WeeklySchedule) => void;
  onApplyPreset: (preset: SchedulePresetId) => void;
  onChangeFocusSkill: (skill: SkillId) => void;
  onAction: (action: PlayerAction) => void;
}

const BUCKETS: Array<{
  key: keyof WeeklySchedule;
  label: string;
  color: string;
  min: number;
  max: number;
  hint: string;
}> = [
  { key: 'sleep', label: 'Sleep', color: '#818CF8', min: 21, max: 90, hint: '49h+ (7h/night) avoids sleep debt' },
  { key: 'workOrSchool', label: 'Work / School', color: '#10B981', min: 0, max: 75, hint: 'Extra hours count as overtime/study' },
  { key: 'hustleAndSkills', label: 'Hustle & Skills', color: '#38BDF8', min: 0, max: 50, hint: 'Builds your selected Focus Skill' },
  { key: 'fitnessAndSelfCare', label: 'Fitness & Self-Care', color: '#F59E0B', min: 0, max: 35, hint: 'Boosts Health, Looks & cuts Stress' },
  { key: 'familyAndSocial', label: 'Family & Social', color: '#F43F5E', min: 0, max: 60, hint: 'Passively nurtures relationships' },
  { key: 'leisureAndRest', label: 'Leisure & Rest', color: '#A78BFA', min: 0, max: 70, hint: 'Primary burnout & stress relief' },
];

const PRESETS: Array<{ id: SchedulePresetId; label: string }> = [
  { id: 'balanced', label: 'Balanced' },
  { id: 'hustle', label: 'Hustle Grind' },
  { id: 'family_first', label: 'Family First' },
  { id: 'health_kick', label: 'Health Kick' },
];

export const ScheduleTab: React.FC<ScheduleTabProps> = ({
  state,
  pack,
  onUpdateSchedule,
  onApplyPreset,
  onChangeFocusSkill,
  onAction,
}) => {
  const { schedule, focusSkill, habits } = state.player;
  const requiredHours = getRequiredWorkOrSchoolHours(state, pack);
  const totalHours = sumScheduleHours(schedule);
  const preview = evaluateScheduleTick(
    schedule,
    state.config.granularity,
    requiredHours,
    state.player.hidden.burnout,
  );

  const handleSliderChange = (key: keyof WeeklySchedule, nextVal: number) => {
    const diff = nextVal - schedule[key];
    if (diff === 0) return;

    const updated: WeeklySchedule = { ...schedule, [key]: nextVal };
    // Auto-balance by taking from or giving to leisureAndRest first, then other buckets
    const adjustOrder: Array<keyof WeeklySchedule> = [
      'leisureAndRest',
      'familyAndSocial',
      'hustleAndSkills',
      'fitnessAndSelfCare',
      'workOrSchool',
      'sleep',
    ].filter((k): k is keyof WeeklySchedule => k !== key);

    let remaining = diff;
    for (const target of adjustOrder) {
      if (remaining === 0) break;
      const floor = target === 'sleep' ? 21 : 0;
      if (remaining > 0) {
        const canTake = Math.max(0, updated[target] - floor);
        const take = Math.min(canTake, remaining);
        updated[target] -= take;
        remaining -= take;
      } else {
        updated[target] += -remaining;
        remaining = 0;
      }
    }

    onUpdateSchedule(normalizeSchedule(updated));
  };

  const activeHabitIds = new Set(habits.map((h) => h.id));
  const positiveHabits = pack.conditions.habits.filter((h) => h.polarity === 'positive');
  const allSkills = Object.keys(state.player.skills) as SkillId[];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* 168-Hour Header & Stacked Bar */}
      <div className="card">
        <div className="section-header">
          <span className="section-title">168h Weekly Allocation</span>
          <span className="chip chip-sky tabular">{totalHours} / {HOURS_PER_WEEK}h</span>
        </div>

        <div className="stacked-bar" style={{ marginBottom: 10 }}>
          {BUCKETS.map((b) => (
            <div
              key={b.key}
              style={{
                width: `${(schedule[b.key] / HOURS_PER_WEEK) * 100}%`,
                backgroundColor: b.color,
                transition: 'width 0.2s ease',
              }}
              title={`${b.label}: ${schedule[b.key]}h`}
            />
          ))}
        </div>

        {/* Presets */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 6, marginBottom: 12 }}>
          {PRESETS.map((p) => (
            <button key={p.id} className="btn btn-sm" style={{ fontSize: 11.5, padding: '7px 8px' }} onClick={() => onApplyPreset(p.id)}>
              {p.label}
            </button>
          ))}
        </div>

        {/* Sliders */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
          {BUCKETS.map((b) => {
            const val = schedule[b.key];
            const showRequired = b.key === 'workOrSchool' && requiredHours > 0;
            return (
              <div key={b.key}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 3 }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12.5, fontWeight: 600 }}>
                    <span
                      style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        backgroundColor: b.color,
                        display: 'inline-block',
                      }}
                    />
                    {b.label}
                    {showRequired && (
                      <span className="chip" style={{ fontSize: 9.5, padding: '1px 6px' }}>
                        Min {requiredHours}h
                      </span>
                    )}
                  </span>
                  <strong className="tabular" style={{ fontSize: 12.5 }}>{val}h/wk</strong>
                </div>
                <input
                  type="range"
                  min={b.min}
                  max={b.max}
                  value={val}
                  onChange={(e) => handleSliderChange(b.key, Number(e.target.value))}
                />
                <div style={{ fontSize: 10.5, color: 'var(--text-muted)', marginTop: 2 }}>
                  {b.hint}
                </div>
              </div>
            );
          })}
        </div>

        {/* Live Trade-off Preview */}
        <div
          style={{
            marginTop: 12,
            paddingTop: 10,
            borderTop: '1px solid var(--border-subtle)',
            display: 'flex',
            flexWrap: 'wrap',
            gap: 6,
          }}
        >
          {schedule.sleep < 49 && (
            <span className="chip chip-rose">
              <Moon size={10} /> Sleep Debt Risk
            </span>
          )}
          {requiredHours > 0 && preview.overtimeHours > 0 && (
            <span className="chip chip-emerald">
              <Flame size={10} /> +{preview.overtimeHours}h Overtime
            </span>
          )}
          {preview.burnoutDelta > 1 && (
            <span className="chip chip-amber">Burnout Rising</span>
          )}
          {preview.passiveNpcAffectionDelta < 0 && (
            <span className="chip chip-rose">
              <Heart size={10} /> Relationships Decaying
            </span>
          )}
          {preview.focusSkillXp > 0 && (
            <span className="chip chip-sky tabular">
              <Sparkles size={10} /> +{preview.focusSkillXp.toFixed(1)} {focusSkill.replace('_', ' ')} XP/turn
            </span>
          )}
        </div>
      </div>

      {/* Focus Skill Selector */}
      <div className="card">
        <div className="section-header">
          <span className="section-title">Hustle Focus Skill</span>
          <span className="tabular" style={{ fontSize: 11, color: 'var(--accent-sky)' }}>
            Lv {Math.round(state.player.skills[focusSkill] ?? 0)}
          </span>
        </div>
        <select
          value={focusSkill}
          onChange={(e) => onChangeFocusSkill(e.target.value as SkillId)}
        >
          {allSkills.map((sk) => (
            <option key={sk} value={sk}>
              {sk.replace('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase())} (Lv {Math.round(state.player.skills[sk] ?? 0)})
            </option>
          ))}
        </select>
      </div>

      {/* Health & Wellness Actions */}
      <div className="card">
        <div className="section-header">
          <span className="section-title">Wellness & Medical</span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <button className="btn btn-amber btn-sm" onClick={() => onAction({ type: 'DOCTOR_VISIT' })}>
            <Stethoscope size={13} /> Doctor (2 AP)
          </button>
          <button className="btn btn-sky btn-sm" onClick={() => onAction({ type: 'THERAPY_SESSION' })}>
            <Brain size={13} /> Therapy (2 AP)
          </button>
        </div>
      </div>

      {/* Habits */}
      <div className="card">
        <div className="section-header">
          <span className="section-title">Habits & Routines</span>
          <span className="chip">{habits.length} Active</span>
        </div>

        {habits.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 10 }}>
            {habits.map((h) => {
              const def = pack.conditions.habits.find((d) => d.id === h.id);
              const isPos = def?.polarity === 'positive';
              return (
                <div
                  key={h.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    backgroundColor: 'var(--bg-input)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 8,
                    padding: '7px 10px',
                  }}
                >
                  <div>
                    <div style={{ fontSize: 12.5, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Activity size={12} color={isPos ? '#10B981' : '#F43F5E'} />
                      {def?.name ?? h.id}
                    </div>
                    <div className="tabular" style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>
                      Strength {Math.round(h.strength)}%
                    </div>
                  </div>
                  <button
                    className={`btn btn-sm ${isPos ? '' : 'btn-rose'}`}
                    onClick={() => onAction({ type: 'QUIT_HABIT', habitId: h.id })}
                  >
                    <X size={12} /> Quit (1 AP)
                  </button>
                </div>
              );
            })}
          </div>
        )}

        <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 6 }}>
          Adopt Positive Habits (1 AP)
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {positiveHabits.map((h) => {
            const active = activeHabitIds.has(h.id);
            return (
              <button
                key={h.id}
                disabled={active}
                className={`btn btn-sm ${active ? '' : 'btn-sky'}`}
                onClick={() => onAction({ type: 'ADOPT_HABIT', habitId: h.id })}
              >
                {active ? <Check size={12} /> : <Plus size={12} />} {h.name}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};

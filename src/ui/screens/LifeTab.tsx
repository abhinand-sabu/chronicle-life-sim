import React, { useMemo } from 'react';
import { Activity, AlertTriangle, Brain, Stethoscope } from 'lucide-react';
import { getAgeYears } from '../../engine';
import type { EraPack, GameState, PlayerAction, TimelineEntry } from '../../engine';

interface LifeTabProps {
  state: GameState;
  pack: EraPack;
  onAction: (action: PlayerAction) => void;
}

interface AgeGroup {
  ageYears: number;
  year: number;
  entries: TimelineEntry[];
}

export const LifeTab: React.FC<LifeTabProps> = ({ state, pack, onAction }) => {
  const ageGroups = useMemo<AgeGroup[]>(() => {
    const groups: AgeGroup[] = [];
    for (const entry of state.timeline) {
      const ageYears = getAgeYears(entry.ageMonths);
      const last = groups[groups.length - 1];
      if (last && last.ageYears === ageYears) {
        last.entries.push(entry);
      } else {
        groups.push({ ageYears, year: entry.year, entries: [entry] });
      }
    }
    return groups;
  }, [state.timeline]);

  const showWellnessAlert =
    state.player.conditions.length > 0 || state.player.hidden.burnout >= 65;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      {/* Active Health Alert (only rendered when actually relevant) */}
      {showWellnessAlert && (
        <div
          className="card"
          style={{
            borderColor: 'rgba(245, 158, 11, 0.42)',
            backgroundColor: 'rgba(245, 158, 11, 0.08)',
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 7,
              color: '#FBBF24',
              fontSize: 13.5,
              fontWeight: 700,
            }}
          >
            <AlertTriangle size={15} /> Health &amp; Wellness Alert
          </div>

          {state.player.hidden.burnout >= 65 && (
            <div style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.45 }}>
              Burnout is high ({Math.round(state.player.hidden.burnout)}%). Ease off your Schedule or book Therapy.
            </div>
          )}

          {state.player.conditions.map((c) => {
            const def = pack.conditions.conditions.find((d) => d.id === c.id);
            return (
              <div
                key={c.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 10,
                  backgroundColor: 'var(--bg-card)',
                  padding: '9px 12px',
                  borderRadius: 10,
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div>
                  <div style={{ fontSize: 13.5, fontWeight: 700 }}>{def?.name ?? c.id}</div>
                  <div style={{ fontSize: 11.5, color: 'var(--text-secondary)', marginTop: 2 }}>
                    {c.treated ? 'Under treatment' : 'Untreated'} · Severity {Math.round(c.severity)}
                  </div>
                </div>
                <button
                  className="btn btn-sm btn-amber"
                  onClick={() => onAction({ type: 'DOCTOR_VISIT', conditionId: c.id })}
                >
                  <Stethoscope size={13} /> Treat (2 AP)
                </button>
              </div>
            );
          })}

          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn btn-sm btn-sky"
              style={{ flex: 1 }}
              onClick={() => onAction({ type: 'THERAPY_SESSION' })}
            >
              <Brain size={13} /> Therapy (2 AP)
            </button>
            <button
              className="btn btn-sm btn-amber"
              style={{ flex: 1 }}
              onClick={() => onAction({ type: 'DOCTOR_VISIT' })}
            >
              <Activity size={13} /> Checkup (2 AP)
            </button>
          </div>
        </div>
      )}

      {/* Clean BitLife-Style Chronological Life Journal */}
      <div className="journal-container">
        {ageGroups.length === 0 ? (
          <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '24px 0' }}>
            Your story begins here. Tap Age to move forward.
          </div>
        ) : (
          ageGroups.map((group) => (
            <section key={`${group.ageYears}-${group.year}`} className="journal-age-group">
              <div className="journal-age-header">
                <span className="journal-age-pill tabular">Age {group.ageYears}</span>
                <span className="journal-age-year tabular">{group.year}</span>
                <div className="journal-age-line" />
              </div>

              <div className="journal-entries">
                {group.entries.map((entry) => (
                  <div key={entry.id} className="journal-entry">
                    <span className={`journal-dot cat-${entry.category}`} aria-hidden="true" />
                    <p className="journal-body">
                      <strong>{entry.title}</strong> — {entry.detail}
                    </p>
                  </div>
                ))}
              </div>
            </section>
          ))
        )}
      </div>
    </div>
  );
};

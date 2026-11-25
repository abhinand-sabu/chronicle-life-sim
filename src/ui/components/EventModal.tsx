import React from 'react';
import { Lock } from 'lucide-react';
import { getCountry, isChoiceAvailable } from '../../engine';
import type { EraPack, EventChoice, GameEvent, GameState } from '../../engine';
import { formatTemplate } from '../../narrative/OfflineFormatter';
import { formatExactMoney } from '../../store/saveStore';

interface EventModalProps {
  state: GameState;
  event: GameEvent;
  pack: EraPack;
  onSelectChoice: (choiceId: string) => void;
}

const CATEGORY_META: Record<GameEvent['category'], { label: string; color: string }> = {
  milestone: { label: 'Milestone', color: '#38BDF8' },
  career: { label: 'Career', color: '#22C55E' },
  finance: { label: 'Finance', color: '#22C55E' },
  health: { label: 'Health', color: '#F59E0B' },
  relationship: { label: 'Relationship', color: '#F472B6' },
  world: { label: 'World Event', color: '#9CA3B4' },
  chaos: { label: 'Wildcard', color: '#A78BFA' },
};

function getRequirementLabel(choice: EventChoice, state: GameState, pack: EraPack): string | null {
  const req = choice.requirements;
  if (!req) return null;
  const country = getCountry(pack, state.config.countryId);
  const parts: string[] = [];
  if (req.minCash !== undefined && state.player.finance.cash < req.minCash) {
    parts.push(`Need ${formatExactMoney(req.minCash, country)}`);
  }
  if (req.minIntelligence !== undefined && state.player.stats.intelligence < req.minIntelligence) {
    parts.push(`Need Intellect ${req.minIntelligence}+`);
  }
  if (req.minCharisma !== undefined && state.player.stats.charisma < req.minCharisma) {
    parts.push(`Need Charisma ${req.minCharisma}+`);
  }
  return parts.length > 0 ? parts.join(' · ') : null;
}

export const EventModal: React.FC<EventModalProps> = ({
  state,
  event,
  pack,
  onSelectChoice,
}) => {
  const ctx = { state, event };
  const title = formatTemplate(event.title, ctx);
  const body = formatTemplate(event.body, ctx);
  const meta = CATEGORY_META[event.category];

  return (
    <div className="overlay-backdrop" role="dialog" aria-modal="true" aria-labelledby="event-modal-title">
      <div className="modal-card">
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 10,
          }}
        >
          <span
            style={{
              fontSize: 11.5,
              fontWeight: 800,
              textTransform: 'uppercase',
              letterSpacing: '0.07em',
              color: meta.color,
            }}
          >
            {meta.label}
          </span>
          <span className="tabular" style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
            Turn {state.turn}
          </span>
        </div>

        <h2
          id="event-modal-title"
          style={{
            fontSize: 21,
            fontWeight: 800,
            color: 'var(--text-primary)',
            marginBottom: 10,
            lineHeight: 1.25,
          }}
        >
          {title}
        </h2>

        <p style={{ fontSize: 15, lineHeight: 1.6, color: 'var(--text-secondary)', marginBottom: 22 }}>
          {body}
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {event.choices.map((choice) => {
            const available = isChoiceAvailable(state, choice);
            const unmet = getRequirementLabel(choice, state, pack);

            return (
              <button
                key={choice.id}
                disabled={!available}
                onClick={() => onSelectChoice(choice.id)}
                className="btn"
                style={{
                  width: '100%',
                  justifyContent: 'space-between',
                  textAlign: 'left',
                  padding: '14px 16px',
                  backgroundColor: available ? 'var(--bg-elevated)' : '#151820',
                  borderColor: available ? 'var(--border-strong)' : 'var(--border-subtle)',
                  borderRadius: 14,
                  boxShadow: available ? '0 2px 0 rgba(0, 0, 0, 0.35)' : 'none',
                }}
              >
                <span style={{ fontSize: 14, fontWeight: 700, lineHeight: 1.35 }}>
                  {choice.label}
                </span>
                {!available && unmet && (
                  <span
                    className="chip chip-rose"
                    style={{ flexShrink: 0, marginLeft: 10, fontSize: 10.5 }}
                  >
                    <Lock size={11} /> {unmet}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};

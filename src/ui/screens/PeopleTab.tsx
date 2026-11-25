import React, { useState } from 'react';
import { ChevronDown, ChevronUp, Gift, Heart, HeartHandshake, MessageCircle, ShieldAlert } from 'lucide-react';
import { formatAge, getAgeYears, getCountry, getNpcMemorySentiment, loadEraPack } from '../../engine';
import type { EraPack, GameState, Npc, ParentingStyle, PlayerAction } from '../../engine';
import { Avatar } from '../components/Avatar';

interface PeopleTabProps {
  state: GameState;
  pack?: EraPack;
  onAction: (action: PlayerAction) => void;
}

const PARENTING_STYLES: ParentingStyle[] = ['authoritative', 'permissive', 'strict', 'neglectful'];

const ROLE_ORDER: Record<Npc['role'], number> = {
  spouse: 0,
  partner: 1,
  child: 2,
  parent: 3,
  sibling: 4,
  friend: 5,
  boss: 6,
  coworker: 7,
  rival: 8,
};

export const PeopleTab: React.FC<PeopleTabProps> = ({ state, pack, onAction }) => {
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const resolvedPack = pack ?? loadEraPack(state.config.eraId);
  const country = getCountry(resolvedPack, state.config.countryId);
  const playerAgeYears = getAgeYears(state.player.ageMonths);

  const sortedNpcs = [...state.npcs].sort((a, b) => {
    if (a.alive !== b.alive) return a.alive ? -1 : 1;
    return ROLE_ORDER[a.role] - ROLE_ORDER[b.role];
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span className="section-title">Relationships & Family</span>
        <span className="chip chip-rose tabular">
          {state.npcs.filter((n) => n.alive).length} Living
        </span>
      </div>

      {sortedNpcs.length === 0 ? (
        <div className="card" style={{ fontSize: 13, color: 'var(--text-muted)', textAlign: 'center' }}>
          No relationships yet. Advance turns to meet people!
        </div>
      ) : (
        sortedNpcs.map((npc) => {
          const isExpanded = expandedId === npc.id;
          const memSentiment = Math.round(getNpcMemorySentiment(npc));

          return (
            <div
              key={npc.id}
              className="card"
              style={{
                opacity: npc.alive ? 1 : 0.6,
                display: 'flex',
                flexDirection: 'column',
                gap: 10,
              }}
            >
              {/* Header */}
              <div
                onClick={() => setExpandedId(isExpanded ? null : npc.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 10,
                  cursor: 'pointer',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                  <Avatar
                    ageMonths={npc.ageMonths}
                    gender={npc.gender}
                    alive={npc.alive}
                    happiness={npc.affection}
                    stress={npc.tension}
                    size={38}
                  />
                  <div style={{ minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <strong style={{ fontSize: 14, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {npc.firstName} {npc.lastName}
                      </strong>
                      <span
                        className={`chip ${
                          npc.role === 'spouse' || npc.role === 'partner'
                            ? 'chip-rose'
                            : npc.role === 'child'
                              ? 'chip-amber'
                              : npc.role === 'boss'
                                ? 'chip-emerald'
                                : ''
                        }`}
                        style={{ textTransform: 'capitalize', fontSize: 10 }}
                      >
                        {npc.role}
                      </span>
                    </div>
                    <div className="tabular" style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
                      {npc.alive ? formatAge(npc.ageMonths) : 'Deceased'} • {npc.memories.length} memories
                      {memSentiment !== 0 && (
                        <span style={{ color: memSentiment > 0 ? '#34D399' : '#FB7185', marginLeft: 5 }}>
                          ({memSentiment > 0 ? `+${memSentiment}` : memSentiment})
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {isExpanded ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
              </div>

              {/* 4 Meters */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
                {[
                  { label: 'Affection', val: npc.affection, color: '#F43F5E' },
                  { label: 'Trust', val: npc.trust, color: '#38BDF8' },
                  { label: 'Respect', val: npc.respect, color: '#10B981' },
                  { label: 'Tension', val: npc.tension, color: '#F59E0B' },
                ].map((m) => (
                  <div key={m.label}>
                    <div className="tabular" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: 'var(--text-secondary)', marginBottom: 3 }}>
                      <span>{m.label}</span>
                      <span style={{ color: 'var(--text-primary)' }}>{Math.round(m.val)}</span>
                    </div>
                    <div className="bar-track" style={{ height: 5 }}>
                      <div className="bar-fill" style={{ width: `${Math.round(m.val)}%`, backgroundColor: m.color }} />
                    </div>
                  </div>
                ))}
              </div>

              {/* Quick Actions */}
              {npc.alive && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
                  <button
                    className="btn btn-sm"
                    onClick={() => onAction({ type: 'INTERACT_NPC', npcId: npc.id, interaction: 'talk' })}
                  >
                    <MessageCircle size={12} /> Talk
                  </button>
                  <button
                    className="btn btn-sm btn-amber"
                    onClick={() => onAction({ type: 'INTERACT_NPC', npcId: npc.id, interaction: 'gift' })}
                  >
                    <Gift size={12} /> Gift
                  </button>
                  {(npc.role === 'partner' || npc.role === 'spouse') && (
                    <button
                      className="btn btn-sm btn-rose"
                      disabled={playerAgeYears < 13}
                      onClick={() => onAction({ type: 'INTERACT_NPC', npcId: npc.id, interaction: 'date' })}
                    >
                      <Heart size={12} /> Date
                    </button>
                  )}
                  {npc.role === 'partner' && (
                    <button
                      className="btn btn-sm btn-violet"
                      disabled={
                        playerAgeYears < country.legalMarriageAge ||
                        getAgeYears(npc.ageMonths) < country.legalMarriageAge
                      }
                      title={
                        playerAgeYears < country.legalMarriageAge ||
                        getAgeYears(npc.ageMonths) < country.legalMarriageAge
                          ? `Legal marriage age is ${country.legalMarriageAge}+ in ${country.name}`
                          : 'Propose marriage'
                      }
                      onClick={() => onAction({ type: 'INTERACT_NPC', npcId: npc.id, interaction: 'propose' })}
                    >
                      <HeartHandshake size={12} />{' '}
                      {playerAgeYears < country.legalMarriageAge ||
                      getAgeYears(npc.ageMonths) < country.legalMarriageAge
                        ? `Marry (${country.legalMarriageAge}+)`
                        : 'Propose'}
                    </button>
                  )}
                  {npc.tension >= 20 && (
                    <button
                      className="btn btn-sm btn-sky"
                      onClick={() => onAction({ type: 'INTERACT_NPC', npcId: npc.id, interaction: 'apologize' })}
                    >
                      Apologize
                    </button>
                  )}
                  <button
                    className="btn btn-sm btn-ghost"
                    onClick={() => onAction({ type: 'INTERACT_NPC', npcId: npc.id, interaction: 'argue' })}
                  >
                    <ShieldAlert size={12} /> Argue
                  </button>
                  {(npc.role === 'partner' || npc.role === 'spouse') && (
                    <button
                      className="btn btn-sm btn-rose"
                      onClick={() => onAction({ type: 'INTERACT_NPC', npcId: npc.id, interaction: 'breakup' })}
                    >
                      {npc.role === 'spouse' ? 'Divorce' : 'Break Up'}
                    </button>
                  )}
                </div>
              )}

              {/* Expanded Details: Parenting, Personality & Memory Log */}
              {isExpanded && (
                <div
                  style={{
                    paddingTop: 10,
                    borderTop: '1px solid var(--border-subtle)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 10,
                    animation: 'fadeIn 0.15s ease',
                  }}
                >
                  {npc.role === 'child' && npc.alive && (
                    <div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 5 }}>
                        Parenting Style (0 AP)
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 5 }}>
                        {PARENTING_STYLES.map((style) => (
                          <button
                            key={style}
                            className={`btn btn-sm ${npc.parentingStyle === style ? 'btn-amber' : ''}`}
                            style={{ fontSize: 11, padding: '6px 6px', textTransform: 'capitalize' }}
                            onClick={() => onAction({ type: 'SET_PARENTING_STYLE', npcId: npc.id, style })}
                          >
                            {style}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Personality Axes */}
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 5 }}>
                      Personality Profile
                    </div>
                    <div className="tabular" style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
                      <span className="chip">Warmth {npc.personality.warmth}</span>
                      <span className="chip">Loyalty {npc.personality.loyalty}</span>
                      <span className="chip">Ambition {npc.personality.ambition}</span>
                      <span className="chip">Volatility {npc.personality.volatility}</span>
                      <span className="chip">Honesty {npc.personality.honesty}</span>
                    </div>
                  </div>

                  {/* Memory Log */}
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 5 }}>
                      Memory Log
                    </div>
                    {npc.memories.length === 0 ? (
                      <div style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
                        No major shared memories yet. Interact to build history.
                      </div>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                        {npc.memories.slice(0, 6).map((m) => (
                          <div
                            key={m.id}
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              fontSize: 11.5,
                              padding: '5px 8px',
                              borderRadius: 7,
                              backgroundColor: 'var(--bg-input)',
                            }}
                          >
                            <span>{m.label}</span>
                            <span
                              className="tabular"
                              style={{
                                fontWeight: 700,
                                color: m.sentimentDelta >= 0 ? '#34D399' : '#FB7185',
                              }}
                            >
                              {m.sentimentDelta > 0 ? `+${Math.round(m.sentimentDelta)}` : Math.round(m.sentimentDelta)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        })
      )}
    </div>
  );
};

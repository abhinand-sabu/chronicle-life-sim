import React from 'react';
import { Award, Briefcase, RotateCcw, Users, Wallet } from 'lucide-react';
import { formatAge, getCountry } from '../../engine';
import type { EraPack, GameState } from '../../engine';
import { formatMoney } from '../../store/saveStore';
import { Avatar } from '../components/Avatar';

interface DeathSummaryScreenProps {
  state: GameState;
  pack: EraPack;
  onNewLife: () => void;
}

export const DeathSummaryScreen: React.FC<DeathSummaryScreenProps> = ({
  state,
  pack,
  onNewLife,
}) => {
  const summary = state.deathSummary;
  const country = getCountry(pack, state.config.countryId);
  if (!summary) return null;

  const milestones = state.timeline.filter((t) => t.category === 'milestone' || t.category === 'career').slice(0, 8);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div className="main-scroll" style={{ padding: '24px 16px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 10 }}>
          <Avatar
            ageMonths={state.player.ageMonths}
            gender={state.player.gender}
            alive={false}
            size={68}
          />
          <div>
            <span className="chip chip-rose" style={{ marginBottom: 6 }}>End of Chronicle</span>
            <h1 style={{ fontSize: 24, fontWeight: 800 }}>
              {state.player.firstName} {state.player.lastName}
            </h1>
            <p className="tabular" style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>
              Lived {formatAge(summary.ageMonths)} • {summary.causeOfDeath}
            </p>
          </div>
        </div>

        {/* Legacy Score Hero */}
        <div
          className="card-elevated"
          style={{
            textAlign: 'center',
            borderColor: 'rgba(167, 139, 250, 0.45)',
          }}
        >
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--accent-violet)', fontWeight: 700 }}>
            <Award size={13} style={{ verticalAlign: '-2px', marginRight: 4 }} />
            Legacy Score
          </div>
          <div
            className="tabular"
            style={{
              fontFamily: 'var(--font-display)',
              fontSize: 42,
              fontWeight: 800,
              color: 'var(--text-primary)',
              marginTop: 2,
            }}
          >
            {summary.legacyScore}
          </div>
        </div>

        {/* Key Metrics */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <div className="card">
            <div style={{ fontSize: 11, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 4 }}>
              <Wallet size={12} color="#10B981" /> Final Net Worth
            </div>
            <div className="tabular" style={{ fontSize: 17, fontWeight: 800, marginTop: 4, color: 'var(--accent-emerald)' }}>
              {formatMoney(summary.finalNetWorth, country)}
            </div>
          </div>

          <div className="card">
            <div style={{ fontSize: 11, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 4 }}>
              <Wallet size={12} color="#38BDF8" /> Peak Net Worth
            </div>
            <div className="tabular" style={{ fontSize: 17, fontWeight: 800, marginTop: 4, color: 'var(--accent-sky)' }}>
              {formatMoney(summary.peakNetWorth, country)}
            </div>
          </div>

          <div className="card">
            <div style={{ fontSize: 11, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 4 }}>
              <Briefcase size={12} color="#F59E0B" /> Highest Role
            </div>
            <div style={{ fontSize: 13.5, fontWeight: 700, marginTop: 4 }}>
              {summary.highestJobTitle ?? 'Unemployed'}
            </div>
          </div>

          <div className="card">
            <div style={{ fontSize: 11, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 4 }}>
              <Users size={12} color="#F43F5E" /> Children
            </div>
            <div className="tabular" style={{ fontSize: 17, fontWeight: 800, marginTop: 4 }}>
              {summary.childrenCount}
            </div>
          </div>
        </div>

        {/* Life Highlights */}
        <div className="card">
          <div className="section-header">
            <span className="section-title">Life Highlights</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {milestones.map((m) => (
              <div key={m.id} style={{ fontSize: 12, display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <span style={{ color: 'var(--text-primary)' }}>{m.title}</span>
                <span className="tabular" style={{ color: 'var(--text-muted)', flexShrink: 0 }}>
                  {formatAge(m.ageMonths)}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="bottom-dock">
        <button className="advance-cta" onClick={onNewLife}>
          <RotateCcw size={16} /> Start a New Life
        </button>
      </div>
    </div>
  );
};

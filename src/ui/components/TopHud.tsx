import React from 'react';
import { FolderOpen, Lock, Sliders } from 'lucide-react';
import { formatAge, getCountry } from '../../engine';
import type { EraPack, GameState } from '../../engine';
import { formatMoney } from '../../store/saveStore';
import { Avatar } from './Avatar';

interface TopHudProps {
  state: GameState;
  pack: EraPack;
  onOpenDirector: () => void;
  onOpenSaves: () => void;
  onTapIdentity?: () => void;
}

export const TopHud: React.FC<TopHudProps> = ({
  state,
  pack,
  onOpenDirector,
  onOpenSaves,
  onTapIdentity,
}) => {
  const country = getCountry(pack, state.config.countryId);
  const { player } = state;

  const subtitle = player.job
    ? `${formatAge(player.ageMonths)} · ${player.job.title}`
    : player.retired
      ? `${formatAge(player.ageMonths)} · Retired`
      : `${formatAge(player.ageMonths)} · ${country.flag} ${country.name}`;

  return (
    <header className="top-hud">
      <button
        type="button"
        className="hud-identity"
        onClick={onTapIdentity}
        disabled={!onTapIdentity}
        style={{ cursor: onTapIdentity ? 'pointer' : 'default', opacity: 1 }}
      >
        <Avatar
          ageMonths={player.ageMonths}
          gender={player.gender}
          alive={player.alive}
          happiness={player.stats.happiness}
          stress={player.stats.stress}
          size={40}
        />
        <div style={{ minWidth: 0 }}>
          <div className="hud-name">
            {player.firstName} {player.lastName}
          </div>
          <div className="hud-sub tabular">{subtitle}</div>
        </div>
      </button>

      <div className="hud-right">
        <div className="hud-balance-wrap">
          <div className="hud-balance tabular">{formatMoney(player.finance.cash, country)}</div>
          <div className="hud-ap tabular">
            {player.actionPoints}/{player.maxActionPoints} AP
          </div>
        </div>

        <button
          className="icon-btn"
          onClick={onOpenSaves}
          title="Save Slots & New Life"
          aria-label="Save Slots"
        >
          <FolderOpen size={16} />
        </button>
        <button
          className={`icon-btn ${state.config.ironman ? '' : 'icon-btn-violet'}`}
          onClick={onOpenDirector}
          title={state.config.ironman ? 'Director Mode (Ironman Locked — Speed Only)' : 'Open Director Mode'}
          aria-label="Director Mode"
        >
          {state.config.ironman ? <Lock size={15} /> : <Sliders size={16} />}
        </button>
      </div>
    </header>
  );
};

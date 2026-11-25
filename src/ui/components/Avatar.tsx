import React from 'react';
import { getLifeStage } from '../../engine';
import type { Gender, LifeStage } from '../../engine';

interface AvatarProps {
  ageMonths: number;
  gender: Gender;
  alive: boolean;
  happiness?: number;
  stress?: number;
  size?: number;
}

const STAGE_VISUALS: Record<LifeStage, { emoji: Record<Gender, string>; bg: string }> = {
  infancy: {
    emoji: { male: '👶', female: '👶', non_binary: '👶' },
    bg: '#1D2939',
  },
  childhood: {
    emoji: { male: '👦', female: '👧', non_binary: '🧒' },
    bg: '#1E2A3A',
  },
  adolescence: {
    emoji: { male: '🧑', female: '👩', non_binary: '🧑' },
    bg: '#1C2836',
  },
  young_adult: {
    emoji: { male: '👨', female: '👩', non_binary: '🧑' },
    bg: '#192634',
  },
  prime_adult: {
    emoji: { male: '🧔', female: '👩', non_binary: '🧑' },
    bg: '#1C2532',
  },
  mature_adult: {
    emoji: { male: '👨‍🦳', female: '👩‍🦳', non_binary: '🧑‍🦳' },
    bg: '#212733',
  },
  elder: {
    emoji: { male: '👴', female: '👵', non_binary: '🧓' },
    bg: '#23262F',
  },
};

export const Avatar: React.FC<AvatarProps> = ({
  ageMonths,
  gender,
  alive,
  happiness = 70,
  stress = 20,
  size = 40,
}) => {
  const stage = getLifeStage(ageMonths);
  const visual = STAGE_VISUALS[stage];
  const glyph = !alive ? '🪦' : visual.emoji[gender];

  let ringColor = '#323E52';
  if (!alive) {
    ringColor = '#F43F5E';
  } else if (stress >= 75) {
    ringColor = '#F43F5E';
  } else if (happiness >= 70) {
    ringColor = '#10B981';
  } else if (happiness >= 40) {
    ringColor = '#F59E0B';
  } else {
    ringColor = '#F43F5E';
  }

  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: Math.round(size * 0.3),
        backgroundColor: visual.bg,
        border: `2px solid ${ringColor}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: Math.round(size * 0.54),
        lineHeight: 1,
        flexShrink: 0,
        userSelect: 'none',
      }}
      title={`${stage.replace('_', ' ')}`}
    >
      {glyph}
    </div>
  );
};

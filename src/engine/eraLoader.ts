import eraJson from '../../data/eras/modern_2020s/era.json';
import countriesJson from '../../data/eras/modern_2020s/countries.json';
import educationJson from '../../data/eras/modern_2020s/education.json';
import careersJson from '../../data/eras/modern_2020s/careers.json';
import assetsJson from '../../data/eras/modern_2020s/assets.json';
import conditionsJson from '../../data/eras/modern_2020s/conditions.json';
import eventsJson from '../../data/eras/modern_2020s/events.json';
import namesJson from '../../data/eras/modern_2020s/names.json';
import type {
  AssetsCatalog,
  CareerTrackDef,
  ConditionsCatalog,
  CountryDef,
  CountryNamePool,
  EducationProgramDef,
  EraMeta,
  EraPack,
  GameEvent,
} from './types';

const MODERN_2020S_PACK: EraPack = {
  meta: eraJson as EraMeta,
  countries: countriesJson as CountryDef[],
  education: educationJson as EducationProgramDef[],
  careers: careersJson as CareerTrackDef[],
  assets: assetsJson as AssetsCatalog,
  conditions: conditionsJson as ConditionsCatalog,
  events: eventsJson as GameEvent[],
  names: namesJson as Record<string, CountryNamePool>,
};

const ERA_REGISTRY: Record<string, EraPack> = {
  modern_2020s: MODERN_2020S_PACK,
};

export function loadEraPack(eraId: string = 'modern_2020s'): EraPack {
  const pack = ERA_REGISTRY[eraId];
  if (!pack) {
    throw new Error(`Unknown Era Pack: "${eraId}". Available: ${Object.keys(ERA_REGISTRY).join(', ')}`);
  }
  return pack;
}

export function listEraPacks(): EraMeta[] {
  return Object.values(ERA_REGISTRY).map((p) => p.meta);
}

export function getCountry(pack: EraPack, countryId: string): CountryDef {
  const found = pack.countries.find((c) => c.id === countryId);
  if (!found) {
    throw new Error(`Country "${countryId}" not found in era "${pack.meta.id}"`);
  }
  return found;
}

import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import {
  advanceTurn,
  buildDeathSummary,
  createNewLife,
  isChoiceAvailable,
  loadEraPack,
  resolveEventChoice,
} from '../../engine';
import { SetupScreen } from '../screens/SetupScreen';
import { LifeTab } from '../screens/LifeTab';
import { ScheduleTab } from '../screens/ScheduleTab';
import { CareerTab } from '../screens/CareerTab';
import { PeopleTab } from '../screens/PeopleTab';
import { AssetsTab } from '../screens/AssetsTab';
import { DeathSummaryScreen } from '../screens/DeathSummaryScreen';
import { TopHud } from '../components/TopHud';
import { StatStrip } from '../components/StatStrip';
import { EventModal } from '../components/EventModal';
import { DirectorDrawer } from '../components/DirectorDrawer';
import { SaveSlotsModal } from '../components/SaveSlotsModal';

const noop = () => {};

describe('UI Screen & Component Render Verification', () => {
  const pack = loadEraPack('modern_2020s');

  it('renders SetupScreen cleanly without NaN or errors', () => {
    const html = renderToStaticMarkup(
      <SetupScreen pack={pack} hasSaves={true} onStartLife={noop} onOpenSaves={noop} />,
    );
    expect(html).toContain('Chronicle');
    expect(html).toContain('Begin New Life');
    expect(html).toContain('Ironman Mode');
    expect(html).not.toContain('NaN');
  });

  it('renders all active gameplay tabs, HUD, and modals across infancy and adulthood', () => {
    let state = createNewLife(
      {
        seed: 777,
        firstName: 'Aarav',
        lastName: 'Sharma',
        countryId: 'usa',
        granularity: 'YEARLY',
        familyWealthTier: 'upper_middle',
      },
      pack,
    );

    // Infant schedule should not show false Overtime badge when requiredHours is 0
    const infantScheduleHtml = renderToStaticMarkup(
      <ScheduleTab
        state={state}
        pack={pack}
        onUpdateSchedule={noop}
        onApplyPreset={noop}
        onChangeFocusSkill={noop}
        onAction={noop}
      />,
    );
    expect(infantScheduleHtml).not.toContain('Overtime');
    expect(infantScheduleHtml).toContain('168 / 168h');

    // Advance 22 years into adulthood
    for (let i = 0; i < 22; i++) {
      state = advanceTurn(state, pack);
      if (state.pendingEvent) {
        const validChoice =
          state.pendingEvent.choices.find((c) => isChoiceAvailable(state, c)) ??
          state.pendingEvent.choices[0]!;
        state = resolveEventChoice(state, validChoice.id, pack);
      }
    }

    // Rent a purchasable housing option to verify rent-to-own Buy button renders
    const purchasable = pack.assets.housing.find((h) => h.purchasePrice > 0)!;
    const rentedState = {
      ...state,
      player: {
        ...state.player,
        finance: {
          ...state.player.finance,
          housingId: purchasable.id,
          ownsHousing: false,
        },
      },
    };

    const fragments = [
      renderToStaticMarkup(
        <TopHud state={rentedState} pack={pack} onOpenDirector={noop} onOpenSaves={noop} />,
      ),
      renderToStaticMarkup(<StatStrip state={rentedState} deltas={{ happiness: 3, stress: -2 }} />),
      renderToStaticMarkup(<LifeTab state={rentedState} pack={pack} onAction={noop} />),
      renderToStaticMarkup(
        <ScheduleTab
          state={rentedState}
          pack={pack}
          onUpdateSchedule={noop}
          onApplyPreset={noop}
          onChangeFocusSkill={noop}
          onAction={noop}
        />,
      ),
      renderToStaticMarkup(
        <CareerTab state={rentedState} pack={pack} onAction={noop} onChangeFocusSkill={noop} />,
      ),
      renderToStaticMarkup(<PeopleTab state={rentedState} onAction={noop} />),
      renderToStaticMarkup(<AssetsTab state={rentedState} pack={pack} onAction={noop} />),
      renderToStaticMarkup(
        <EventModal
          state={rentedState}
          event={pack.events[0]!}
          pack={pack}
          onSelectChoice={noop}
        />,
      ),
      renderToStaticMarkup(
        <DirectorDrawer state={rentedState} pack={pack} onMutate={noop} onClose={noop} />,
      ),
      renderToStaticMarkup(
        <SaveSlotsModal
          slots={{ 1: { slotId: 1, updatedAt: Date.now(), state: rentedState } }}
          activeSlotId={1}
          currentState={rentedState}
          pack={pack}
          onLoadSlot={noop}
          onSaveToSlot={noop}
          onDeleteSlot={noop}
          onNewLife={noop}
          onClose={noop}
        />,
      ),
    ];

    for (const html of fragments) {
      expect(html.length).toBeGreaterThan(100);
      expect(html).not.toContain('NaN');
      expect(html).not.toContain('undefined');
    }

    // Verify AssetsTab shows Buy option even while currently renting that property
    const assetsHtml = fragments[6]!;
    expect(assetsHtml).toContain('Living Here');
    expect(assetsHtml).toContain('Buy (20%');
  });

  it('renders Ironman DirectorDrawer lock state and DeathSummaryScreen cleanly', () => {
    const ironmanState = createNewLife({ seed: 888, ironman: true }, pack);
    const drawerHtml = renderToStaticMarkup(
      <DirectorDrawer state={ironmanState} pack={pack} onMutate={noop} onClose={noop} />,
    );
    expect(drawerHtml).toContain('Ironman Mode is active');
    expect(drawerHtml).toContain('Turn Speed');

    const summary = buildDeathSummary(ironmanState, 'Old age', pack);
    const deadState = {
      ...ironmanState,
      player: { ...ironmanState.player, alive: false },
      deathSummary: summary,
    };
    const deathHtml = renderToStaticMarkup(
      <DeathSummaryScreen state={deadState} pack={pack} onNewLife={noop} />,
    );
    expect(deathHtml).toContain('End of Chronicle');
    expect(deathHtml).toContain(String(summary.legacyScore));
    expect(deathHtml).not.toContain('NaN');
  });
});

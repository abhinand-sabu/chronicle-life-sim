import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Briefcase,
  CalendarClock,
  ChevronLeft,
  Plus,
  Users,
  Wallet,
  X,
} from 'lucide-react';
import {
  advanceTurn,
  applyDirectorMutation,
  applySchedulePreset,
  createNewLife,
  loadEraPack,
  performPlayerAction,
  resolveEventChoice,
  updateSchedule,
} from '../engine';
import type {
  CreateLifeOptions,
  DirectorMutation,
  GameState,
  PlayerAction,
  SchedulePresetId,
  SkillId,
  WeeklySchedule,
} from '../engine';
import {
  computeStatDeltas,
  deleteSaveSlot,
  formatGranularityShort,
  getLastActiveSlotId,
  loadAllSaveSlots,
  pickSlotForNewLife,
  saveToSlot,
} from '../store/saveStore';
import type { SaveSlot, StatDeltas } from '../store/saveStore';
import { TopHud } from './components/TopHud';
import { StatStrip } from './components/StatStrip';
import { EventModal } from './components/EventModal';
import { DirectorDrawer } from './components/DirectorDrawer';
import { SaveSlotsModal } from './components/SaveSlotsModal';
import { SetupScreen } from './screens/SetupScreen';
import { LifeTab } from './screens/LifeTab';
import { ScheduleTab } from './screens/ScheduleTab';
import { CareerTab } from './screens/CareerTab';
import { PeopleTab } from './screens/PeopleTab';
import { AssetsTab } from './screens/AssetsTab';
import { DeathSummaryScreen } from './screens/DeathSummaryScreen';

type ActiveTab = 'life' | 'schedule' | 'career' | 'people' | 'assets';

const SUBSCREEN_TITLES: Record<Exclude<ActiveTab, 'life'>, string> = {
  career: 'Career & Education',
  assets: 'Assets & Housing',
  people: 'Relationships',
  schedule: 'Weekly Schedule',
};

interface ToastState {
  message: string;
  tone: 'success' | 'error';
}

export const App: React.FC = () => {
  const pack = useMemo(() => loadEraPack('modern_2020s'), []);
  const [slots, setSlots] = useState<Record<number, SaveSlot>>(() => loadAllSaveSlots());
  const [activeSlotId, setActiveSlotId] = useState<number>(() => getLastActiveSlotId());

  const [gameState, setGameState] = useState<GameState | null>(() => {
    const initialSlots = loadAllSaveSlots();
    const lastId = getLastActiveSlotId();
    return initialSlots[lastId]?.state ?? null;
  });

  const [activeTab, setActiveTab] = useState<ActiveTab>('life');
  const [statDeltas, setStatDeltas] = useState<StatDeltas>({});
  const [directorOpen, setDirectorOpen] = useState(false);
  const [savesOpen, setSavesOpen] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);
  const toastTimer = useRef<number | null>(null);

  const showToast = (message: string, tone: 'success' | 'error' = 'success') => {
    setToast({ message, tone });
    if (toastTimer.current) window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2600);
  };

  useEffect(() => {
    return () => {
      if (toastTimer.current) window.clearTimeout(toastTimer.current);
    };
  }, []);

  useEffect(() => {
    if (gameState) {
      setSlots(saveToSlot(activeSlotId, gameState));
    }
  }, [gameState, activeSlotId]);

  const applyNextState = (next: GameState, prev?: GameState | null) => {
    if (prev) {
      setStatDeltas(computeStatDeltas(prev.player.stats, next.player.stats));
    }
    setGameState(next);
  };

  const handleStartLife = (options: CreateLifeOptions) => {
    const targetSlot = pickSlotForNewLife(slots, activeSlotId);
    const fresh = createNewLife(options, pack);
    setActiveSlotId(targetSlot);
    setStatDeltas({});
    setActiveTab('life');
    setGameState(fresh);
    showToast(`Welcome to the world, ${fresh.player.firstName}! (Slot ${targetSlot})`);
  };

  const handleAdvanceTurn = () => {
    if (!gameState || !gameState.player.alive) return;
    if (gameState.pendingEvent) return;
    const next = advanceTurn(gameState, pack);
    applyNextState(next, gameState);
    setActiveTab('life');
  };

  const handleResolveEvent = (choiceId: string) => {
    if (!gameState) return;
    const next = resolveEventChoice(gameState, choiceId, pack);
    applyNextState(next, gameState);
  };

  const handlePlayerAction = (action: PlayerAction) => {
    if (!gameState) return;
    const res = performPlayerAction(gameState, action, pack);
    if (res.success) {
      applyNextState(res.state, gameState);
      showToast(res.message, 'success');
    } else {
      showToast(res.message, 'error');
    }
  };

  const handleUpdateSchedule = (schedule: WeeklySchedule) => {
    if (!gameState) return;
    setGameState(updateSchedule(gameState, schedule));
  };

  const handleApplyPreset = (preset: SchedulePresetId) => {
    if (!gameState) return;
    setGameState(applySchedulePreset(gameState, preset));
    showToast(`Applied ${preset.replace('_', ' ')} schedule.`);
  };

  const handleChangeFocusSkill = (skill: SkillId) => {
    if (!gameState) return;
    setGameState({
      ...gameState,
      player: { ...gameState.player, focusSkill: skill },
    });
    showToast(`Focus skill set to ${skill.replace('_', ' ')}.`);
  };

  const handleDirectorMutation = (mutation: DirectorMutation) => {
    if (!gameState) return;
    const res = applyDirectorMutation(gameState, mutation, pack);
    if (res.applied) {
      applyNextState(res.state, gameState);
      showToast(res.message, 'success');
    } else {
      showToast(res.message, 'error');
    }
  };

  const handleLoadSlot = (slotId: number) => {
    const target = slots[slotId];
    if (!target) return;
    setActiveSlotId(slotId);
    setStatDeltas({});
    setActiveTab('life');
    setGameState(target.state);
    setSavesOpen(false);
    showToast(`Loaded ${target.state.player.firstName}'s life (Slot ${slotId}).`);
  };

  const handleSaveToSlot = (slotId: number) => {
    if (!gameState) return;
    setActiveSlotId(slotId);
    setSlots(saveToSlot(slotId, gameState));
    showToast(`Saved to Slot ${slotId}.`);
  };

  const handleDeleteSlot = (slotId: number) => {
    const updated = deleteSaveSlot(slotId);
    setSlots(updated);
    showToast(`Cleared Slot ${slotId}.`);
  };

  const handleNewLifeFlow = () => {
    setSavesOpen(false);
    setDirectorOpen(false);
    setGameState(null);
  };

  const toggleSubTab = (tab: Exclude<ActiveTab, 'life'>) => {
    setActiveTab((curr) => (curr === tab ? 'life' : tab));
  };

  const hasSaves = Object.keys(slots).length > 0;

  return (
    <div className="app-viewport">
      <div className="phone-frame">
        {/* Toast Banner */}
        {toast && (
          <div className={`toast-banner ${toast.tone === 'success' ? 'toast-success' : 'toast-error'}`}>
            <span>{toast.message}</span>
            <button onClick={() => setToast(null)} aria-label="Dismiss notification">
              <X size={14} />
            </button>
          </div>
        )}

        {/* Setup Screen */}
        {!gameState ? (
          <SetupScreen
            pack={pack}
            hasSaves={hasSaves}
            onStartLife={handleStartLife}
            onOpenSaves={() => setSavesOpen(true)}
          />
        ) : !gameState.player.alive && gameState.deathSummary ? (
          /* End of Life Summary */
          <DeathSummaryScreen
            state={gameState}
            pack={pack}
            onNewLife={handleNewLifeFlow}
          />
        ) : (
          /* Active 3-Zone BitLife-Inspired Game Shell */
          <>
            <TopHud
              state={gameState}
              pack={pack}
              onOpenDirector={() => setDirectorOpen(true)}
              onOpenSaves={() => setSavesOpen(true)}
              onTapIdentity={() => setActiveTab('life')}
            />

            {activeTab !== 'life' && (
              <div className="screen-bar">
                <button className="back-pill" onClick={() => setActiveTab('life')}>
                  <ChevronLeft size={15} /> Life
                </button>
                <span className="screen-bar-title">{SUBSCREEN_TITLES[activeTab]}</span>
                <span style={{ width: 58 }} aria-hidden="true" />
              </div>
            )}

            <main className="main-scroll">
              {activeTab === 'life' && (
                <LifeTab state={gameState} pack={pack} onAction={handlePlayerAction} />
              )}
              {activeTab === 'schedule' && (
                <ScheduleTab
                  state={gameState}
                  pack={pack}
                  onUpdateSchedule={handleUpdateSchedule}
                  onApplyPreset={handleApplyPreset}
                  onChangeFocusSkill={handleChangeFocusSkill}
                  onAction={handlePlayerAction}
                />
              )}
              {activeTab === 'career' && (
                <CareerTab
                  state={gameState}
                  pack={pack}
                  onAction={handlePlayerAction}
                  onChangeFocusSkill={handleChangeFocusSkill}
                />
              )}
              {activeTab === 'people' && (
                <PeopleTab state={gameState} pack={pack} onAction={handlePlayerAction} />
              )}
              {activeTab === 'assets' && (
                <AssetsTab state={gameState} pack={pack} onAction={handlePlayerAction} />
              )}
            </main>

            {/* Zone 3: Stat Bars + BitLife-Style Center +Age Action Dock */}
            <div className="bottom-shell">
              <StatStrip state={gameState} deltas={statDeltas} />

              <nav className="action-dock" aria-label="Main Navigation">
                <button
                  className={`dock-tab ${activeTab === 'career' ? 'active' : ''}`}
                  onClick={() => toggleSubTab('career')}
                >
                  <Briefcase size={19} />
                  <span>Career</span>
                </button>

                <button
                  className={`dock-tab ${activeTab === 'assets' ? 'active' : ''}`}
                  onClick={() => toggleSubTab('assets')}
                >
                  <Wallet size={19} />
                  <span>Assets</span>
                </button>

                <button
                  className={`age-btn ${gameState.pendingEvent ? 'age-btn-pending' : ''}`}
                  onClick={handleAdvanceTurn}
                  disabled={Boolean(gameState.pendingEvent)}
                >
                  {gameState.pendingEvent ? (
                    <>
                      <span>Event</span>
                      <span className="age-btn-sub">Choose</span>
                    </>
                  ) : (
                    <>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}>
                        <Plus size={15} strokeWidth={3} /> Age
                      </span>
                      <span className="age-btn-sub tabular">
                        {formatGranularityShort(gameState.config.granularity)}
                      </span>
                    </>
                  )}
                </button>

                <button
                  className={`dock-tab ${activeTab === 'people' ? 'active' : ''}`}
                  onClick={() => toggleSubTab('people')}
                >
                  <Users size={19} />
                  <span>People</span>
                </button>

                <button
                  className={`dock-tab ${activeTab === 'schedule' ? 'active' : ''}`}
                  onClick={() => toggleSubTab('schedule')}
                >
                  <CalendarClock size={19} />
                  <span>Schedule</span>
                </button>
              </nav>
            </div>

            {/* Pending Event Modal */}
            {gameState.pendingEvent && (
              <EventModal
                state={gameState}
                event={gameState.pendingEvent}
                pack={pack}
                onSelectChoice={handleResolveEvent}
              />
            )}

            {/* Director Mode Drawer */}
            {directorOpen && (
              <DirectorDrawer
                state={gameState}
                pack={pack}
                onMutate={handleDirectorMutation}
                onClose={() => setDirectorOpen(false)}
              />
            )}
          </>
        )}

        {/* Save Slots Modal */}
        {savesOpen && (
          <SaveSlotsModal
            slots={slots}
            activeSlotId={activeSlotId}
            currentState={gameState}
            pack={pack}
            onLoadSlot={handleLoadSlot}
            onSaveToSlot={handleSaveToSlot}
            onDeleteSlot={handleDeleteSlot}
            onNewLife={handleNewLifeFlow}
            onClose={() => setSavesOpen(false)}
          />
        )}
      </div>
    </div>
  );
};

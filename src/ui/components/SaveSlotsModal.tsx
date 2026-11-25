import React from 'react';
import { Lock, Plus, Save, Trash2, Upload, X } from 'lucide-react';
import { calculateNetWorth, formatAge, getCountry } from '../../engine';
import type { EraPack, GameState } from '../../engine';
import { formatMoney, MAX_SAVE_SLOTS } from '../../store/saveStore';
import type { SaveSlot } from '../../store/saveStore';

interface SaveSlotsModalProps {
  slots: Record<number, SaveSlot>;
  activeSlotId: number;
  currentState: GameState | null;
  pack: EraPack;
  onLoadSlot: (slotId: number) => void;
  onSaveToSlot: (slotId: number) => void;
  onDeleteSlot: (slotId: number) => void;
  onNewLife: () => void;
  onClose: () => void;
}

export const SaveSlotsModal: React.FC<SaveSlotsModalProps> = ({
  slots,
  activeSlotId,
  currentState,
  pack,
  onLoadSlot,
  onSaveToSlot,
  onDeleteSlot,
  onNewLife,
  onClose,
}) => {
  const slotNumbers = Array.from({ length: MAX_SAVE_SLOTS }, (_, i) => i + 1);

  return (
    <div className="overlay-backdrop" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
          <h2 style={{ fontSize: 18, fontWeight: 800 }}>Save Slots</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close Saves">
            <X size={16} />
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 14 }}>
          {slotNumbers.map((id) => {
            const slot = slots[id];
            const isActive = id === activeSlotId;

            if (!slot) {
              return (
                <div
                  key={id}
                  className="card"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    borderStyle: 'dashed',
                  }}
                >
                  <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>Slot {id} — Empty</span>
                  {currentState && (
                    <button className="btn btn-sm" onClick={() => onSaveToSlot(id)}>
                      <Save size={13} /> Save Here
                    </button>
                  )}
                </div>
              );
            }

            const s = slot.state;
            const country = getCountry(pack, s.config.countryId);
            const nw = calculateNetWorth(s, pack);

            return (
              <div
                key={id}
                className="card"
                style={{
                  borderColor: isActive ? 'var(--accent-emerald)' : 'var(--border-subtle)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <strong style={{ fontSize: 14 }}>
                        {s.player.firstName} {s.player.lastName}
                      </strong>
                      {isActive && <span className="chip chip-emerald">Active</span>}
                      {s.config.ironman && (
                        <span className="chip chip-amber">
                          <Lock size={9} /> Ironman
                        </span>
                      )}
                      {!s.player.alive && <span className="chip chip-rose">Deceased</span>}
                    </div>
                    <div className="tabular" style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
                      Slot {id} • {formatAge(s.player.ageMonths)} • {country.name} • NW {formatMoney(nw, country)}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 6 }}>
                  <button className="btn btn-sm btn-sky" style={{ flex: 1 }} onClick={() => onLoadSlot(id)}>
                    <Upload size={12} /> Load
                  </button>
                  {currentState && (
                    <button className="btn btn-sm" style={{ flex: 1 }} onClick={() => onSaveToSlot(id)}>
                      <Save size={12} /> Overwrite
                    </button>
                  )}
                  <button
                    className="btn btn-sm btn-rose"
                    onClick={() => onDeleteSlot(id)}
                    aria-label={`Delete slot ${id}`}
                  >
                    <Trash2 size={12} />
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        <button className="btn btn-primary" style={{ width: '100%' }} onClick={onNewLife}>
          <Plus size={15} /> Start a Brand New Life
        </button>
      </div>
    </div>
  );
};

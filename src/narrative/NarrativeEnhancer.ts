import type { GameEvent, GameState } from '../engine/types';

export interface EventNarrativeContext {
  state: GameState;
  event: GameEvent;
}

export interface EnhancedEventText {
  title: string;
  body: string;
}

/**
 * Presentation-layer narrative interface.
 * Default implementation is 100% offline & deterministic.
 * Optional BYO AI enhancers can implement this interface later without touching engine logic.
 */
export interface NarrativeEnhancer {
  readonly id: string;
  enhanceEventText(context: EventNarrativeContext): Promise<EnhancedEventText>;
}

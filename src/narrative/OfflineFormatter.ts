import type { EventNarrativeContext, EnhancedEventText, NarrativeEnhancer } from './NarrativeEnhancer';

/**
 * Default 100% offline deterministic formatter.
 * Interpolates {{player.firstName}}, {{job.title}}, {{partner.firstName}}, etc.
 */
export class OfflineNarrativeEnhancer implements NarrativeEnhancer {
  readonly id = 'offline_default';

  async enhanceEventText(context: EventNarrativeContext): Promise<EnhancedEventText> {
    return {
      title: formatTemplate(context.event.title, context),
      body: formatTemplate(context.event.body, context),
    };
  }
}

export function formatTemplate(template: string, context: EventNarrativeContext): string {
  const { player, npcs } = context.state;
  const partner = npcs.find((n) => n.alive && (n.role === 'partner' || n.role === 'spouse'));
  const replacements: Record<string, string> = {
    'player.firstName': player.firstName,
    'player.lastName': player.lastName,
    'job.title': player.job?.title ?? 'your work',
    'partner.firstName': partner?.firstName ?? 'your partner',
  };

  return template.replace(/\{\{([^}]+)\}\}/g, (_match, rawKey: string) => {
    const key = rawKey.trim();
    return replacements[key] ?? `{{${key}}}`;
  });
}

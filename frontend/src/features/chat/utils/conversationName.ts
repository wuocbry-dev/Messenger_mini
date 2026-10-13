import type { Conversation } from '../../../shared/types';

/** Name shown for a conversation: its title, the other person, or the members' names. */
export function getConversationTitle(conv: Conversation, currentUserId?: number): string {
  if (conv.title?.trim()) return conv.title.trim();
  if (conv.directWithUser) return conv.directWithUser.displayName;

  const others = (conv.members ?? []).filter((m) => m.userId !== currentUserId && !m.leftAt);
  if (others.length > 0) {
    const names = others.slice(0, 3).map((m) => m.displayName);
    const extra = others.length - names.length;
    return extra > 0 ? `${names.join(', ')} +${extra}` : names.join(', ');
  }
  return conv.type === 'GROUP' ? 'Group chat' : `Conversation ${conv.id}`;
}

export function memberCount(conv: Conversation): number {
  return (conv.members ?? []).filter((m) => !m.leftAt).length;
}

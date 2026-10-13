import { parseUtc } from './datetime';

/** "Active 5 minutes ago"-style text for an offline user. */
export function formatLastSeen(lastSeenAt: string | undefined | null, nowMs: number = Date.now()): string {
  const t = parseUtc(lastSeenAt);
  if (t == null) return 'Offline';
  const diffMin = Math.max(0, Math.floor((nowMs - t) / 60_000));
  if (diffMin < 1) return 'Active just now';
  if (diffMin < 60) return `Active ${diffMin} ${diffMin === 1 ? 'minute' : 'minutes'} ago`;
  const hours = Math.floor(diffMin / 60);
  if (hours < 24) return `Active ${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return 'Active yesterday';
  if (days < 7) return `Active ${days} days ago`;
  return `Last seen ${new Date(t).toLocaleDateString([], { month: 'short', day: 'numeric' })}`;
}

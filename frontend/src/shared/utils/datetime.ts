/**
 * Backend timestamps are UTC LocalDateTime strings without a zone suffix
 * (e.g. "2026-10-13T09:00:00.123"). Parse them as UTC so they display in the
 * viewer's local time.
 */
export function parseUtc(iso?: string | null): number | null {
  if (!iso) return null;
  const t = Date.parse(/[zZ]|[+-]\d\d:?\d\d$/.test(iso) ? iso : `${iso}Z`);
  return Number.isNaN(t) ? null : t;
}

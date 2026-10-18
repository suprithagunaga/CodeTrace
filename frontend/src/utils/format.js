/** Formatting helpers. Scores from the API are fractions in [0, 1]. */

export function formatPercent(fraction, digits = 1) {
  if (fraction === null || fraction === undefined || Number.isNaN(Number(fraction))) return '—';
  return `${(Number(fraction) * 100).toFixed(digits)}%`;
}

export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

export function formatDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export function formatNumber(n) {
  if (n === null || n === undefined) return '—';
  return Number(n).toLocaleString();
}

export function shortId(id) {
  return id ? String(id).slice(0, 8) : '';
}

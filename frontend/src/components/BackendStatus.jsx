import { useEffect, useState } from 'react';
import { getHealth } from '../api/client';

/** Polls GET /api/health and shows API + database connectivity. */
export function useBackendHealth(intervalMs = 30000) {
  const [state, setState] = useState({ status: 'checking', health: null, error: null });
  const [nonce, setNonce] = useState(0);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const check = () => {
      getHealth(controller.signal)
        .then((health) => active && setState({ status: health.database?.status === 'UP' ? 'up' : 'degraded', health, error: null }))
        .catch((error) => active && error?.name !== 'AbortError' && setState({ status: 'down', health: null, error }));
    };
    check();
    const t = setInterval(check, intervalMs);
    return () => { active = false; controller.abort(); clearInterval(t); };
  }, [intervalMs, nonce]);
  return { ...state, recheck: () => setNonce((n) => n + 1) };
}

const LABELS = { checking: 'Checking backend…', up: 'Backend connected', degraded: 'Database unavailable', down: 'Backend offline' };

export function BackendStatusPill({ status }) {
  return (
    <span className={`status-pill status-${status}`} role="status" aria-live="polite">
      <span className="status-dot" aria-hidden="true" />
      {LABELS[status]}
    </span>
  );
}

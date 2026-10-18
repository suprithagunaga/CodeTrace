import { AlertTriangle, Inbox, Loader2, RefreshCw, WifiOff } from 'lucide-react';

export function LoadingState({ label = 'Loading…' }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <Loader2 className="spin" size={22} aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="state state-empty">
      <Inbox size={28} aria-hidden="true" />
      <strong>{title}</strong>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

/** Shows the server's message (and per-field details) or a connection error, with a retry button. */
export function ErrorState({ error, onRetry, title }) {
  const network = error?.network;
  const Icon = network ? WifiOff : AlertTriangle;
  return (
    <div className="state state-error" role="alert">
      <Icon size={26} aria-hidden="true" />
      <strong>{title || (network ? 'Backend unavailable' : 'Something went wrong')}</strong>
      <p>{error?.message || 'Unexpected error.'}</p>
      {error?.details?.length > 0 && (
        <ul className="error-details">
          {error.details.map((d, i) => (
            <li key={i}><code>{d.field}</code>: {d.message}</li>
          ))}
        </ul>
      )}
      {onRetry && (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          <RefreshCw size={16} aria-hidden="true" /> Retry
        </button>
      )}
    </div>
  );
}

export function InlineError({ error }) {
  if (!error) return null;
  return (
    <div className="alert alert-error" role="alert">
      <AlertTriangle size={18} aria-hidden="true" />
      <div>
        <strong>{error.message}</strong>
        {error.details?.length > 0 && (
          <ul className="error-details">
            {error.details.map((d, i) => (
              <li key={i}><code>{d.field}</code>: {d.message}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

import { SCORE_BANDS, scoreBand } from '../utils/score';
import { formatPercent } from '../utils/format';

export function ScoreBadge({ value, digits = 1 }) {
  const band = scoreBand(value);
  return (
    <span className={`score-badge score-${band.key}`} title={band.label}>
      {formatPercent(value, digits)}
      <span className="sr-only"> ({band.label})</span>
    </span>
  );
}

export function ScoreBar({ value }) {
  const band = scoreBand(value);
  const pct = Math.max(0, Math.min(100, (Number(value) || 0) * 100));
  return (
    <span className="score-bar" aria-hidden="true">
      <span className={`score-bar-fill fill-${band.key}`} style={{ width: `${pct}%` }} />
    </span>
  );
}

export function ScoreLegend() {
  return (
    <div className="legend" aria-label="Score legend">
      <span className="legend-title">Overall score bands</span>
      <ul>
        {SCORE_BANDS.map((b) => (
          <li key={b.key}>
            <span className={`legend-swatch score-${b.key}`} aria-hidden="true" />
            <span>
              <strong>{b.label}</strong> {Math.round(b.min * 100)}–{Math.min(100, Math.round(b.max * 100))}%
            </span>
          </li>
        ))}
      </ul>
      <p className="legend-note">
        Bands describe measured overlap only. A high score is a reason to review code in context, not proof of plagiarism.
      </p>
    </div>
  );
}

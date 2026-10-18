import { useCallback, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AlertTriangle, Info, Link2, Link2Off } from 'lucide-react';
import { getComparison } from '../api/client';
import { useApi } from '../hooks/useApi';
import { PageHeader } from '../components/PageHeader';
import { ErrorState, LoadingState } from '../components/States';
import { ScoreBadge } from '../components/ScoreBadge';
import { Disclaimer } from '../components/Disclaimer';
import { CodePane } from '../components/CodeViewer';
import { formatPercent } from '../utils/format';

export default function ComparisonView() {
  const { id, comparisonId } = useParams();
  const detail = useApi((signal) => getComparison(id, comparisonId, signal), [id, comparisonId]);
  const [mode, setMode] = useState('original');
  const [sync, setSync] = useState(true);
  const [activeRegion, setActiveRegion] = useState(null);
  const leftRef = useRef(null);
  const rightRef = useRef(null);
  const syncing = useRef(false);

  const d = detail.data;
  const rangesA = useMemo(() => (d?.regions ?? []).map((r, i) => ({ start: r.fileAStartOffset, end: r.fileAEndOffset, index: i })), [d]);
  const rangesB = useMemo(() => (d?.regions ?? []).map((r, i) => ({ start: r.fileBStartOffset, end: r.fileBEndOffset, index: i })), [d]);
  const linesA = useMemo(() => (d?.regions ?? []).map((r, i) => ({ startLine: r.fileAStartLine, endLine: r.fileAEndLine, index: i })), [d]);
  const linesB = useMemo(() => (d?.regions ?? []).map((r, i) => ({ startLine: r.fileBStartLine, endLine: r.fileBEndLine, index: i })), [d]);

  const mirror = useCallback((from, to) => {
    if (!sync || syncing.current || !from || !to) return;
    syncing.current = true;
    const max = from.scrollHeight - from.clientHeight;
    const ratio = max > 0 ? from.scrollTop / max : 0;
    to.scrollTop = ratio * (to.scrollHeight - to.clientHeight);
    to.scrollLeft = from.scrollLeft;
    requestAnimationFrame(() => { syncing.current = false; });
  }, [sync]);

  const scrollToLine = (pane, line) => {
    const el = pane?.querySelector(`[data-line="${line}"]`);
    if (pane && el) pane.scrollTop = Math.max(0, el.offsetTop - 48);
  };

  const focusRegion = (index) => {
    const r = d.regions[index];
    setActiveRegion(index);
    syncing.current = true;
    scrollToLine(leftRef.current, r.fileAStartLine);
    scrollToLine(rightRef.current, r.fileBStartLine);
    requestAnimationFrame(() => { syncing.current = false; });
  };

  if (detail.loading && !d) return <LoadingState label="Loading comparison…" />;
  if (detail.error) {
    return (
      <>
        <PageHeader title="Code comparison" />
        <ErrorState error={detail.error} onRetry={detail.error.status === 404 ? undefined : detail.reload}
          title={detail.error.status === 404 ? 'Comparison not found' : undefined} />
        <p><Link to={`/analyses/${id}`}>← Back to results</Link></p>
      </>
    );
  }

  const m = d.metadata;
  const cfg = d.configuration;
  const hasRegions = d.regions.length > 0;
  const allWarnings = [...(m.warnings ?? [])];

  return (
    <>
      <PageHeader
        eyebrow={<Link to={`/analyses/${id}`}>← Analysis results</Link>}
        title={<span className="pair-title"><span className="filename">{d.fileA.filename}</span> <span className="muted">↔</span> <span className="filename">{d.fileB.filename}</span></span>}
        actions={<ScoreBadge value={d.overallScore} />}
      />

      <div className="metric-row comparison-metrics">
        <Metric label="Overall" value={formatPercent(d.overallScore)} hint="Mean of Jaccard and Winnowing" />
        <Metric label="Jaccard" value={formatPercent(d.jaccardScore)} hint={`${cfg.jaccardNgram}-token shingle sets`} />
        <Metric label="Winnowing" value={m.winnowingApplicable ? formatPercent(d.winnowingScore) : 'n/a'} hint="Fingerprint set overlap" />
        <Metric label="Shared fingerprints" value={`${d.sharedFingerprintCount}`} hint={`of ${m.fingerprintsA} / ${m.fingerprintsB}`} />
        <Metric label="A found in B" value={formatPercent(m.containmentA)} hint="Directional containment" />
        <Metric label="B found in A" value={formatPercent(m.containmentB)} hint="Directional containment" />
        <Metric label="Matched tokens" value={`${formatPercent(m.coverageA, 0)} / ${formatPercent(m.coverageB, 0)}`} hint="Coverage of A / B" />
      </div>

      <section className="card explanation" aria-labelledby="explain-title">
        <h2 id="explain-title"><Info size={18} aria-hidden="true" /> How this score was computed</h2>
        <p>{d.explanation}</p>
        <p className="muted small">
          Algorithms: Java tokenizer (comments and whitespace ignored) → {cfg.normalizationMode === 'IDENTIFIERS' ? 'identifier normalization' : 'original tokens'}
          {cfg.normalizeNumbers ? ' + numeric literal normalization' : ''} → Jaccard on {cfg.jaccardNgram}-token shingles and
          Winnowing fingerprints (k = {cfg.kgramSize}, w = {cfg.windowSize}). Rule: {cfg.combinedScoreRule}.
        </p>
        {allWarnings.length > 0 && (
          <ul className="warning-list">
            {allWarnings.map((w, i) => <li key={i}><AlertTriangle size={14} aria-hidden="true" /> {w}</li>)}
          </ul>
        )}
      </section>

      <Disclaimer text={d.disclaimer} />

      <section className="card" aria-labelledby="regions-title">
        <div className="card-header wrap">
          <h2 id="regions-title">Matching regions <span className="count-pill">{d.regions.length}</span></h2>
          {d.sourceAvailable && (
            <div className="toolbar">
              <div className="segmented" role="group" aria-label="Code view">
                <button type="button" className={mode === 'original' ? 'active' : ''} aria-pressed={mode === 'original'} onClick={() => setMode('original')}>Original code</button>
                <button type="button" className={mode === 'normalized' ? 'active' : ''} aria-pressed={mode === 'normalized'} onClick={() => setMode('normalized')}>Normalized tokens</button>
              </div>
              <button type="button" className="btn btn-secondary btn-sm" aria-pressed={sync} onClick={() => setSync((s) => !s)}>
                {sync ? <Link2 size={14} aria-hidden="true" /> : <Link2Off size={14} aria-hidden="true" />} {sync ? 'Scroll synced' : 'Scroll independent'}
              </button>
            </div>
          )}
        </div>
        {!hasRegions ? (
          <div className="alert alert-info" role="status">
            <Info size={18} aria-hidden="true" />
            <p>No meaningful matching region was found: the files share no run of at least {cfg.kgramSize + cfg.windowSize - 1} identical normalized tokens.
              Any non-zero score comes from shared vocabulary (Jaccard) rather than copied sequences.</p>
          </div>
        ) : (
          <ol className="region-list">
            {d.regions.map((r, i) => (
              <li key={i}>
                <button type="button" className={`region-chip region-${i % 6}${activeRegion === i ? ' active' : ''}`} onClick={() => focusRegion(i)}
                  disabled={!d.sourceAvailable} aria-label={`Region ${i + 1}: file A lines ${r.fileAStartLine} to ${r.fileAEndLine}, file B lines ${r.fileBStartLine} to ${r.fileBEndLine}, ${r.tokenLength} tokens`}>
                  <span className="region-index">{i + 1}</span>
                  A {r.fileAStartLine}–{r.fileAEndLine} ↔ B {r.fileBStartLine}–{r.fileBEndLine}
                  <span className="muted small"> · {r.tokenLength} tokens</span>
                </button>
              </li>
            ))}
          </ol>
        )}
      </section>

      {d.sourceAvailable ? (
        <div className="code-compare">
          <CodePane ref={leftRef} label="File A" title={`A · ${d.fileA.filename}`} mode={mode} source={d.fileASource}
            normalizedLines={d.fileANormalizedLines} ranges={rangesA} lineRanges={linesA} activeRegion={activeRegion}
            onScroll={() => mirror(leftRef.current, rightRef.current)} onRegionClick={focusRegion} />
          <CodePane ref={rightRef} label="File B" title={`B · ${d.fileB.filename}`} mode={mode} source={d.fileBSource}
            normalizedLines={d.fileBNormalizedLines} ranges={rangesB} lineRanges={linesB} activeRegion={activeRegion}
            onScroll={() => mirror(rightRef.current, leftRef.current)} onRegionClick={focusRegion} />
        </div>
      ) : (
        <div className="alert alert-warn" role="status">
          <AlertTriangle size={18} aria-hidden="true" />
          <p>Source text was not retained for this analysis (server setting <code>RETENTION_STORE_SOURCE=false</code>), so the
            side-by-side view is unavailable. Matching regions above are listed by original line numbers.</p>
        </div>
      )}
    </>
  );
}

function Metric({ label, value, hint }) {
  return (
    <div className="metric card">
      <span className="metric-label">{label}</span>
      <span className="metric-value">{value}</span>
      {hint && <span className="metric-hint">{hint}</span>}
    </div>
  );
}

import { useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { AlertTriangle, ArrowDown, ArrowUp, ArrowUpDown, CheckCircle2, Download, GitCompare, Search, Trash2 } from 'lucide-react';
import { deleteAnalysis, exportAnalysis, getAnalysis } from '../api/client';
import { useApi } from '../hooks/useApi';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState, InlineError, LoadingState } from '../components/States';
import { ScoreBadge, ScoreBar, ScoreLegend } from '../components/ScoreBadge';
import { Disclaimer } from '../components/Disclaimer';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { formatBytes, formatDateTime, formatPercent } from '../utils/format';
import { saveBlob } from '../utils/download';

const COLUMNS = [
  { key: 'pair', label: 'File pair', sortable: false },
  { key: 'overallScore', label: 'Overall', sortable: true },
  { key: 'jaccardScore', label: 'Jaccard', sortable: true },
  { key: 'winnowingScore', label: 'Winnowing', sortable: true },
  { key: 'sharedFingerprintCount', label: 'Shared fingerprints', sortable: true },
  { key: 'regionCount', label: 'Regions', sortable: true },
];

export default function AnalysisResults() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const report = useApi((signal) => getAnalysis(id, signal), [id]);
  const [filter, setFilter] = useState('');
  const [sort, setSort] = useState({ key: 'overallScore', dir: 'desc' });
  const [exporting, setExporting] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const rows = useMemo(() => {
    if (!report.data) return [];
    const f = filter.trim().toLowerCase();
    const list = report.data.comparisons.filter((c) => !f
      || c.fileAName.toLowerCase().includes(f) || c.fileBName.toLowerCase().includes(f));
    const factor = sort.dir === 'asc' ? 1 : -1;
    return [...list].sort((a, b) => (a[sort.key] - b[sort.key]) * factor
      || a.fileAName.localeCompare(b.fileAName) || a.fileBName.localeCompare(b.fileBName));
  }, [report.data, filter, sort]);

  async function doExport(format) {
    setExporting(format);
    setActionError(null);
    try {
      const { blob, filename } = await exportAnalysis(id, format);
      saveBlob(blob, filename);
    } catch (err) {
      setActionError(err);
    } finally {
      setExporting(null);
    }
  }

  async function doDelete() {
    setDeleting(true);
    setActionError(null);
    try {
      await deleteAnalysis(id);
      navigate('/history', { state: { deleted: report.data?.analysis.title } });
    } catch (err) {
      setActionError(err);
      setConfirmDelete(false);
    } finally {
      setDeleting(false);
    }
  }

  const toggleSort = (key) => setSort((s) => (s.key === key ? { key, dir: s.dir === 'desc' ? 'asc' : 'desc' } : { key, dir: 'desc' }));

  if (report.loading && !report.data) return <LoadingState label="Loading analysis report…" />;
  if (report.error) {
    return (
      <>
        <PageHeader title="Analysis results" />
        <ErrorState error={report.error} onRetry={report.error.status === 404 ? undefined : report.reload}
          title={report.error.status === 404 ? 'Analysis not found' : undefined} />
        <p><Link to="/history">← Back to history</Link></p>
      </>
    );
  }

  const { analysis, configuration, files, comparisons, disclaimer, sourceRetained } = report.data;

  return (
    <>
      <PageHeader
        eyebrow={<Link to="/history">History</Link>}
        title={analysis.title}
        subtitle={<>Analysis <code className="id-code">{analysis.id}</code></>}
        actions={(
          <>
            <button type="button" className="btn btn-secondary" onClick={() => doExport('csv')} disabled={!!exporting}>
              <Download size={16} aria-hidden="true" /> {exporting === 'csv' ? 'Exporting…' : 'CSV'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => doExport('json')} disabled={!!exporting}>
              <Download size={16} aria-hidden="true" /> {exporting === 'json' ? 'Exporting…' : 'JSON'}
            </button>
            <button type="button" className="btn btn-danger-outline" onClick={() => setConfirmDelete(true)}>
              <Trash2 size={16} aria-hidden="true" /> Delete
            </button>
          </>
        )}
      />

      {location.state?.justCreated && (
        <div className="alert alert-success" role="status">
          <CheckCircle2 size={18} aria-hidden="true" /> Analysis completed and saved.
        </div>
      )}
      <InlineError error={actionError} />

      <div className="meta-grid card results-meta">
        <Meta label="Completed" value={formatDateTime(analysis.completedAt)} />
        <Meta label="Files processed" value={analysis.fileCount} />
        <Meta label="Pairs compared" value={analysis.comparisonCount} />
        <Meta label="Highest overall" value={formatPercent(analysis.maxScore)} />
        <Meta label="Winnowing" value={`k = ${configuration.kgramSize}, w = ${configuration.windowSize}`} />
        <Meta label="Jaccard shingles" value={`${configuration.jaccardNgram}-token`} />
        <Meta label="Normalization" value={`${configuration.normalizationMode === 'IDENTIFIERS' ? 'Identifiers' : 'Original tokens'}${configuration.normalizeNumbers ? ' + numbers' : ''}`} />
        <Meta label="Source retained" value={sourceRetained ? 'Yes' : 'No'} />
      </div>

      <Disclaimer text={disclaimer} />

      <section className="card results-pairs" aria-labelledby="pairs-title">
        <div className="card-header wrap">
          <h2 id="pairs-title">File pairs</h2>
          <div className="search-box">
            <Search size={16} aria-hidden="true" />
            <label htmlFor="pair-filter" className="sr-only">Filter by filename</label>
            <input id="pair-filter" type="search" placeholder="Filter by filename…" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
        </div>
        <p className="muted small">
          Overall = (Jaccard + Winnowing) / 2 — an unweighted mean of two overlap measures, not a probability of copying.
          Pairs where Winnowing is not applicable use the Jaccard score.
        </p>
        {comparisons.length === 0 ? (
          <EmptyState title="No comparisons" />
        ) : rows.length === 0 ? (
          <EmptyState title="No pairs match the filter" />
        ) : (
          <div className="table-wrap">
            <table className="table" aria-labelledby="pairs-title">
              <thead>
                <tr>
                  <th scope="col" className="num">#</th>
                  {COLUMNS.map((c) => (
                    <th key={c.key} scope="col" className={c.sortable ? 'num' : ''}
                      aria-sort={sort.key === c.key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
                      {c.sortable ? (
                        <button type="button" className="sort-btn" onClick={() => toggleSort(c.key)}>
                          {c.label}
                          {sort.key === c.key ? (sort.dir === 'asc' ? <ArrowUp size={14} aria-hidden="true" /> : <ArrowDown size={14} aria-hidden="true" />) : <ArrowUpDown size={14} aria-hidden="true" />}
                        </button>
                      ) : c.label}
                    </th>
                  ))}
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((c, i) => (
                  <tr key={c.id}>
                    <td className="num muted">{i + 1}</td>
                    <td>
                      <div className="pair-names">
                        <span className="filename">{c.fileAName}</span>
                        <span className="muted"> ↔ </span>
                        <span className="filename">{c.fileBName}</span>
                      </div>
                      {c.warnings.length > 0 && (
                        <div className="row-warning" title={c.warnings.join('\n')}>
                          <AlertTriangle size={13} aria-hidden="true" /> {c.warnings[0]}{c.warnings.length > 1 ? ` (+${c.warnings.length - 1})` : ''}
                        </div>
                      )}
                    </td>
                    <td className="num"><div className="score-cell"><ScoreBadge value={c.overallScore} /><ScoreBar value={c.overallScore} /></div></td>
                    <td className="num">{formatPercent(c.jaccardScore)}</td>
                    <td className="num">{formatPercent(c.winnowingScore)}</td>
                    <td className="num">{c.sharedFingerprintCount}</td>
                    <td className="num">{c.regionCount}</td>
                    <td>
                      <Link to={`/analyses/${analysis.id}/comparisons/${c.id}`} className="btn btn-secondary btn-sm"
                        aria-label={`Compare ${c.fileAName} and ${c.fileBName}`}>
                        <GitCompare size={14} aria-hidden="true" /> Compare
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <div className="grid-2 align-start">
        <section className="card" aria-labelledby="files-title">
          <div className="card-header"><h2 id="files-title">Files</h2></div>
          <div className="table-wrap">
            <table className="table compact">
              <thead><tr><th scope="col">File</th><th scope="col" className="num">Size</th><th scope="col" className="num">Lines</th><th scope="col" className="num">Tokens</th></tr></thead>
              <tbody>
                {files.map((f) => (
                  <tr key={f.id}>
                    <td>
                      <span className="filename">{f.filename}</span>
                      {f.warnings.length > 0 && <div className="row-warning"><AlertTriangle size={13} aria-hidden="true" /> {f.warnings.join('; ')}</div>}
                    </td>
                    <td className="num">{formatBytes(f.fileSize)}</td>
                    <td className="num">{f.lineCount}</td>
                    <td className="num">{f.tokenCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
        <section className="card"><ScoreLegend /></section>
      </div>

      <ConfirmDialog open={confirmDelete} title="Delete this analysis?" confirmLabel="Delete permanently" danger busy={deleting}
        onConfirm={doDelete} onCancel={() => setConfirmDelete(false)}>
        <p>“{analysis.title}” and all its files, comparisons, matching regions and retained source text will be removed from the database. This cannot be undone.</p>
      </ConfirmDialog>
    </>
  );
}

function Meta({ label, value }) {
  return (
    <div className="meta">
      <span className="meta-label">{label}</span>
      <span className="meta-value">{value}</span>
    </div>
  );
}

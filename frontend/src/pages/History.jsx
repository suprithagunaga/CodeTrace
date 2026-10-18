import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { CheckCircle2, Eye, FilePlus2, Search, Trash2 } from 'lucide-react';
import { deleteAnalysis, listAnalyses } from '../api/client';
import { useApi, useDebounced } from '../hooks/useApi';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState, InlineError, LoadingState } from '../components/States';
import { ScoreBadge } from '../components/ScoreBadge';
import { Pagination } from '../components/Pagination';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { formatDateTime } from '../utils/format';

const SORTS = [
  { value: 'createdAt', label: 'Date' },
  { value: 'fileCount', label: 'Number of Files' },
  { value: 'maxScore', label: 'Highest Similarity Score' },
];
const HISTORY_PAGE_SIZE = 10;

export default function History() {
  const location = useLocation();
  const [search, setSearch] = useState('');
  const q = useDebounced(search, 300);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('createdAt');
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const [notice, setNotice] = useState(location.state?.deleted ? `Deleted “${location.state.deleted}”.` : null);

  const result = useApi((signal) => listAnalyses({ page, size: HISTORY_PAGE_SIZE, sort, direction: 'desc', q }, signal), [page, sort, q]);

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteAnalysis(pendingDelete.id);
      setNotice(`Deleted “${pendingDelete.title}”.`);
      setPendingDelete(null);
      const remainingOnPage = (result.data?.content.length ?? 1) - 1;
      if (remainingOnPage === 0 && page > 0) setPage(page - 1);
      else result.reload();
    } catch (err) {
      setDeleteError(err);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Analysis history"
        actions={<Link to="/new" className="btn btn-primary"><FilePlus2 size={18} aria-hidden="true" /> New Analysis</Link>}
      />

      {notice && (
        <div className="alert alert-success" role="status">
          <CheckCircle2 size={18} aria-hidden="true" /> {notice}
          <button type="button" className="link-btn" onClick={() => setNotice(null)}>Dismiss</button>
        </div>
      )}

      <section className="card history-card">
        <div className="filters">
          <div className="search-box grow">
            <Search size={16} aria-hidden="true" />
            <label htmlFor="history-search" className="sr-only">Search analyses</label>
            <input id="history-search" type="search" placeholder="Search title or filename…" value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0); }} maxLength={100} />
          </div>
          <div className="field inline">
            <label htmlFor="history-sort">Sort by</label>
            <select id="history-sort" value={sort} onChange={(e) => { setSort(e.target.value); setPage(0); }}>
              {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
          </div>
        </div>

        {result.loading && !result.data && <LoadingState label="Loading history…" />}
        {result.error && <ErrorState error={result.error} onRetry={result.reload} />}
        {result.data && result.data.content.length === 0 && (
          <EmptyState title={q ? 'No analyses match your search' : 'No analyses stored yet'}
            action={!q && <Link to="/new" className="btn btn-primary">Run your first analysis</Link>} />
        )}
        {result.data && result.data.content.length > 0 && (
          <>
            <div className={`table-wrap${result.loading ? ' is-refreshing' : ''}`}>
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Analysis</th>
                    <th scope="col">Created Date</th>
                    <th scope="col" className="num">Files</th>
                    <th scope="col">Highest Similarity</th>
                    <th scope="col">Status</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((a) => (
                    <tr key={a.id}>
                      <td>
                        <Link to={`/analyses/${a.id}`}>{a.title}</Link>
                      </td>
                      <td>{formatDateTime(a.createdAt)}</td>
                      <td className="num">{a.fileCount}</td>
                      <td>{a.maxScore !== null ? <ScoreBadge value={a.maxScore} /> : '—'}</td>
                      <td><span className="status-tag">{a.status.toLowerCase()}</span></td>
                      <td className="actions">
                        <Link to={`/analyses/${a.id}`} className="btn btn-secondary btn-sm" aria-label={`View ${a.title}`}>
                          <Eye size={16} aria-hidden="true" /> View
                        </Link>
                        <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => { setDeleteError(null); setPendingDelete(a); }} aria-label={`Delete ${a.title}`}>
                          <Trash2 size={16} aria-hidden="true" /> Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={result.data.page} totalPages={result.data.totalPages} totalElements={result.data.totalElements} onPage={setPage} />
          </>
        )}
      </section>

      <ConfirmDialog open={!!pendingDelete} title="Delete this analysis?" confirmLabel="Delete permanently" danger busy={deleting}
        onConfirm={confirmDelete} onCancel={() => setPendingDelete(null)}>
        <p>“{pendingDelete?.title}” and all associated files, comparisons, matching regions and retained source text will be removed from the database.</p>
        <InlineError error={deleteError} />
      </ConfirmDialog>
    </>
  );
}

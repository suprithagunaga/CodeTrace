import { Link, useNavigate } from 'react-router-dom';
import { Activity, FileCode2, FilePlus2, GitCompare } from 'lucide-react';
import { getDashboard, listAnalyses } from '../api/client';
import { useApi } from '../hooks/useApi';
import { PageHeader } from '../components/PageHeader';
import { StatCard } from '../components/StatCard';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { ScoreBadge } from '../components/ScoreBadge';
import { formatDateTime, formatNumber } from '../utils/format';

export default function Dashboard() {
  const navigate = useNavigate();
  const dashboard = useApi((signal) => getDashboard(signal), []);
  const recent = useApi((signal) => listAnalyses({ page: 0, size: 5 }, signal), []);

  const newButton = (
    <Link to="/new" className="btn btn-primary"><FilePlus2 size={18} aria-hidden="true" /> New Analysis</Link>
  );

  if (dashboard.loading && !dashboard.data) {
    return (<><PageHeader title="Dashboard" actions={newButton} /><LoadingState label="Loading dashboard…" /></>);
  }
  if (dashboard.error) {
    return (<><PageHeader title="Dashboard" actions={newButton} /><ErrorState error={dashboard.error} onRetry={dashboard.reload} /></>);
  }

  const d = dashboard.data;

  return (
    <>
      <PageHeader
        title="Dashboard"   
        actions={newButton}
      />

      <div className="stat-grid dashboard-stats">
        <StatCard icon={Activity} label="Total analyses" value={formatNumber(d.totalAnalyses)} />
        <StatCard icon={FileCode2} label="Files analyzed" value={formatNumber(d.totalFiles)} />
        <StatCard icon={GitCompare} label="File pairs compared" value={formatNumber(d.totalComparisons)} />
      </div>

      {d.totalAnalyses === 0 && (
        <EmptyState title="No analyses yet">
          Upload two or more Java files to compare them. Saved results will appear here.
        </EmptyState>
      )}

      {d.totalAnalyses > 0 && (
        <section className="card dashboard-recent" aria-labelledby="recent-title">
          <div className="card-header">
            <div>
              <h2 id="recent-title">Recent analyses</h2>
              <p className="muted small">Your latest saved runs</p>
            </div>
            <Link to="/history" className="btn btn-secondary btn-sm">View history</Link>
          </div>
          {recent.loading && !recent.data && <LoadingState label="Loading recent analyses…" />}
          {recent.error && <ErrorState error={recent.error} onRetry={recent.reload} />}
          {recent.data && recent.data.content.length === 0 && <EmptyState title="No recent analyses" />}
          {recent.data && recent.data.content.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Analysis</th>
                    <th scope="col">Created Date</th>
                    <th scope="col" className="num">Files</th>
                    <th scope="col">Highest Similarity</th>
                  </tr>
                </thead>
                <tbody>
                  {recent.data.content.map((analysis) => (
                    <tr key={analysis.id} className="clickable" onClick={() => navigate(`/analyses/${analysis.id}`)}>
                      <td><Link to={`/analyses/${analysis.id}`} onClick={(event) => event.stopPropagation()}>{analysis.title}</Link></td>
                      <td>{formatDateTime(analysis.createdAt)}</td>
                      <td className="num">{analysis.fileCount}</td>
                      <td>{analysis.maxScore !== null ? <ScoreBadge value={analysis.maxScore} /> : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </>
  );
}

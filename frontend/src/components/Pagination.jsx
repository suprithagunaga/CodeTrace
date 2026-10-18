import { ChevronLeft, ChevronRight } from 'lucide-react';

export function Pagination({ page, totalPages, totalElements, onPage }) {
  if (totalPages <= 1) {
    return <div className="pagination"><span className="muted">{totalElements} result{totalElements === 1 ? '' : 's'}</span></div>;
  }
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="muted">Page {page + 1} of {totalPages} · {totalElements} results</span>
      <div className="pagination-buttons">
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => onPage(page - 1)} disabled={page <= 0} aria-label="Previous page">
          <ChevronLeft size={16} aria-hidden="true" /> Prev
        </button>
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => onPage(page + 1)} disabled={page >= totalPages - 1} aria-label="Next page">
          Next <ChevronRight size={16} aria-hidden="true" />
        </button>
      </div>
    </nav>
  );
}

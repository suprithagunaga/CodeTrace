import { forwardRef, useMemo } from 'react';
import { buildLines, buildPlainLines } from '../utils/javaHighlight';

/**
 * Read-only code pane with original line numbers. Highlights matching regions either by exact character
 * offsets (original view) or by line ranges (normalized-token view).
 */
export const CodePane = forwardRef(function CodePane(
  { title, source, normalizedLines, mode, ranges, lineRanges, activeRegion, onScroll, onRegionClick, label },
  ref,
) {
  const lines = useMemo(
    () => (mode === 'normalized' ? buildPlainLines(normalizedLines, lineRanges) : buildLines(source, ranges)),
    [mode, source, normalizedLines, ranges, lineRanges],
  );
  return (
    <section className="code-pane" aria-label={label}>
      <div className="code-pane-header" title={title}>{title}</div>
      <div className="code-scroll" ref={ref} onScroll={onScroll} tabIndex={0} aria-label={`${label} source`}>
        <pre className="code">
          {lines.map((line) => {
            const regionIndex = line.regions.size ? Math.min(...line.regions) : null;
            const active = activeRegion !== null && line.regions.has(activeRegion);
            return (
              <div
                key={line.number}
                className={`code-line${regionIndex !== null ? ` in-region region-${regionIndex % 6}` : ''}${active ? ' active-region' : ''}`}
                data-line={line.number}
              >
                <span className="line-no" aria-hidden="true">{line.number}</span>
                <span className="line-text">
                  {line.segments.length === 0 ? ' ' : line.segments.map((seg, i) => (
                    <span
                      key={i}
                      className={`${seg.cls}${seg.region !== null ? ` hl hl-${seg.region % 6}` : ''}`}
                      onClick={seg.region !== null && onRegionClick ? () => onRegionClick(seg.region) : undefined}
                    >
                      {seg.text.replace(/\r/g, '')}
                    </span>
                  ))}
                </span>
              </div>
            );
          })}
        </pre>
      </div>
    </section>
  );
});

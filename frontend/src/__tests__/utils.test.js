import { describe, expect, it } from 'vitest';
import { formatBytes, formatPercent } from '../utils/format';
import { scoreBand } from '../utils/score';
import { buildLines, buildPlainLines } from '../utils/javaHighlight';

describe('format helpers', () => {
  it('formats fractions as percentages', () => {
    expect(formatPercent(0.4567)).toBe('45.7%');
    expect(formatPercent(1, 0)).toBe('100%');
    expect(formatPercent(null)).toBe('—');
  });
  it('formats byte sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(524288)).toBe('512.0 KB');
  });
  it('maps scores to non-accusatory bands', () => {
    expect(scoreBand(0.1).key).toBe('low');
    expect(scoreBand(0.45).key).toBe('moderate');
    expect(scoreBand(0.7).key).toBe('high');
    expect(scoreBand(1).key).toBe('very-high');
  });
});

describe('buildLines (code highlighting)', () => {
  const src = 'int a = 1; // note\nString s = "/* not a comment */";\n';

  it('keeps original line numbers and text', () => {
    const lines = buildLines(src);
    expect(lines).toHaveLength(2);
    expect(lines.map((l) => l.segments.map((s) => s.text).join(''))).toEqual([
      'int a = 1; // note', 'String s = "/* not a comment */";',
    ]);
  });

  it('classifies comments and strings without confusing comment markers inside strings', () => {
    const lines = buildLines(src);
    expect(lines[0].segments.find((s) => s.cls === 'tok-comment').text).toBe('// note');
    const str = lines[1].segments.find((s) => s.cls === 'tok-string');
    expect(str.text).toBe('"/* not a comment */"');
    expect(lines[1].segments.some((s) => s.cls === 'tok-comment')).toBe(false);
  });

  it('marks exactly the characters inside a backend region', () => {
    // region covers "a = 1" (offsets 4..9)
    const lines = buildLines(src, [{ start: 4, end: 9, index: 0 }]);
    const highlighted = lines[0].segments.filter((s) => s.region === 0).map((s) => s.text).join('');
    expect(highlighted).toBe('a = 1');
    expect(lines[0].regions.has(0)).toBe(true);
    expect(lines[1].regions.size).toBe(0);
  });

  it('handles multi-line regions and block comments', () => {
    const code = '/* a\n b */ int x;\nint y;';
    const lines = buildLines(code, [{ start: 11, end: code.length, index: 2 }]);
    expect(lines).toHaveLength(3);
    expect(lines[0].segments[0].cls).toBe('tok-comment');
    expect(lines[1].regions.has(2)).toBe(true);
    expect(lines[2].regions.has(2)).toBe(true);
  });

  it('normalized view highlights by line range', () => {
    const lines = buildPlainLines(['class T {', 'int V ;', '}'], [{ startLine: 2, endLine: 3, index: 1 }]);
    expect(lines[0].regions.size).toBe(0);
    expect(lines[1].segments[0].region).toBe(1);
    expect(lines[2].regions.has(1)).toBe(true);
  });
});

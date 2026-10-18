/**
 * Lightweight Java syntax classification for display. Produces, for each line, segments with absolute
 * character offsets so that backend matching regions (character offsets) can be overlaid exactly.
 * Output is plain text segments rendered by React (never injected as HTML).
 */
const KEYWORDS = new Set(('abstract assert boolean break byte case catch char class const continue default do double else enum '
  + 'extends final finally float for goto if implements import instanceof int interface long native new package private '
  + 'protected public return short static strictfp super switch synchronized this throw throws transient try void volatile '
  + 'while true false null var record yield sealed permits').split(' '));

function classify(src) {
  // returns array of {start, end, cls}
  const out = [];
  let i = 0;
  const n = src.length;
  const push = (start, end, cls) => { if (end > start) out.push({ start, end, cls }); };
  while (i < n) {
    const c = src[i];
    const next = src[i + 1];
    if (c === '/' && next === '/') {
      let j = i + 2;
      while (j < n && src[j] !== '\n') j++;
      push(i, j, 'tok-comment'); i = j;
    } else if (c === '/' && next === '*') {
      const close = src.indexOf('*/', i + 2);
      const j = close < 0 ? n : close + 2;
      push(i, j, 'tok-comment'); i = j;
    } else if (c === '"' && src.startsWith('"""', i)) {
      let j = i + 3;
      while (j < n && !src.startsWith('"""', j)) j += src[j] === '\\' ? 2 : 1;
      j = Math.min(n, j + 3);
      push(i, j, 'tok-string'); i = j;
    } else if (c === '"' || c === "'") {
      let j = i + 1;
      while (j < n && src[j] !== c && src[j] !== '\n') j += src[j] === '\\' ? 2 : 1;
      j = Math.min(n, j + 1);
      push(i, j, 'tok-string'); i = j;
    } else if (/[0-9]/.test(c)) {
      let j = i + 1;
      while (j < n && /[0-9a-zA-Z_.]/.test(src[j])) j++;
      push(i, j, 'tok-number'); i = j;
    } else if (/[A-Za-z_$]/.test(c)) {
      let j = i + 1;
      while (j < n && /[A-Za-z0-9_$]/.test(src[j])) j++;
      const word = src.slice(i, j);
      let cls = 'tok-ident';
      if (KEYWORDS.has(word)) cls = 'tok-keyword';
      else if (i > 0 && src[i - 1] === '@') cls = 'tok-annotation';
      else if (/^[A-Z]/.test(word)) cls = 'tok-type';
      push(i, j, cls); i = j;
    } else if (c === ' ' || c === '\t' || c === '\r' || c === '\f') {
      let j = i + 1;
      while (j < n && (src[j] === ' ' || src[j] === '\t' || src[j] === '\r' || src[j] === '\f')) j++;
      push(i, j, 'tok-space'); i = j;
    } else {
      push(i, i + 1, c === '\n' ? 'tok-space' : 'tok-punct');
      i += 1;
    }
  }
  return out;
}

/**
 * @param {string} source
 * @param {{start:number,end:number,index:number}[]} ranges character ranges to highlight (end exclusive)
 * @returns {{number:number, segments:{text:string, cls:string, region:number|null}[], regions:Set<number>}[]}
 */
export function buildLines(source, ranges = []) {
  const text = source ?? '';
  const tokens = classify(text);
  // line start offsets
  const lineStarts = [0];
  for (let i = 0; i < text.length; i++) if (text[i] === '\n') lineStarts.push(i + 1);
  const lines = lineStarts.map((start, idx) => ({ number: idx + 1, start, end: idx + 1 < lineStarts.length ? lineStarts[idx + 1] - 1 : text.length, segments: [], regions: new Set() }));
  if (text.endsWith('\n')) lines.pop();

  const sortedRanges = [...ranges].sort((a, b) => a.start - b.start);
  const regionAt = (pos) => {
    for (const r of sortedRanges) {
      if (pos >= r.start && pos < r.end) return r.index;
      if (r.start > pos) break;
    }
    return null;
  };

  // split every token at line boundaries and range boundaries
  const boundaries = new Set();
  for (const r of sortedRanges) { boundaries.add(r.start); boundaries.add(r.end); }

  let lineIdx = 0;
  for (const tok of tokens) {
    let s = tok.start;
    while (s < tok.end) {
      while (lineIdx < lines.length - 1 && s > lines[lineIdx].end) lineIdx++;
      const line = lines[lineIdx];
      if (!line) break;
      if (text[s] === '\n') { s += 1; continue; }
      let e = Math.min(tok.end, line.end);
      for (const b of boundaries) if (b > s && b < e) e = b;
      if (e <= s) { s += 1; continue; }
      const region = regionAt(s);
      line.segments.push({ text: text.slice(s, e), cls: tok.cls, region });
      if (region !== null) line.regions.add(region);
      s = e;
    }
  }
  return lines;
}

/** Plain lines (used for the normalized-token view): highlight by line ranges. */
export function buildPlainLines(lines, lineRanges = []) {
  return (lines ?? []).map((t, idx) => {
    const number = idx + 1;
    const hit = lineRanges.find((r) => number >= r.startLine && number <= r.endLine);
    const region = hit ? hit.index : null;
    return { number, segments: t ? [{ text: t, cls: 'tok-normalized', region }] : [], regions: region === null ? new Set() : new Set([region]) };
  });
}

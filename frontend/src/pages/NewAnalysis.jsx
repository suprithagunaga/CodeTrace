import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardPaste, FileCode2, Loader2, Play, Trash2 } from 'lucide-react';
import { createAnalysis, getConfig } from '../api/client';
import { useApi } from '../hooks/useApi';
import { PageHeader } from '../components/PageHeader';
import { FileDropzone } from '../components/FileDropzone';
import { InlineError } from '../components/States';
import { Disclaimer } from '../components/Disclaimer';
import { formatBytes } from '../utils/format';

/** Used only for client-side hints until /api/config answers; the server always re-validates. */
const FALLBACK_LIMITS = { maxFiles: 50, maxFileBytes: 524288, allowedExtensions: ['.java'] };
let nextId = 1;

/** Reads a File as text (Blob.text() with a FileReader fallback for older environments). */
function readText(file) {
  if (typeof file.text === 'function') return file.text();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
}

export async function validateFile(file, limits, currentCount) {
  const name = file.name || 'unnamed';
  const lower = name.toLowerCase();
  if (!limits.allowedExtensions.some((ext) => lower.endsWith(ext.toLowerCase()))) {
    return `${name}: unsupported file type (allowed: ${limits.allowedExtensions.join(', ')})`;
  }
  if (file.size === 0) return `${name}: file is empty`;
  if (file.size > limits.maxFileBytes) {
    return `${name}: ${formatBytes(file.size)} exceeds the ${formatBytes(limits.maxFileBytes)} limit`;
  }
  if (currentCount >= limits.maxFiles) return `${name}: the maximum of ${limits.maxFiles} files is reached`;
  const text = await readText(file);
  if (!text.trim()) return `${name}: file contains only whitespace`;
  return null;
}

export default function NewAnalysis() {
  const navigate = useNavigate();
  const config = useApi((signal) => getConfig(signal), []);
  const limits = config.data ?? FALLBACK_LIMITS;

  const [files, setFiles] = useState([]);
  const [rejected, setRejected] = useState([]);
  const [pasteName, setPasteName] = useState('Snippet1.java');
  const [pasteCode, setPasteCode] = useState('');
  const [pasteError, setPasteError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState(null);
  const submittingRef = useRef(false);

  async function addFiles(list) {
    const accepted = [];
    const problems = [];
    let count = files.length;
    for (const file of list) {
      // eslint-disable-next-line no-await-in-loop
      const problem = await validateFile(file, limits, count);
      if (problem) problems.push(problem);
      else {
        accepted.push({ id: nextId++, file, name: file.name, size: file.size, origin: 'upload' });
        count += 1;
      }
    }
    setFiles((prev) => [...prev, ...accepted]);
    setRejected(problems);
    setError(null);
  }

  async function addPasted() {
    setPasteError(null);
    let name = pasteName.trim() || `Snippet${files.length + 1}.java`;
    if (!name.toLowerCase().endsWith('.java')) name += '.java';
    if (!pasteCode.trim()) {
      setPasteError('Paste some Java code first.');
      return;
    }
    const file = new File([pasteCode], name, { type: 'text/x-java' });
    const problem = await validateFile(file, limits, files.length);
    if (problem) {
      setPasteError(problem);
      return;
    }
    setFiles((prev) => [...prev, { id: nextId++, file, name, size: file.size, origin: 'paste' }]);
    setPasteCode('');
    setPasteName(`Snippet${files.length + 2}.java`);
    setError(null);
  }

  const removeFile = (id) => setFiles((prev) => prev.filter((f) => f.id !== id));

  const canSubmit = files.length >= 2 && !submitting;
  const duplicateNames = new Set(files.map((f) => f.name.toLowerCase())).size !== files.length;

  async function submit(e) {
    e.preventDefault();
    if (!canSubmit || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    setProgress(0);
    setError(null);
    try {
      const report = await createAnalysis(
        files.map((f) => f.file),
        {},
        setProgress,
      );
      navigate(`/analyses/${report.analysis.id}`, { state: { justCreated: true } });
    } catch (err) {
      setError(err);
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <PageHeader
        title="New Analysis"
      />

      {config.error && (
        <div className="alert alert-warn" role="status">
          Could not load server limits ({config.error.message}). Using default limits for hints; the server validates every upload.
        </div>
      )}

      <div className="grid-2 align-start new-analysis-grid">
        <section className="card upload-card" aria-labelledby="upload-title">
          <div className="card-header"><h2 id="upload-title">Upload files</h2></div>
          <FileDropzone onFiles={addFiles} accept={limits.allowedExtensions.join(',')} disabled={submitting} />
          <p className="muted small hint">
            Supported: {limits.allowedExtensions.join(', ')} · up to {limits.maxFiles} files · max {formatBytes(limits.maxFileBytes)} per file.
          
          </p>
          {rejected.length > 0 && (
            <div className="alert alert-warn" role="alert">
              <div>
                <strong>{rejected.length} file{rejected.length > 1 ? 's were' : ' was'} not added</strong>
                <ul className="error-details">{rejected.map((r, i) => <li key={i}>{r}</li>)}</ul>
              </div>
              <button type="button" className="link-btn" onClick={() => setRejected([])}>Dismiss</button>
            </div>
          )}
        </section>

        <section className="card paste-card" aria-labelledby="paste-title">
          <div className="card-header"><h2 id="paste-title">Or paste code</h2></div>
          <div className="field">
            <label htmlFor="paste-name">File name</label>
            <input id="paste-name" type="text" value={pasteName} onChange={(e) => setPasteName(e.target.value)} maxLength={120} disabled={submitting} />
          </div>
          <div className="field">
            <label htmlFor="paste-code">Java source</label>
            <textarea id="paste-code" className="code-input" rows={9} spellCheck={false} value={pasteCode}
              placeholder={'public class Example {\n    // paste code here\n}'}
              onChange={(e) => setPasteCode(e.target.value)} disabled={submitting} />
          </div>
          {pasteError && <p className="field-error" role="alert">{pasteError}</p>}
          <button type="button" className="btn btn-secondary" onClick={addPasted} disabled={submitting}>
            <ClipboardPaste size={16} aria-hidden="true" /> Add pasted code
          </button>
        </section>
      </div>

      <section className="card selected-files-card" aria-labelledby="selected-title">
        <div className="card-header">
          <h2 id="selected-title">Selected files <span className="count-pill">{files.length}</span></h2>
          {files.length > 0 && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setFiles([])} disabled={submitting}>Clear all</button>
          )}
        </div>
        {files.length === 0 ? (
          <p className="muted">No files selected yet. Add at least two files to run a pairwise comparison.</p>
        ) : (
          <ul className="file-list" aria-label="Selected files">
            {files.map((f) => (
              <li key={f.id}>
                <FileCode2 size={18} aria-hidden="true" />
                <span className="filename">{f.name}</span>
                <span className="muted small">{formatBytes(f.size)}{f.origin === 'paste' ? ' · pasted' : ''}</span>
                <button type="button" className="icon-btn" onClick={() => removeFile(f.id)} disabled={submitting}
                  aria-label={`Remove ${f.name}`}>
                  <Trash2 size={16} />
                </button>
              </li>
            ))}
          </ul>
        )}
        {files.length === 1 && <p className="field-error">Add at least one more file — pairwise comparison needs two or more.</p>}
        {duplicateNames && <p className="muted small">Some files share a name; the server will label them “Name (2).java”, etc.</p>}
        {files.length >= 2 && <p className="muted small">{files.length} files → {(files.length * (files.length - 1)) / 2} unique pairs.</p>}
      </section>

      <InlineError error={error} />

      {submitting && (
        <div className="progress-block" role="status" aria-live="polite">
          <div className="progress" aria-hidden="true"><div className="progress-fill" style={{ width: `${Math.round(progress * 100)}%` }} /></div>
          <span className="muted small">
            {progress < 1 ? `Uploading… ${Math.round(progress * 100)}%` : 'Upload complete — running similarity algorithms…'}
          </span>
        </div>
      )}

      <div className="form-actions">
        <Disclaimer text="Similarity scores show code overlap, not proof of plagiarism." />
        <button type="submit" className="btn btn-primary btn-lg" disabled={!canSubmit} aria-disabled={!canSubmit}>
          {submitting ? <Loader2 className="spin" size={18} aria-hidden="true" /> : <Play size={18} aria-hidden="true" />}
          {submitting ? 'Analyzing…' : 'Run analysis'}
        </button>
      </div>
    </form>
  );
}

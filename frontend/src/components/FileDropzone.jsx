import { useRef, useState } from 'react';
import { UploadCloud } from 'lucide-react';

/** Drag-and-drop zone plus a keyboard-accessible file picker. */
export function FileDropzone({ onFiles, accept = '.java', disabled = false }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);

  const handle = (fileList) => {
    const files = Array.from(fileList || []);
    if (files.length) onFiles(files);
  };

  return (
    <div
      className={`dropzone${dragging ? ' dragging' : ''}${disabled ? ' disabled' : ''}`}
      onDragOver={(e) => { e.preventDefault(); if (!disabled) setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => { e.preventDefault(); setDragging(false); if (!disabled) handle(e.dataTransfer.files); }}
      data-testid="dropzone"
    >
      <UploadCloud size={32} aria-hidden="true" />
      <p><strong>Drag and drop .java files here</strong></p>
      <p className="muted">or</p>
      <button type="button" className="btn btn-secondary" onClick={() => inputRef.current?.click()} disabled={disabled}>
        Browse files
      </button>
      <input
        ref={inputRef}
        id="file-input"
        type="file"
        accept={accept}
        multiple
        className="sr-only"
        aria-label="Choose Java source files"
        onChange={(e) => { handle(e.target.files); e.target.value = ''; }}
        disabled={disabled}
      />
    </div>
  );
}

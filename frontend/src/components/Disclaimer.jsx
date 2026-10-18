import { Info } from 'lucide-react';

export function Disclaimer({ text }) {
  return (
    <div className="alert alert-info">
      <Info size={18} aria-hidden="true" />
      <p>
        {text || 'Similarity scores indicate potential overlap between source files. They do not by themselves prove plagiarism or misconduct.'}
      </p>
    </div>
  );
}

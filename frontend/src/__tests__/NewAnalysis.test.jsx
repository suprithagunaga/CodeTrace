import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import NewAnalysis from '../pages/NewAnalysis';
import * as client from '../api/client';

vi.mock('../api/client', async (orig) => {
  const actual = await orig();
  return { ...actual, getConfig: vi.fn(), createAnalysis: vi.fn() };
});

const CONFIG = {
  version: '1.0.0', maxFiles: 3, maxFileBytes: 100, allowedExtensions: ['.java'], sourceRetained: true, retentionDays: 30,
  defaults: { kgramSize: 5, windowSize: 4, jaccardNgram: 3, normalizationMode: 'IDENTIFIERS', normalizeNumbers: false },
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/new']}>
      <Routes>
        <Route path="/new" element={<NewAnalysis />} />
        <Route path="/analyses/:id" element={<div>Results page for new analysis</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

const java = (name, code = 'class X {}') => new File([code], name, { type: 'text/plain' });

describe('New Analysis page', () => {
  beforeEach(() => {
    client.getConfig.mockResolvedValue(CONFIG);
  });

  it('adds and removes files and enables submission only with two or more', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/up to 3 files/);
    const submit = screen.getByRole('button', { name: /run analysis/i });
    expect(submit).toBeDisabled();

    await user.upload(screen.getByLabelText(/choose java source files/i), [java('A.java')]);
    expect(await screen.findByText('A.java')).toBeInTheDocument();
    expect(screen.getByText(/add at least one more file/i)).toBeInTheDocument();
    expect(submit).toBeDisabled();

    await user.upload(screen.getByLabelText(/choose java source files/i), [java('B.java')]);
    await screen.findByText('B.java');
    expect(screen.getByText(/2 files → 1 unique pairs/)).toBeInTheDocument();
    expect(submit).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Remove A.java' }));
    expect(screen.queryByText('A.java')).not.toBeInTheDocument();
    expect(submit).toBeDisabled();
  });

  it('rejects wrong extensions, empty, oversized and whitespace-only files with messages', async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderPage();
    await screen.findByText(/up to 3 files/);
    await user.upload(screen.getByLabelText(/choose java source files/i), [
      java('notes.txt', 'hello'), java('Empty.java', ''), java('Big.java', 'x'.repeat(101)), java('Blank.java', '   \n'), java('Ok.java'),
    ]);
    const alert = await screen.findByText(/4 files were not added/);
    const box = alert.closest('.alert');
    expect(within(box).getByText(/notes.txt: unsupported file type/)).toBeInTheDocument();
    expect(within(box).getByText(/Empty.java: file is empty/)).toBeInTheDocument();
    expect(within(box).getByText(/Big.java: .* exceeds/)).toBeInTheDocument();
    expect(within(box).getByText(/Blank.java: file contains only whitespace/)).toBeInTheDocument();
    expect(screen.getByText('Ok.java')).toBeInTheDocument();
  });

  it('enforces the maximum file count', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/up to 3 files/);
    await user.upload(screen.getByLabelText(/choose java source files/i), [java('A.java'), java('B.java'), java('C.java'), java('D.java')]);
    expect(await screen.findByText(/D.java: the maximum of 3 files is reached/)).toBeInTheDocument();
  });

  it('adds pasted code as a .java file', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/up to 3 files/);
    await user.click(screen.getByRole('button', { name: /add pasted code/i }));
    expect(screen.getByText(/paste some java code first/i)).toBeInTheDocument();
    await user.clear(screen.getByLabelText('File name'));
    await user.type(screen.getByLabelText('File name'), 'Pasted');
    await user.type(screen.getByLabelText('Java source'), 'class P {{}}');
    await user.click(screen.getByRole('button', { name: /add pasted code/i }));
    expect(await screen.findByText('Pasted.java')).toBeInTheDocument();
    expect(screen.getByText(/pasted/, { selector: 'span.muted' })).toBeInTheDocument();
  });

  it('submits files with backend defaults, shows progress, blocks duplicate submits and navigates on success', async () => {
    const user = userEvent.setup();
    let resolve;
    client.createAnalysis.mockImplementation((files, opts, onProgress) => {
      onProgress(1);
      return new Promise((r) => { resolve = r; });
    });
    renderPage();
    await screen.findByText(/up to 3 files/);
    await user.upload(screen.getByLabelText(/choose java source files/i), [java('A.java'), java('B.java')]);
    await screen.findByText('B.java');
    await user.click(screen.getByRole('button', { name: /run analysis/i }));

    expect(await screen.findByText(/running similarity algorithms/i)).toBeInTheDocument();
    const busy = screen.getByRole('button', { name: /analyzing/i });
    expect(busy).toBeDisabled();
    await user.click(busy);
    expect(client.createAnalysis).toHaveBeenCalledTimes(1);

    const [files, opts] = client.createAnalysis.mock.calls[0];
    expect(files.map((f) => f.name)).toEqual(['A.java', 'B.java']);
    expect(opts).toEqual({});

    resolve({ analysis: { id: 'abc' } });
    expect(await screen.findByText('Results page for new analysis')).toBeInTheDocument();
  });

  it('shows server validation errors and does not navigate', async () => {
    const user = userEvent.setup();
    client.createAnalysis.mockRejectedValue(new client.ApiError('1 file(s) failed validation', {
      status: 400, details: [{ field: 'B.java', message: 'File is not valid UTF-8 text' }],
    }));
    renderPage();
    await screen.findByText(/up to 3 files/);
    await user.upload(screen.getByLabelText(/choose java source files/i), [java('A.java'), java('B.java')]);
    await screen.findByText('B.java');
    await user.click(screen.getByRole('button', { name: /run analysis/i }));
    expect(await screen.findByText('1 file(s) failed validation')).toBeInTheDocument();
    expect(screen.getByText(/not valid UTF-8/)).toBeInTheDocument();
    expect(screen.queryByText('Results page for new analysis')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /run analysis/i })).toBeEnabled();
  });

  it('hides the title and algorithm controls and shows the concise similarity notice', async () => {
    renderPage();
    await screen.findByText(/up to 3 files/);
    expect(screen.queryByLabelText(/analysis title/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /algorithm options/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/k-gram size/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/window size/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/jaccard shingle size/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/normalize identifiers/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/normalize numeric literals/i)).not.toBeInTheDocument();
    expect(screen.getByText('Similarity scores show code overlap, not proof of plagiarism.')).toBeInTheDocument();
  });

  it('still works with fallback hints when the config endpoint fails', async () => {
    client.getConfig.mockRejectedValue(new client.ApiError('Cannot reach the CodeTrace backend', { network: true }));
    renderPage();
    expect(await screen.findByText(/could not load server limits/i)).toBeInTheDocument();
    expect(screen.getByText(/up to 50 files/)).toBeInTheDocument();
  });
});

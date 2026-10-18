import { describe, expect, it, vi } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import AnalysisResults from '../pages/AnalysisResults';
import * as client from '../api/client';
import * as download from '../utils/download';
import { ANALYSIS_ID, report } from './fixtures';

vi.mock('../api/client', async (orig) => {
  const actual = await orig();
  return { ...actual, getAnalysis: vi.fn(), exportAnalysis: vi.fn(), deleteAnalysis: vi.fn() };
});
vi.mock('../utils/download', () => ({ saveBlob: vi.fn() }));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/analyses/${ANALYSIS_ID}`]}>
      <Routes>
        <Route path="/analyses/:id" element={<AnalysisResults />} />
        <Route path="/analyses/:id/comparisons/:cid" element={<div>Comparison page</div>} />
        <Route path="/history" element={<div>History page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}


describe('Analysis Results page', () => {
  it('renders metadata and pairs from the API sorted by overall score', async () => {
    client.getAnalysis.mockResolvedValue(report);
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Week 4 lab' })).toBeInTheDocument();
    expect(client.getAnalysis).toHaveBeenCalledWith(ANALYSIS_ID, expect.anything());
    expect(screen.getByText(ANALYSIS_ID)).toBeInTheDocument();
    expect(screen.getByText('Files processed').nextSibling).toHaveTextContent('3');
    const table = screen.getByRole('table', { name: /file pairs/i });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByText('92.0%')).toBeInTheDocument();
    expect(within(rows[2]).getByText('15.0%')).toBeInTheDocument();
    expect(screen.getByText(/not proof of plagiarism/i)).toBeInTheDocument();
  });

  it('sorts by score and filters by filename', async () => {
    const user = userEvent.setup();
    client.getAnalysis.mockResolvedValue(report);
    renderPage();
    const table = await screen.findByRole('table', { name: /file pairs/i });
    await user.click(within(table).getByRole('button', { name: /overall/i }));
    let rows = within(table).getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('15.0%')).toBeInTheDocument();
    await user.click(within(table).getByRole('button', { name: /shared fingerprints/i }));
    rows = within(table).getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('30')).toBeInTheDocument();

    await user.type(screen.getByLabelText(/filter by filename/i), 'gamma');
    rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    await user.clear(screen.getByLabelText(/filter by filename/i));
    await user.type(screen.getByLabelText(/filter by filename/i), 'zzz');
    expect(screen.getByText('No pairs match the filter')).toBeInTheDocument();
  });

  it('navigates to the comparison view', async () => {
    const user = userEvent.setup();
    client.getAnalysis.mockResolvedValue(report);
    renderPage();
    await user.click(await screen.findByRole('link', { name: 'Compare Alpha.java and Beta.java' }));
    expect(await screen.findByText('Comparison page')).toBeInTheDocument();
  });

  it('downloads CSV and JSON exports through the API', async () => {
    const user = userEvent.setup();
    client.getAnalysis.mockResolvedValue(report);
    const blob = new Blob(['x']);
    client.exportAnalysis.mockResolvedValue({ blob, filename: 'codetrace-analysis.csv' });
    renderPage();
    await user.click(await screen.findByRole('button', { name: /csv/i }));
    expect(client.exportAnalysis).toHaveBeenCalledWith(ANALYSIS_ID, 'csv');
    await waitFor(() => expect(download.saveBlob).toHaveBeenCalledWith(blob, 'codetrace-analysis.csv'));
    await user.click(screen.getByRole('button', { name: /json/i }));
    expect(client.exportAnalysis).toHaveBeenLastCalledWith(ANALYSIS_ID, 'json');
  });

  it('shows an export error instead of a fake download', async () => {
    const user = userEvent.setup();
    client.getAnalysis.mockResolvedValue(report);
    client.exportAnalysis.mockRejectedValue(new client.ApiError('The database is unavailable', { status: 503 }));
    renderPage();
    await user.click(await screen.findByRole('button', { name: /csv/i }));
    expect(await screen.findByText('The database is unavailable')).toBeInTheDocument();
    expect(download.saveBlob).not.toHaveBeenCalled();
  });

  it('deletes after confirmation and returns to history', async () => {
    const user = userEvent.setup();
    client.getAnalysis.mockResolvedValue(report);
    client.deleteAnalysis.mockResolvedValue(null);
    renderPage();
    await user.click(await screen.findByRole('button', { name: /^delete$/i }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /cancel/i }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(client.deleteAnalysis).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /delete permanently/i }));
    expect(client.deleteAnalysis).toHaveBeenCalledWith(ANALYSIS_ID);
    expect(await screen.findByText('History page')).toBeInTheDocument();
  });

  it('shows a connection error with a working retry', async () => {
    const user = userEvent.setup();
    client.getAnalysis
      .mockRejectedValueOnce(new client.ApiError('Cannot reach the CodeTrace backend at http://localhost:8080.', { network: true }))
      .mockResolvedValueOnce(report);
    renderPage();
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByRole('heading', { name: 'Week 4 lab' })).toBeInTheDocument();
    expect(client.getAnalysis).toHaveBeenCalledTimes(2);
  });

  it('shows not-found for unknown analyses', async () => {
    client.getAnalysis.mockRejectedValue(new client.ApiError('Analysis x not found', { status: 404 }));
    renderPage();
    expect(await screen.findByText('Analysis not found')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
  });
});

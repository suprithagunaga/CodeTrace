import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import History from '../pages/History';
import * as client from '../api/client';
import { page, report } from './fixtures';

vi.mock('../api/client', async (orig) => {
  const actual = await orig();
  return { ...actual, listAnalyses: vi.fn(), deleteAnalysis: vi.fn() };
});

const a1 = { ...report.analysis };
const a2 = { ...report.analysis, id: '3f2b8c1e-0000-4000-8000-000000000002', title: 'Second run', maxScore: 0.2 };

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/history']}>
      <Routes>
        <Route path="/history" element={<History />} />
        <Route path="/analyses/:id" element={<div>Results page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('History page', () => {
  it('loads analyses from the API on every mount (persisted server-side, not in localStorage)', async () => {
    client.listAnalyses.mockResolvedValue(page([a1, a2]));
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const first = renderPage();
    expect(await screen.findByText('Week 4 lab')).toBeInTheDocument();
    first.unmount();
    renderPage(); // simulates a page refresh
    expect(await screen.findByText('Second run')).toBeInTheDocument();
    expect(client.listAnalyses).toHaveBeenCalledTimes(2);
    expect(setItem).not.toHaveBeenCalled();
  });

  it('sorts saved analyses by date, file count, or highest similarity in descending order and combines sorting with search', async () => {
    const user = userEvent.setup();
    client.listAnalyses.mockResolvedValue(page([a1], { totalElements: 25, totalPages: 3 }));
    renderPage();
    await screen.findByText('Week 4 lab');
    expect(client.listAnalyses).toHaveBeenLastCalledWith({ page: 0, size: 10, sort: 'createdAt', direction: 'desc', q: '' }, expect.anything());

    await user.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => expect(client.listAnalyses).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }), expect.anything()));

    expect(screen.getByRole('option', { name: 'Date' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Number of Files' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Highest Similarity Score' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Order')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Per page')).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Sort by'), 'fileCount');
    await waitFor(() => expect(client.listAnalyses).toHaveBeenLastCalledWith(
      { page: 0, size: 10, sort: 'fileCount', direction: 'desc', q: '' }, expect.anything()));

    await user.selectOptions(screen.getByLabelText('Sort by'), 'maxScore');
    await waitFor(() => expect(client.listAnalyses).toHaveBeenLastCalledWith(
      { page: 0, size: 10, sort: 'maxScore', direction: 'desc', q: '' }, expect.anything()));

    await user.type(screen.getByLabelText('Search analyses'), 'lab');
    await waitFor(() => expect(client.listAnalyses).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'lab', page: 0 }), expect.anything()));
  });

  it('shows only the requested history columns and opens the selected analysis from View', async () => {
    const user = userEvent.setup();
    client.listAnalyses.mockResolvedValue(page([a1]));
    renderPage();
    await screen.findByText('Week 4 lab');
    expect(screen.getByRole('columnheader', { name: 'Analysis' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Created Date' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Files' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Highest Similarity' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Status' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Pairs' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete Week 4 lab' })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'View Week 4 lab' }));
    expect(await screen.findByText('Results page')).toBeInTheDocument();
  });

  it('deletes an analysis after confirmation and reloads the list', async () => {
    const user = userEvent.setup();
    client.listAnalyses.mockResolvedValueOnce(page([a1, a2])).mockResolvedValue(page([a2]));
    client.deleteAnalysis.mockResolvedValue(null);
    renderPage();
    await screen.findByText('Week 4 lab');
    await user.click(screen.getByRole('button', { name: 'Delete Week 4 lab' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText(/will be removed from the database/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: /delete permanently/i }));
    expect(client.deleteAnalysis).toHaveBeenCalledWith(a1.id);
    expect(await screen.findByText(/Deleted “Week 4 lab”/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Week 4 lab' })).not.toBeInTheDocument());
  });

  it('keeps the dialog open and shows the error when deletion fails', async () => {
    const user = userEvent.setup();
    client.listAnalyses.mockResolvedValue(page([a1]));
    client.deleteAnalysis.mockRejectedValue(new client.ApiError('The database is unavailable', { status: 503 }));
    renderPage();
    await screen.findByText('Week 4 lab');
    await user.click(screen.getByRole('button', { name: 'Delete Week 4 lab' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /delete permanently/i }));
    expect(await within(screen.getByRole('dialog')).findByText('The database is unavailable')).toBeInTheDocument();
    expect(screen.queryByText(/Deleted “/)).not.toBeInTheDocument();
  });

  it('shows empty and error states', async () => {
    client.listAnalyses.mockResolvedValueOnce(page([]));
    const r = renderPage();
    expect(await screen.findByText('No analyses stored yet')).toBeInTheDocument();
    r.unmount();
    client.listAnalyses.mockRejectedValueOnce(new client.ApiError('Cannot reach the CodeTrace backend', { network: true }));
    renderPage();
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });
});

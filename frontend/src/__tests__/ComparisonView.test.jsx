import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ComparisonView from '../pages/ComparisonView';
import * as client from '../api/client';
import { ANALYSIS_ID, comparison } from './fixtures';

vi.mock('../api/client', async (orig) => {
  const actual = await orig();
  return { ...actual, getComparison: vi.fn() };
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/analyses/${ANALYSIS_ID}/comparisons/c1`]}>
      <Routes>
        <Route path="/analyses/:id/comparisons/:comparisonId" element={<ComparisonView />} />
        <Route path="/analyses/:id" element={<div>Results page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Code Comparison page', () => {
  it('shows both files side by side with original line numbers and backend regions highlighted', async () => {
    client.getComparison.mockResolvedValue(comparison);
    const { container } = renderPage();
    expect(await screen.findByText('A · Alpha.java')).toBeInTheDocument();
    expect(client.getComparison).toHaveBeenCalledWith(ANALYSIS_ID, 'c1', expect.anything());
    expect(screen.getByText('B · Beta.java')).toBeInTheDocument();

    const paneA = screen.getByRole('region', { name: 'File A' });
    const paneB = screen.getByRole('region', { name: 'File B' });
    expect(paneA.querySelectorAll('.code-line')).toHaveLength(5);
    expect(paneB.querySelectorAll('.code-line')).toHaveLength(6);
    // B line 1 is the extra comment, outside the region; lines 2-6 are inside it
    expect(paneB.querySelector('[data-line="1"]')).not.toHaveClass('in-region');
    expect(paneB.querySelector('[data-line="2"]')).toHaveClass('in-region');
    expect(container.querySelectorAll('.hl-0').length).toBeGreaterThan(10);
    expect(screen.getByText(/Jaccard compares the sets/)).toBeInTheDocument();
    expect(screen.getByText(/A 1–5 ↔ B 2–6/)).toBeInTheDocument();
  });

  it('switches to the normalized token view', async () => {
    const user = userEvent.setup();
    client.getComparison.mockResolvedValue(comparison);
    renderPage();
    await screen.findByText('A · Alpha.java');
    await user.click(screen.getByRole('button', { name: 'Normalized tokens' }));
    const paneA = screen.getByRole('region', { name: 'File A' });
    expect(within(paneA).getByText('int M ( int V , int V ) {')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Normalized tokens' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Original code' }));
    expect(within(paneA).getByText('Alpha')).toBeInTheDocument();
  });

  it('toggles synchronized scrolling and focuses a region', async () => {
    const user = userEvent.setup();
    client.getComparison.mockResolvedValue(comparison);
    renderPage();
    await screen.findByText('A · Alpha.java');
    const syncBtn = screen.getByRole('button', { name: /scroll synced/i });
    await user.click(syncBtn);
    expect(screen.getByRole('button', { name: /scroll independent/i })).toHaveAttribute('aria-pressed', 'false');
    await user.click(screen.getByRole('button', { name: /region 1:/i }));
    expect(screen.getByRole('button', { name: /region 1:/i })).toHaveClass('active');
  });

  it('explains when no matching region exists', async () => {
    client.getComparison.mockResolvedValue({ ...comparison, regions: [], sharedFingerprintCount: 0 });
    renderPage();
    expect(await screen.findByText(/No meaningful matching region was found/)).toBeInTheDocument();
  });

  it('explains when source text was not retained', async () => {
    client.getComparison.mockResolvedValue({ ...comparison, sourceAvailable: false, fileASource: null, fileBSource: null });
    renderPage();
    expect(await screen.findByText(/Source text was not retained/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'File A' })).not.toBeInTheDocument();
  });

  it('handles a missing comparison', async () => {
    client.getComparison.mockRejectedValue(new client.ApiError('Comparison c1 not found in analysis', { status: 404 }));
    renderPage();
    expect(await screen.findByText('Comparison not found', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText('Comparison c1 not found in analysis')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to results/i })).toBeInTheDocument();
  });
});

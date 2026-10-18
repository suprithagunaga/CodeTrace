import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Dashboard from '../pages/Dashboard';
import * as client from '../api/client';
import { page, report } from './fixtures';

vi.mock('../api/client', async (orig) => {
  const actual = await orig();
  return { ...actual, getDashboard: vi.fn(), listAnalyses: vi.fn() };
});

const dashboard = {
  totalAnalyses: 4, totalFiles: 12, totalComparisons: 18,
  scoreDistribution: Array.from({ length: 10 }, (_, i) => ({ label: `${i * 10}–${(i + 1) * 10}%`, from: i / 10, to: (i + 1) / 10, count: i === 9 ? 2 : 1 })),
  topPairs: [{ analysisId: report.analysis.id, analysisTitle: 'Week 4 lab', comparisonId: 'c1', fileAName: 'Alpha.java', fileBName: 'Beta.java', overallScore: 0.92, createdAt: '2026-10-01T10:00:00Z' }],
  recentAnalyses: [report.analysis],
};

const renderPage = () => render(<MemoryRouter><Dashboard /></MemoryRouter>);

describe('Dashboard', () => {
  it('shows saved-data totals and recent analyses from the API', async () => {
    client.getDashboard.mockResolvedValue(dashboard);
    client.listAnalyses.mockResolvedValue(page([report.analysis]));
    renderPage();
    expect(await screen.findByText('18')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Week 4 lab' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Recent analyses' })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /bar chart/i })).not.toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /new analysis/i }).length).toBeGreaterThan(0);
  });

  it('shows an empty state when nothing is stored', async () => {
    client.getDashboard.mockResolvedValue({ ...dashboard, totalAnalyses: 0, totalFiles: 0, totalComparisons: 0, topPairs: [], recentAnalyses: [] });
    client.listAnalyses.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('No analyses yet', { selector: 'strong' })).toBeInTheDocument();
  });

  it('shows a retryable error when the backend is down', async () => {
    client.getDashboard.mockRejectedValue(new client.ApiError('Cannot reach the CodeTrace backend', { network: true }));
    client.listAnalyses.mockResolvedValue(page([]));
    renderPage();
    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });
});

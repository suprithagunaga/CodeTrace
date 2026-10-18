import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createAnalysis, exportAnalysis, getHealth, listAnalyses, API_BASE_URL } from '../api/client';

function jsonResponse(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
}

afterEach(() => vi.unstubAllGlobals());

describe('api client', () => {
  it('builds history query strings and omits empty parameters', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse({ content: [] })));
    vi.stubGlobal('fetch', fetchMock);
    await listAnalyses({ page: 2, size: 20, sort: 'maxScore', direction: 'asc', q: 'lab 1' });
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/api/analyses?page=2&size=20&sort=maxScore&direction=asc&q=lab+1`);
    await listAnalyses({ q: '' });
    expect(fetchMock.mock.calls[1][0]).not.toContain('q=');
  });

  it('turns server error bodies into ApiError with message and details', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(
      { status: 400, message: '1 file(s) failed validation', details: [{ field: 'a.txt', message: 'Unsupported file type' }] }, 400)));
    const err = await listAnalyses().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.message).toBe('1 file(s) failed validation');
    expect(err.details[0].field).toBe('a.txt');
  });

  it('reports network failures clearly instead of pretending success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const err = await listAnalyses().catch((e) => e);
    expect(err.network).toBe(true);
    expect(err.message).toContain('Cannot reach the CodeTrace backend');
  });

  it('health returns the body for 503 (database down)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'DEGRADED', database: { status: 'DOWN' } }, 503)));
    const health = await getHealth();
    expect(health.database.status).toBe('DOWN');
  });

  it('export resolves the filename from Content-Disposition', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('a,b\r\n', {
      status: 200, headers: { 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="codetrace-analysis-x.csv"' },
    })));
    const { blob, filename } = await exportAnalysis('x', 'csv');
    expect(filename).toBe('codetrace-analysis-x.csv');
    expect(await blob.text()).toBe('a,b\r\n');
  });

  it('createAnalysis posts multipart form data and reports progress', async () => {
    const sent = {};
    class FakeXhr {
      constructor() { this.upload = {}; this.headers = {}; }
      open(method, url) { sent.method = method; sent.url = url; }
      setRequestHeader(k, v) { this.headers[k] = v; }
      send(body) {
        sent.body = body;
        this.upload.onprogress?.({ lengthComputable: true, loaded: 5, total: 10 });
        this.status = 201;
        this.responseText = JSON.stringify({ analysis: { id: 'new-id' } });
        this.onload();
      }
    }
    vi.stubGlobal('XMLHttpRequest', FakeXhr);
    const progress = vi.fn();
    const files = [new File(['class A {}'], 'A.java'), new File(['class B {}'], 'B.java')];
    const result = await createAnalysis(files, { title: 'T', kgramSize: 5, normalizeNumbers: false, empty: '' }, progress);
    expect(result.analysis.id).toBe('new-id');
    expect(sent.method).toBe('POST');
    expect(sent.url).toBe(`${API_BASE_URL}/api/analyses`);
    expect(sent.body.getAll('files')).toHaveLength(2);
    expect(sent.body.get('kgramSize')).toBe('5');
    expect(sent.body.get('normalizeNumbers')).toBe('false');
    expect(sent.body.has('empty')).toBe(false);
    expect(progress).toHaveBeenCalledWith(0.5);
  });

  it('createAnalysis rejects with the server message on 413', async () => {
    class FakeXhr {
      constructor() { this.upload = {}; }
      open() {}
      setRequestHeader() {}
      send() { this.status = 413; this.responseText = JSON.stringify({ message: 'File too large' }); this.onload(); }
    }
    vi.stubGlobal('XMLHttpRequest', FakeXhr);
    const err = await createAnalysis([new File(['x'], 'A.java')], {}).catch((e) => e);
    expect(err.status).toBe(413);
    expect(err.message).toBe('File too large');
  });
});

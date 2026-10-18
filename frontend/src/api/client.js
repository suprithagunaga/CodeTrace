/**
 * Thin client for the CodeTrace Spring Boot API. Every UI feature goes through these functions;
 * there are no mock or hard-coded responses.
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(message, { status = 0, details = [], network = false } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = Array.isArray(details) ? details : [];
    this.network = network;
  }
}

const NETWORK_MESSAGE = `Cannot reach the CodeTrace backend at ${API_BASE_URL}. Make sure it is running and try again.`;

async function toApiError(response) {
  let message = `Request failed with status ${response.status}`;
  let details = [];
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string' && body.message) message = body.message;
    if (body && Array.isArray(body.details)) details = body.details;
  } catch {
    // non-JSON error body; keep the generic message
  }
  return new ApiError(message, { status: response.status, details });
}

async function request(path, { method = 'GET', body, signal, raw = false } = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      body,
      signal,
      headers: { Accept: raw ? '*/*' : 'application/json' },
    });
  } catch (err) {
    if (err?.name === 'AbortError') throw err;
    throw new ApiError(NETWORK_MESSAGE, { network: true });
  }
  if (!response.ok) throw await toApiError(response);
  if (raw) return response;
  if (response.status === 204) return null;
  return response.json();
}

function query(params) {
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') usp.set(k, String(v));
  });
  const s = usp.toString();
  return s ? `?${s}` : '';
}

/** Health returns a JSON body for both 200 (UP) and 503 (database DOWN). */
export async function getHealth(signal) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}/api/health`, { signal, headers: { Accept: 'application/json' } });
  } catch (err) {
    if (err?.name === 'AbortError') throw err;
    throw new ApiError(NETWORK_MESSAGE, { network: true });
  }
  if (response.status === 200 || response.status === 503) return response.json();
  throw await toApiError(response);
}

export const getConfig = (signal) => request('/api/config', { signal });
export const getDashboard = (signal) => request('/api/dashboard', { signal });

export const listAnalyses = ({ page = 0, size = 10, sort = 'createdAt', direction = 'desc', q = '' } = {}, signal) =>
  request(`/api/analyses${query({ page, size, sort, direction, q })}`, { signal });

export const getAnalysis = (id, signal) => request(`/api/analyses/${encodeURIComponent(id)}`, { signal });

export const getComparison = (id, comparisonId, signal) =>
  request(`/api/analyses/${encodeURIComponent(id)}/comparisons/${encodeURIComponent(comparisonId)}`, { signal });

export const deleteAnalysis = (id) => request(`/api/analyses/${encodeURIComponent(id)}`, { method: 'DELETE' });

/** Downloads an export; resolves to { blob, filename }. */
export async function exportAnalysis(id, format) {
  const response = await request(`/api/analyses/${encodeURIComponent(id)}/export${query({ format })}`, { raw: true });
  const disposition = response.headers.get('Content-Disposition') || '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const filename = match ? decodeURIComponent(match[1]) : `codetrace-analysis-${id}.${format}`;
  return { blob: await response.blob(), filename };
}

/**
 * Uploads files and runs an analysis. Uses XMLHttpRequest so upload progress can be reported.
 * @param {File[]} files
 * @param {object} options title, kgramSize, windowSize, jaccardNgram, normalizeIdentifiers, normalizeNumbers
 * @param {(fraction:number)=>void} [onProgress]
 */
export function createAnalysis(files, options = {}, onProgress) {
  const form = new FormData();
  files.forEach((f) => form.append('files', f, f.name));
  Object.entries(options).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') form.append(k, String(v));
  });
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE_URL}/api/analyses`);
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.upload.onprogress = (e) => {
      if (onProgress && e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onerror = () => reject(new ApiError(NETWORK_MESSAGE, { network: true }));
    xhr.ontimeout = () => reject(new ApiError('The analysis request timed out.', { network: true }));
    xhr.onload = () => {
      let body = null;
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        body = null;
      }
      if (xhr.status >= 200 && xhr.status < 300 && body) {
        resolve(body);
      } else {
        reject(new ApiError(body?.message || `Request failed with status ${xhr.status}`, {
          status: xhr.status,
          details: body?.details || [],
        }));
      }
    };
    xhr.send(form);
  });
}

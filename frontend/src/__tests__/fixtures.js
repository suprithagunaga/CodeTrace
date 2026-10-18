export const ANALYSIS_ID = '3f2b8c1e-0000-4000-8000-000000000001';

export const report = {
  analysis: {
    id: ANALYSIS_ID, title: 'Week 4 lab', createdAt: '2026-10-01T10:00:00Z', completedAt: '2026-10-01T10:00:02Z',
    status: 'COMPLETED', fileCount: 3, comparisonCount: 3, maxScore: 0.92,
  },
  configuration: { kgramSize: 5, windowSize: 4, jaccardNgram: 3, normalizationMode: 'IDENTIFIERS', normalizeNumbers: false, combinedScoreRule: 'overall = (jaccard + winnowing) / 2' },
  sourceRetained: true,
  files: [
    { id: 'f1', ordinal: 0, filename: 'Alpha.java', fileSize: 400, contentHash: 'a', tokenCount: 90, lineCount: 20, warnings: [] },
    { id: 'f2', ordinal: 1, filename: 'Beta.java', fileSize: 420, contentHash: 'b', tokenCount: 95, lineCount: 21, warnings: [] },
    { id: 'f3', ordinal: 2, filename: 'Gamma.java', fileSize: 300, contentHash: 'c', tokenCount: 60, lineCount: 15, warnings: ['Unterminated string literal at line 3'] },
  ],
  comparisons: [
    { id: 'c1', fileAId: 'f1', fileAName: 'Alpha.java', fileBId: 'f2', fileBName: 'Beta.java', jaccardScore: 0.9, winnowingScore: 0.94, overallScore: 0.92, sharedFingerprintCount: 30, regionCount: 2, warnings: [] },
    { id: 'c2', fileAId: 'f1', fileAName: 'Alpha.java', fileBId: 'f3', fileBName: 'Gamma.java', jaccardScore: 0.2, winnowingScore: 0.1, overallScore: 0.15, sharedFingerprintCount: 2, regionCount: 1, warnings: [] },
    { id: 'c3', fileAId: 'f2', fileAName: 'Beta.java', fileBId: 'f3', fileBName: 'Gamma.java', jaccardScore: 0.5, winnowingScore: 0.4, overallScore: 0.45, sharedFingerprintCount: 9, regionCount: 1, warnings: ['No shared token run'] },
  ],
  disclaimer: 'Similarity scores indicate potential overlap between source files.',
};

const SRC_A = 'class Alpha {\n  int add(int a, int b) {\n    return a + b;\n  }\n}\n';
const SRC_B = '// copied\nclass Beta {\n  int add(int x, int y) {\n    return x + y;\n  }\n}\n';

export const comparison = {
  id: 'c1', analysisId: ANALYSIS_ID,
  fileA: report.files[0], fileB: report.files[1],
  jaccardScore: 0.9, winnowingScore: 0.94, overallScore: 0.92, sharedFingerprintCount: 30,
  metadata: { fingerprintsA: 32, fingerprintsB: 33, containmentA: 0.94, containmentB: 0.91, coverageA: 1, coverageB: 0.9, winnowingApplicable: true, warnings: [] },
  regions: [
    { fileAStartOffset: 0, fileAEndOffset: SRC_A.length - 1, fileBStartOffset: 11, fileBEndOffset: SRC_B.length - 1,
      fileAStartLine: 1, fileAEndLine: 5, fileBStartLine: 2, fileBEndLine: 6, tokenLength: 21 },
  ],
  configuration: report.configuration,
  explanation: 'Jaccard compares the sets of 3-token shingles. Winnowing selected fingerprints.',
  sourceAvailable: true,
  fileASource: SRC_A,
  fileBSource: SRC_B,
  fileANormalizedLines: ['class T {', 'int M ( int V , int V ) {', 'return V + V ;', '}', '}'],
  fileBNormalizedLines: ['', 'class T {', 'int M ( int V , int V ) {', 'return V + V ;', '}', '}'],
  disclaimer: report.disclaimer,
};

export const page = (content, extra = {}) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1, ...extra });

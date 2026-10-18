# CodeTrace — Java Code Similarity Analyzer

CodeTrace compares Java source files and shows where they overlap. It tokenizes each file with a hand-written
Java scanner, normalizes identifiers, and measures similarity with **Jaccard** (token shingle sets) and
**Winnowing** (document fingerprinting). Results — scores, ranked file pairs and the exact matching code regions —
are stored in PostgreSQL and presented in a React dashboard with a side-by-side code view and CSV/JSON export.

> **Similarity is not proof.** Scores indicate potential overlap. Starter code, boilerplate, common algorithms and
> permitted collaboration all produce matches. Always review matched code in context before drawing conclusions.

---

## Contents

1. [Verification status — read this first](#1-verification-status--read-this-first)
2. [Prerequisites](#2-prerequisites)
3. [Quick start (VS Code)](#3-quick-start-vs-code)
4. [Project architecture](#4-project-architecture)
5. [Configuring PostgreSQL](#5-configuring-postgresql)
6. [Running the backend](#6-running-the-backend)
7. [Running the frontend](#7-running-the-frontend)
8. [Running the tests](#8-running-the-tests)
9. [REST API](#9-rest-api)
10. [Algorithms and their limitations](#10-algorithms-and-their-limitations)
11. [Security and data-retention decisions](#11-security-and-data-retention-decisions)
12. [Sample dataset and verified results](#12-sample-dataset-and-verified-results)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Verification status — read this first

The project was built in a sandbox that had Java 21, Maven 3.9, Node 22 and PostgreSQL 16, but **Maven Central was
blocked by the sandbox network policy** and **no Docker daemon was available**. That determines what could and could
not be executed there:

| Area | Status in the build environment |
|------|----------------------------------|
| Tokenizer, normalizer, Jaccard, Winnowing, region matching, pairwise service, upload validation, CSV export (**64 JUnit tests**) | ✅ Compiled with `javac` and **executed** (all pass). JUnit itself could not be downloaded, so the real test sources were run with a minimal JUnit-compatible runner. |
| All backend main code (controllers, services, JPA entities, repositories, exception handling) | ✅ Compiled with `javac` against hand-written API stubs of Spring/JPA/Jackson to catch type and signature errors. ⚠️ **Not compiled against the real Spring libraries, not started.** |
| Flyway migration `V1__create_schema.sql` | ✅ Applied to a real PostgreSQL 16; all constraints, cascade deletes, the retention delete and the native dashboard/search queries were executed and checked. ⚠️ Not run through Flyway itself. |
| Spring tests: WebMvc controller tests (17), Mockito service tests (8), PostgreSQL integration tests (10) | ⚠️ **Written but not executed** (need Maven dependencies; integration tests also need Docker or `TEST_DB_URL`). |
| Frontend `npm install`, `npm run build`, **45 Vitest/Testing Library tests** | ✅ Executed, all pass. |
| UI rendering | ✅ Production build screenshotted in headless Chromium at 1440/820/390 px using results produced by the real Java engine on `sample-data/` (no console errors, no horizontal overflow). |
| End-to-end: React UI ↔ running Spring Boot ↔ PostgreSQL | ⚠️ **Not executed** (backend could not be built). |
| Dockerfiles / `docker compose --profile app` | ⚠️ Not executed (no Docker daemon). |

**First thing to do on your machine:** `cd backend && mvn verify`. This compiles against the real libraries and runs all
99 backend tests (integration tests run automatically when Docker is available). If anything fails there, see
[Troubleshooting](#13-troubleshooting) — and treat that as a real defect, not something already verified.

---

## 2. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **21** | e.g. Eclipse Temurin 21. `java -version` must show 21. |
| Maven | 3.9+ | `mvn -v`. (No Maven wrapper is included.) |
| Node.js | 18.18+ (20 or 22 recommended) | `node -v`, with npm 9+. |
| PostgreSQL | 14+ (16 recommended) | Via Docker Compose (easiest) or a local install. |
| Docker | optional | For the bundled PostgreSQL and for the Testcontainers integration tests. |
| VS Code | optional | Extensions are recommended in `.vscode/extensions.json`. |

---

## 3. Quick start (VS Code)

```bash
# 0) open the folder
code CodeTrace

# 1) database (Docker)
cp .env.example .env               # optional; the defaults already match
docker compose up -d postgres      # PostgreSQL 16 on localhost:5432 (db/user/password: codetrace)

# 2) backend  (terminal 1)
cd backend
mvn spring-boot:run                # Flyway creates the schema on first start → http://localhost:8080

# 3) frontend (terminal 2)
cd frontend
npm install
npm run dev                        # → http://localhost:5173
```

Open <http://localhost:5173>, go to **New Analysis**, drop the files from `sample-data/`, and click **Run analysis**.

In VS Code you can also start the backend from the **Run and Debug** panel (“CodeTrace backend (Spring Boot)”, defined
in `.vscode/launch.json`) once the Java extension pack has imported the Maven project.

---

## 4. Project architecture

```
CodeTrace/
├── backend/                         Spring Boot 3.3 · Java 21 · Maven
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/codetrace/
│       │   ├── CodeTraceApplication.java
│       │   ├── config/        CodeTraceProperties (codetrace.*), WebConfig (CORS)
│       │   ├── controller/    AnalysisController (/api/analyses, /api/dashboard), SystemController (/api/health, /api/config)
│       │   ├── dto/           API records (no entity is ever serialized)
│       │   ├── entity/        Analysis, SourceFile, SourceFileContent, ComparisonResult, MatchingRegion
│       │   ├── exception/     GlobalExceptionHandler (@RestControllerAdvice), ApiValidationException, ...
│       │   ├── repository/    Spring Data JPA repositories
│       │   ├── service/       FileIntakeService, TokenizationService, PairwiseComparisonService,
│       │   │                  AnalysisService (persistence/history/report), ReportExportService, RetentionService
│       │   ├── similarity/    JaccardSimilarity, Winnowing, MatchRegionFinder, SimilarityEngine  ← no Spring
│       │   ├── tokenizer/     JavaTokenizer, TokenNormalizer                                    ← no Spring
│       │   └── validation/    SourceUploadValidator, FilenameSanitizer                          ← no Spring
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__create_schema.sql   (Flyway)
│       └── test/java/com/codetrace/  unit, WebMvc, Mockito and integration tests
├── frontend/                        React 18 · Vite 5 · Recharts · lucide-react
│   ├── src/api/client.js            the only place that talks to the backend
│   ├── src/pages/                   Dashboard, NewAnalysis, AnalysisResults, ComparisonView, History
│   ├── src/components/              Layout, CodeViewer, FileDropzone, ConfirmDialog, States, ...
│   ├── src/utils/javaHighlight.js   syntax colouring + exact region overlay from backend offsets
│   └── src/__tests__/               Vitest + Testing Library
├── sample-data/                     non-sensitive Java files for trying the app
├── docs/CodeTrace.postman_collection.json
├── docker-compose.yml               PostgreSQL (+ optional app containers via the "app" profile)
└── .env.example
```

**Request flow:** `Controller → Service → Repository → PostgreSQL`.
The CPU-heavy comparison runs outside any transaction; persisting an analysis happens in a single write transaction
(all rows or nothing). Reads use read-only transactions.

**Where each spec'd “service” lives:** file validation & intake → `FileIntakeService` + `SourceUploadValidator`;
tokenization & normalization → `TokenizationService` (wrapping `JavaTokenizer`/`TokenNormalizer`); Jaccard →
`JaccardSimilarity`; Winnowing → `Winnowing`; pairwise comparison → `PairwiseComparisonService` + `SimilarityEngine`;
history & reports → `AnalysisService` + `ReportExportService`. The algorithm classes are deliberately framework-free
so they can be unit-tested without Spring.

**Frontend UI note:** the side-by-side view uses a purpose-built code viewer (the spec allowed “Monaco or another
suitable viewer”). It renders original line numbers, highlights the exact character ranges the backend reports,
supports synchronized scrolling and a normalized-token view, and never injects HTML.

**Database schema** (Flyway, `V1__create_schema.sql`):

| Table | Key columns | Notes |
|-------|-------------|-------|
| `analysis` | id, title, created_at, completed_at, status, file_count, comparison_count, max_score, algorithm_configuration (JSON text), source_retained | check constraints on status/counts/score |
| `source_file` | id, analysis_id → analysis, ordinal, original_filename, display_name, file_size, content_hash (SHA-256), token_count, line_count, warnings | unique (analysis_id, ordinal) and (analysis_id, display_name) |
| `source_file_content` | source_file_id → source_file, content | only written when source retention is on |
| `comparison_result` | id, analysis_id, file_a_id, file_b_id, jaccard_score, winnowing_score, overall_score, shared_fingerprint_count, region_count, result_metadata (JSON) | unique pair per analysis, `file_a_id <> file_b_id`, scores in [0,1] |
| `matching_region` | id, comparison_result_id, file_{a,b}_{start,end}_offset, file_{a,b}_{start,end}_line, token_length | offsets are character offsets, end exclusive |

Every foreign key is `ON DELETE CASCADE`, so deleting an `analysis` row removes all of its records.
Hibernate runs with `ddl-auto: none`; Flyway owns the schema.

---

## 5. Configuring PostgreSQL

**Option A — Docker (recommended)**

```bash
docker compose up -d postgres
docker compose ps        # wait for "healthy"
```

Data is kept in the `codetrace-pgdata` volume (survives restarts). `docker compose down -v` deletes it.

**Option B — existing PostgreSQL**

```sql
CREATE USER codetrace WITH PASSWORD 'choose-a-password';
CREATE DATABASE codetrace OWNER codetrace;
```

Then point the backend at it with environment variables (see below). Flyway creates the tables on first start.

**Backend database settings** (environment variables; defaults in `application.yml` match Docker Compose):

| Variable | Default |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/codetrace` |
| `DB_USERNAME` | `codetrace` |
| `DB_PASSWORD` | `codetrace` |

Spring Boot does **not** read `.env` files. If you change the password, export the variables in the terminal that runs
the backend:

```bash
# macOS / Linux
export DB_PASSWORD=choose-a-password
# Windows PowerShell
$env:DB_PASSWORD = "choose-a-password"
```

…or edit the `env` block in `.vscode/launch.json` when launching from VS Code.

---

## 6. Running the backend

```bash
cd backend
mvn spring-boot:run                 # development
# or
mvn -DskipTests package && java -jar target/codetrace-backend.jar
```

Check it: <http://localhost:8080/api/health> → `{"status":"UP", ..., "database":{"status":"UP"}}`.

Other settings (all optional):

| Variable | Default | Meaning |
|----------|---------|---------|
| `SERVER_PORT` | 8080 | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | comma-separated origins allowed to call `/api/**` |
| `UPLOAD_MAX_FILES` | 50 | files per analysis |
| `UPLOAD_MAX_FILE_BYTES` | 524288 | bytes per file (512 KB) |
| `UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE` | 1MB / 30MB | servlet multipart hard limits |
| `RETENTION_STORE_SOURCE` | true | keep source text for the side-by-side view |
| `RETENTION_DAYS` | 30 | auto-delete analyses older than N days (0 = never) |

Algorithm defaults (k = 5, w = 4, 3-token shingles, identifier normalization on, number normalization off) are in
`application.yml` under `codetrace.similarity` and can be overridden per analysis from the UI.

---

## 7. Running the frontend

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173 (hot reload)
npm run build          # production bundle in dist/
npm run preview        # serve dist/ on http://localhost:4173
```

The API base URL comes from `VITE_API_BASE_URL` (default `http://localhost:8080`). To change it, copy
`frontend/.env.example` to `frontend/.env.local` and edit it. If you serve the UI from another origin, add that origin to
`CORS_ALLOWED_ORIGINS` on the backend.

**Pages:** Dashboard · New Analysis (drag-and-drop, paste code, options, progress) · Analysis Results (sort, filter,
legend, CSV/JSON export, delete) · Code Comparison (side-by-side, region list, original/normalized view, synced
scrolling) · History (search, sort, page size, pagination, delete with confirmation).

---

## 8. Running the tests

### Backend

```bash
cd backend
mvn test         # or: mvn verify
```

| Suite | Tests | Needs |
|-------|-------|-------|
| `tokenizer/*Test`, `similarity/*Test` — tokenizer, normalizer, Jaccard, Winnowing, engine | 46 | nothing |
| `validation/*Test`, `service/CsvWriterTest`, `ReportCsvTest`, `PairwiseComparisonServiceTest`, `AnalysisServiceHelpersTest` | 18 | nothing |
| `service/AnalysisServiceTest` (Mockito, mocked repositories) | 8 | nothing |
| `controller/AnalysisControllerTest` (`@WebMvcTest`, status codes, validation, error bodies, exports, health) | 17 | nothing |
| `integration/AnalysisApiIntegrationTest` (full Spring context + real PostgreSQL: upload → persist → retrieve, regions vs. source, n(n−1)/2, invalid uploads, duplicates, pagination/search, exports, cascade delete, dashboard) | 10 | Docker **or** `TEST_DB_URL` |

The integration class is **skipped** (not failed) when neither Docker nor `TEST_DB_URL` is available. To run it against
an existing database:

```bash
createdb codetrace_test                       # dedicated DB — the tests delete all analyses in it
export TEST_DB_URL=jdbc:postgresql://localhost:5432/codetrace_test
export TEST_DB_USERNAME=codetrace TEST_DB_PASSWORD=codetrace
mvn test -Dtest=AnalysisApiIntegrationTest
```

### Frontend

```bash
cd frontend
npm test          # Vitest, 45 tests: upload/removal, validation, loading & error states, real-response rendering,
                  # sorting/filtering, comparison navigation & views, export downloads, history refresh/delete
```

Component tests replace the API client with Vitest mocks; the application code itself has no mock data.

### Manual acceptance checklist

1. `docker compose up -d postgres`, start backend → log shows Flyway migrating `V1`.
2. `GET /api/health` → 200, database UP. Stop PostgreSQL → 503, database DOWN; the UI shows a banner.
3. Upload the six `sample-data` files → 15 pairs; `BubbleSort.java ↔ ArraySorter.java` = 100 %.
4. Restart the backend → the analysis is still in History (stored in PostgreSQL).
5. Export CSV/JSON → files contain the saved scores.
6. Delete the analysis → `select count(*) from source_file` etc. no longer contain its rows.

---

## 9. REST API

Base URL `http://localhost:8080`. A Postman collection is in `docs/CodeTrace.postman_collection.json`
(run “Create analysis” first; it stores the ids used by the other requests).

| Method & path | Description | Success |
|---------------|-------------|---------|
| `GET /api/health` | Application + database connectivity | 200 (UP) / 503 (database DOWN) |
| `GET /api/config` | Upload limits, retention, algorithm defaults | 200 |
| `POST /api/analyses` | `multipart/form-data`: `files` (≥ 2 `.java` parts), optional `title`, `kgramSize` (2–50), `windowSize` (1–100), `jaccardNgram` (1–10), `normalizeIdentifiers`, `normalizeNumbers`. Runs all algorithms and persists the result. | 201 + report, `Location` header |
| `GET /api/analyses?page&size&sort&direction&q` | Paginated history. `sort` ∈ createdAt, title, fileCount, comparisonCount, maxScore; `direction` ∈ asc, desc; `size` ≤ 100; `q` searches title and filenames (wildcards escaped) | 200 |
| `GET /api/analyses/{id}` | Full saved report (summary, configuration, files, every pair) | 200 / 404 |
| `GET /api/analyses/{id}/comparisons/{comparisonId}` | Pair detail: scores, containment, coverage, matching regions (offsets + lines), explanation, source text and normalized token lines (if retained) | 200 / 404 |
| `DELETE /api/analyses/{id}` | Deletes the analysis and all associated rows | 204 / 404 |
| `GET /api/analyses/{id}/export?format=csv` | CSV attachment (one row per pair) | 200 |
| `GET /api/analyses/{id}/export?format=json` | JSON attachment of the saved report | 200 |
| `GET /api/dashboard` | Totals, 10-bucket score histogram, top pairs, recent analyses | 200 |
| `GET /actuator/health` | Spring Boot Actuator liveness (no details) | 200 |

Scores are fractions in `[0, 1]`; the UI shows them as percentages.

**Errors** always use one JSON shape and never include stack traces, SQL or credentials:

```json
{
  "timestamp": "2026-10-15T16:20:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "1 file(s) failed validation",
  "path": "/api/analyses",
  "details": [{ "field": "notes.txt", "message": "Unsupported file type; allowed: .java" }]
}
```

| Status | When |
|--------|------|
| 400 | fewer than 2 valid files, bad extension, empty/whitespace/binary/non-UTF-8 file, too many files, bad options, invalid UUID, bad sort/page/format |
| 404 | unknown analysis or comparison |
| 413 | a file above the per-file limit, or a request above the multipart limit |
| 415 | `POST /api/analyses` without multipart content |
| 503 | database unavailable |
| 500 | unexpected error (details only in the server log) |

---

## 10. Algorithms and their limitations

### 1. Tokenizer (`JavaTokenizer`)
A single-pass hand-written scanner (no regex comment stripping). It recognizes keywords, identifiers, numeric literals
(hex, binary, underscores, exponents, suffixes), string/char literals with escapes, text blocks, operators (longest
match) and separators. Whitespace and comments are skipped, so `"// not a comment"` inside a string stays a string.
Every token keeps its character offsets, line and column. Malformed input (unterminated strings, comments, text blocks,
unknown characters) never throws; it produces warnings shown in the UI.

### 2. Normalization (`TokenNormalizer`)
* **Original mode** — tokens are compared as written.
* **Identifier mode** (default) — a lexical role analysis finds names *declared in the file* and replaces every
  occurrence with a role placeholder: `T` (class/interface/enum/record), `M` (method), `V` (variable, field, parameter,
  for-each and single-parameter lambda variables). Keywords, operators, package/import names, annotation names and
  names *not* declared in the file (library APIs such as `System.out.println`, `size`, `toString`) stay verbatim,
  because renaming those would change meaning. String/text-block literals become `<STR>`, char literals `<CHR>`.
* **Number normalization** (optional) — numeric literals become `<NUM>`.

Example: `public int add(int a, int b) { return a + b; }` and `public int plus(int x, int y) { return x + y; }` both
normalize to `public int M ( int V , int V ) { return V + V ; }`.

Placeholders are intentionally **not numbered** (`V1`, `V2`…): numbering by first appearance makes a method copied into a
larger file receive different numbers and hides partial copies (this was observed and fixed during development).

**Limits:** lexical, not semantic — scopes and shadowing are ignored; different variables are not distinguished
(`a = b + c` and `a = a + a` look alike); multi-parameter lambdas `(a, b) ->` are not recognized as declarations; an
expression such as `x > y;` can be mistaken for a declaration of `y`. These affect only how strongly renamed code
matches, never correctness of the program.

### 3. Jaccard similarity (`JaccardSimilarity`)
`J(A, B) = |A ∩ B| / |A ∪ B|` over the **sets** of n-token shingles (default n = 3; n = 1 gives plain token sets).
Empty inputs return 0; the value is in [0, 1] and symmetric. It measures shared vocabulary/short phrases and ignores
order and repetition — it is set similarity, not sequence similarity.

### 4. Winnowing (`Winnowing`)
1. Hash every k-gram of normalized tokens (64-bit FNV-1a over token hashes + SplitMix finalizer; deterministic).
2. Slide a window of w hashes; select the **minimum** (signed 64-bit comparison), the **rightmost** one on ties, and
   record it only if its position differs from the previous window's selection.
3. **k** is the noise threshold (shorter matches are never seen); **w** controls density (~2/(w+1) of k-grams kept).
   Any shared run of at least **w + k − 1** tokens (8 with defaults) is guaranteed to share a fingerprint.
4. Fewer than k tokens → no fingerprints (warning). Fewer than w k-grams → one window, one fingerprint.

**Winnowing score** = Jaccard of the two fingerprint-hash sets (symmetric). Directional containment
(`|FA ∩ FB| / |FA|`, `|FA ∩ FB| / |FB|`) is reported separately and labelled as directional.

### 5. Matching regions (`MatchRegionFinder`)
Each pair of positions sharing a fingerprint is verified token-by-token (hash collisions never create regions),
extended left and right while tokens match, then accepted longest-first without overlaps (deterministic greedy tiling).
Regions shorter than w + k − 1 tokens are dropped so short boilerplate runs are not highlighted. Token ranges are
mapped back to character offsets and line numbers of the original files.

### 6. Overall score
`overall = (jaccard + winnowing) / 2` — an unweighted mean; if Winnowing is not applicable it equals `jaccard`.
It is a ranking aid, **not** a calibrated probability. Every file pair is compared exactly once (i < j), giving
`n(n−1)/2` pairs, and every score is symmetric.

UI score bands (display only): Low < 30 %, Moderate 30–60 %, High 60–80 %, Very high ≥ 80 %.

### General limitations
* Evidence of overlap, not proof of plagiarism; common algorithms and starter code score high legitimately.
* Reordering statements, extracting methods or changing control structures (`for` ↔ `while`) reduces matches.
* Java only; Unicode escapes outside literals are not pre-translated.
* Analysis is synchronous; very large batches (e.g. 50 large files = 1 225 pairs) take noticeably longer.

---

## 11. Security and data-retention decisions

* **Uploaded code is untrusted data.** It is decoded strictly as UTF-8 and tokenized in memory. It is never
  compiled, executed, evaluated or written to a public directory; there is no command execution anywhere.
* **Validation on the server** regardless of the client: file count, per-file size (413), `.java` extension on the
  sanitized name, empty/whitespace/binary (NUL) and invalid UTF-8 content. Client-supplied MIME types are ignored.
* **Filenames** are sanitized (directory parts removed → no path traversal; unsafe characters replaced; length
  limited) and only ever used as labels. React escapes all text; the code viewer never uses `innerHTML`.
* **Database access** uses JPA/JPQL with bound parameters; sort fields are whitelisted; LIKE wildcards in searches are
  escaped.
* **CSV exports** neutralize cells beginning with `= + - @`, tab or CR (spreadsheet formula injection).
* **Errors** never expose stack traces, SQL or credentials (`server.error.include-stacktrace: never`, uniform handler).
* **Logging** records analysis ids, counts and timings only — never file contents.
* **CORS** is limited to configured origins (default: the Vite dev server).
* **Secrets**: no real credentials are committed; `.env` is git-ignored and `.env.example` holds local placeholders.
* **Retention:**
  * Stored for every analysis: filenames, sizes, SHA-256 hashes, token/line counts, scores, matching regions.
  * Source text: stored in `source_file_content` only if `RETENTION_STORE_SOURCE=true` (default). This is what makes
    the side-by-side view work after upload. **Implication:** anyone with database access can read uploaded code. Set
    it to `false` to keep metadata only; the comparison page then lists matching line ranges without code.
  * Analyses older than `RETENTION_DAYS` (default 30) are deleted by a nightly job (03:00 server time); `0` disables it.
  * Deleting an analysis (UI or `DELETE /api/analyses/{id}`) removes all its rows, including retained source text,
    through `ON DELETE CASCADE`.
* **Not included:** authentication/authorization. Run CodeTrace on a trusted network or put it behind your SSO/reverse
  proxy before exposing it.

---

## 12. Sample dataset and verified results

`sample-data/` contains six non-sensitive files (see `sample-data/README.md`). These values were produced by the
project's own Java engine with default settings and are the expected output of the app:

| File A | File B | Overall | Jaccard | Winnowing | Region(s) |
|--------|--------|--------:|--------:|----------:|-----------|
| ArraySorter.java | BubbleSort.java | **100.0 %** | 100.0 % | 100.0 % | whole file (renamed + reformatted + recommented) |
| ArraySorter.java | LibraryManager.java | 32.6 % | 35.7 % | 29.5 % | A 8–31 ↔ B 34–52 (copied method, 120 tokens) |
| BubbleSort.java | LibraryManager.java | 32.6 % | 35.7 % | 29.5 % | A 8–26 ↔ B 34–52 (copied method, 120 tokens) |
| BinarySearch.java | RecursiveSearch.java | 25.7 % | 33.9 % | 17.5 % | short shared fragments |
| other pairs | | 2.7 – 10.1 % | | | none or one short region |

---

## 13. Troubleshooting

| Symptom | Fix |
|---------|-----|
| UI banner “Cannot reach the CodeTrace API” | Start the backend; check `VITE_API_BASE_URL`; open <http://localhost:8080/api/health>. |
| Browser console shows a CORS error | Add your UI origin to `CORS_ALLOWED_ORIGINS` (comma-separated) and restart the backend. |
| `Connection to localhost:5432 refused` at startup | PostgreSQL is not running: `docker compose up -d postgres`, wait until healthy. |
| `password authentication failed for user "codetrace"` | The backend password differs from the database's. Export `DB_PASSWORD` (Spring does not read `.env`). If the Docker volume was created earlier with another password, either use that one or `docker compose down -v` (deletes data). |
| `Port 5432 is already allocated` | Another PostgreSQL is running: stop it or set `POSTGRES_PORT=5433` in `.env` and `DB_URL=jdbc:postgresql://localhost:5433/codetrace`. |
| `Port 8080 already in use` / `Port 5173 is in use` | Set `SERVER_PORT` (and `VITE_API_BASE_URL`), or stop the other process. Vite uses `strictPort`. |
| `release version 21 not supported` / `invalid target release: 21` | Maven is running on an older JDK. Point `JAVA_HOME` to JDK 21 (`mvn -v` shows the JDK in use). |
| `Flyway ... Validate failed: Migration checksum mismatch` | The migration file was edited after it ran. For local data you can drop and recreate the database (`docker compose down -v`). |
| Integration tests are “skipped” | Docker is not available. Start Docker, or set `TEST_DB_URL` (see §8). |
| `Could not find a valid Docker environment` | Same as above; Testcontainers needs a running Docker daemon. |
| 413 on upload | File above 512 KB (or request above 30 MB). Raise `UPLOAD_MAX_FILE_BYTES` and `UPLOAD_MAX_FILE_SIZE` together. |
| Comparison page says source was not retained | The analysis ran with `RETENTION_STORE_SOURCE=false`; re-run it with retention enabled. |
| `npm install` fails behind a proxy | Configure `npm config set proxy/https-proxy`, or use Node 20/22 LTS. |
| VS Code does not recognize the Java project | Install the recommended extensions, then “Java: Clean Java Language Server Workspace”. Open the repository root or `backend/`. |

---

### Tech stack

Java 21 · Spring Boot 3.3.5 (Web, Validation, Data JPA, Actuator) · Hibernate 6 · Flyway 10 · PostgreSQL 16 ·
JUnit 5 · Mockito · Testcontainers · React 18 · Vite 5 · React Router 6 · Recharts 2 · lucide-react · Vitest ·
Testing Library.

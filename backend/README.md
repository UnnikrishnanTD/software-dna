# Software DNA — Backend

The analysis engine behind the Software DNA frontend. Give it a public GitHub
repository and it clones it, reads its structure and history, and produces a
Software DNA profile: eight scored dimensions, a module dependency graph,
ranked hotspots, a dependency surface, an evolution timeline, and
evidence-backed findings.

---

## Running it

**No Docker or PostgreSQL installed?** The application can start its own:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=localdb
```

That downloads a real PostgreSQL binary on first run, starts it, migrates it,
and discards the data on shutdown. Never use this profile in a deployed
environment.

**With Docker:**

```bash
docker compose up -d
cp .env.example .env      # then edit
./mvnw spring-boot:run
```

Either way the API is at `http://localhost:8080`, with OpenAPI at
[`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) and health at
`/actuator/health`.

```bash
./mvnw test                        # unit + integration, no network
./mvnw test -DexcludedGroups=      # also the tests that reach GitHub
```

Maven itself is not required: `./mvnw` bootstraps it, so a JDK 21+ is the only
prerequisite.

---

## Two conventions that run through everything

Read these before reading any response, because they explain most of the design.

### 1. Null means *not measured*, never zero

A repository with no coverage report and a repository with genuinely zero
coverage are different findings. Any system that cannot tell them apart will
confidently report the wrong one.

So `testCoverage`, `advisories`, `latestVersion`, `transitive` and `percentile`
are null until something actually measured them, and the UI renders them as
"—" or "not scanned" rather than as a number.

The most important case: **test coverage cannot be measured.** Producing it
means executing the repository's test suite, and this service will never run
code from an arbitrary repository. Coverage is only reported when the
repository itself commits an LCOV or JaCoCo report. Otherwise the Testing
dimension is scored from test *presence*, its confidence drops by 35 points,
and its summary says so in words.

The same applies to security: no vulnerability database is consulted unless one
is configured, so advisories are null rather than zero. "We did not look" must
never render as "we looked and found nothing."

### 2. Every score is traceable

A dimension score is never a bare number. Each carries:

- **`confidence`** (0–100), which falls as inputs go missing;
- **`evidence`**, a map of the metric keys and values that produced it;
- **`strengths`** and **`watchItems`**, each conditional on a measured value.

Every metric is also stored in the `metric` table, including the ones that
could not be measured, with the reason. Any number in a profile can be traced
back to what was measured.

Scoring formulas are documented on each module in
`analysis/engine/module/` — thresholds, weights and caps, in prose, next to the
code that applies them.

---

## Architecture

```
Angular
  ↓  REST
AnalysisController ──► AnalysisSubmissionService ──► bounded worker pool
                                                          ↓
                                              AnalysisOrchestrator
                                                          ↓
     RepositoryProvider → clone → RepositoryScanner → LanguageAnalyzer
                                                          ↓
        GitHistoryAnalyzer · ArchitectureGraphBuilder · ManifestParser
                                                          ↓
                       AnalysisModule × 8 → DnaScoringService
                                                          ↓
                                      AnalysisResultWriter → PostgreSQL
                                                          ↓
                       AnalysisReadService ──► RepositoryAnalysisDto
```

Submission returns a `202` immediately with an id. The pipeline runs on a
bounded pool and the client polls `/status`; the frontend turns those snapshots
back into the observable stream it was always built against, so replacing
polling with SSE later changes one file on each side and nothing else.

### Package layout

| Package | Responsibility |
|---|---|
| `repository` | Coordinates parsing, GitHub provider, persistence of repositories |
| `analysis.workspace` | Sandboxed clone directories, JGit checkout, limits |
| `codebase` | Scanner, language analyzers, lexical signal detection |
| `architecture` | Import resolution, module grouping, graph and cycle detection |
| `evolution` | Git history, contributors, timeline and milestones |
| `dependency` | Manifest parsing, technology profiling |
| `analysis.engine` | Scoring framework and the eight dimension modules |
| `analysis.findings` | Issues and ranked recommendations |
| `api` | DTOs, controllers, read assembly, comparison |
| `ai` | Provider abstraction and the deterministic implementation |

---

## Security

The repository is treated as hostile input throughout.

**No shell, ever.** Cloning uses JGit, a pure-Java implementation. There is no
argument string and no shell, so command injection is not mitigated — it is
absent as a category. Repository source is never executed; the analyzer is
static only.

**SSRF is prevented by an allow-list, not a filter.**
`RepositoryCoordinates.parse` extracts the host explicitly and accepts only
`github.com`. A link-local metadata address, an internal hostname, a
`file://` URL or a non-HTTP scheme cannot be expressed in a form the parser
returns. 66 tests cover this boundary, including real SSRF and traversal
payloads.

**Path traversal cannot escape the workspace.** Every read goes through
`AnalysisWorkspace.resolve`, which normalises and then refuses any path outside
the checkout. Symbolic links are never followed during the scan, so a link to
`/etc/passwd` reads nothing.

**XXE is disabled** on both XML parsers — `pom.xml` and JaCoCo reports both come
from the repository.

**Resource exhaustion is bounded** at every stage: repository size checked
before transfer and again after, per-file size cap, file-count cap, commit
cap, clone timeout, bounded worker pool and bounded queue. Reaching a limit
fails with a specific error code, and a truncated scan lowers the confidence of
everything derived from it.

**Secrets never leave the server.** The GitHub token is attached in one place,
excluded from `toString`, never logged, and never written into a clone URL that
could persist into git config. Error responses carry a code and a message —
never a stack trace, an exception class, or an internal identifier.

---

## Performance

Analysis output is written once and read back as projections, so persistence is
split by access pattern rather than by preference: **JPA for the two aggregate
roots** that have a lifecycle, **batched JDBC for the bulk rows**. Pushing tens
of thousands of file rows through a persistence context would hold every one in
memory as a managed entity and pay for dirty checking that can never fire.

Reads are explicit queries into DTOs. There is no lazy proxy, so N+1 is absent
by construction, and no JPA entity is ever serialised to a client.

The architecture graph is aggregated to module level with an automatic grouping
depth, so a large repository returns a readable graph rather than a truncated
one. Build-layout prefixes (`src/main/java`) and the directory prefix common to
every file are stripped first — a prefix shared by the whole repository
distinguishes nothing, and leaving it in collapses a deep package structure
into a single node.

---

## What is measured, and how well

| Signal | Basis | Confidence |
|---|---|---|
| Lines, files, languages | Direct count | Exact |
| Cyclomatic complexity (Java) | JavaParser AST | Exact |
| Cyclomatic complexity (TS/JS) | Decision points over comment- and string-masked source | High |
| Imports, declarations | Parse (Java) / lex (TS) | High |
| Commits, contributors, churn, file history | JGit | Exact |
| Bug-fix commits | Commit-subject convention | Heuristic, weighted as one |
| Module graph, cycles | Resolved imports, Tarjan | Exact for what resolves |
| Dependencies | Manifest parse | Direct only |
| Committed credentials | Provider-specific key formats | High precision, not exhaustive |
| Performance patterns | Lexical | Confidence capped at 60 |
| **Test coverage** | Committed report only | **Absent otherwise** |
| **Vulnerabilities** | Not checked by default | **Absent otherwise** |
| Transitive dependencies, package sizes, licences | Not resolved | **Absent** |

TypeScript has no production-grade JVM parser, so its analyzer is lexical
rather than syntactic. It runs over masked source so keywords in comments and
strings are never counted, and it does not resolve path aliases — those imports
count as external, which understates internal coupling rather than inventing
it.

---

## API

| Endpoint | Purpose |
|---|---|
| `POST /api/analyses` | Start an analysis. `202` with an id. |
| `GET /api/analyses` | Completed analyses, newest first |
| `GET /api/analyses/{id}` | The full profile |
| `GET /api/analyses/{id}/status` | Progress, derived from completed stages |
| `DELETE /api/analyses/{id}` | Cancel |
| `GET /api/analyses/{id}/{section}` | One section: `architecture`, `codebase`, `hotspots`, `dependencies`, `evolution`, `recommendations` |
| `GET /api/analyses/compare?left=&right=` | Side-by-side comparison |
| `POST /api/ai/questions` | Ask the Doctor about an analysis |

Errors are one shape everywhere: `timestamp`, `status`, `code`, `message`,
`path`, and field-level `errors` for validation failures.

---

## Extending it

- **A language** — implement `LanguageAnalyzer`, declare the languages it
  supports, register it as a bean. The scanner picks it up.
- **A dimension** — implement `AnalysisModule`. `DnaScoringService` collects
  every module and renormalises the weights.
- **A repository host** — implement `RepositoryProvider`. Nothing in the engine
  refers to GitHub.
- **An AI model** — implement `AiProvider` and set `AI_PROVIDER`. Providers
  receive the structured analysis, never repository source, so customer code is
  never sent to a third party.
- **Vulnerability scanning** — populate `advisoryCount` on `ResolvedDependency`
  from a real source. Security scoring already handles it; until then it stays
  null, and the absence is reported rather than assumed away.

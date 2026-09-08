# Software DNA

**Every software system has a DNA.**

Software DNA reads a repository and generates a visual DNA profile of its
engineering health across eight dimensions — architecture, maintainability,
security, performance, testing, dependencies, documentation and evolution.

This repository contains both halves:

- **`src/`** — the Angular frontend.
- **`backend/`** — a Spring Boot analysis engine that clones a real GitHub
  repository, reads its structure and history, and produces the profile.

The frontend runs against either. With `useBackend: false` in
`src/environments/environment.ts` it serves a self-contained sample dataset and
needs nothing else; with it `true` every screen reads from the API.

---

## Running it

**Frontend only**, against the sample dataset — set `useBackend: false` in
`src/environments/environment.ts`, then:

```bash
npm install
npm start
```

**Both**, analysing real repositories. In one terminal:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=localdb
```

That starts the API on `:8080` with its own PostgreSQL — no Docker or database
install needed. Then in another:

```bash
npm start
```

Open <http://localhost:4200>, paste any public GitHub repository, and watch it
be cloned, analysed and scored.

```bash
npm run build                                  # production build
npm test -- --watch=false --browsers=ChromeHeadless   # 88 unit tests

cd backend && ./mvnw test                      # 106 tests, no network
cd backend && ./mvnw test -DexcludedGroups=    # 115, including live GitHub
```

The backend has its own [README](backend/README.md) covering its architecture,
security posture, and — importantly — what it can and cannot measure.

---

## Phase 1 scope

**Built:** the full product experience — landing, repository input, the
analysis sequence, and eight explorable screens over a realistic analysed
repository.

**Deliberately not built yet:** Spring Boot, PostgreSQL, GitHub OAuth,
repository cloning, real static analysis, a real AI model, authentication,
deployment. Those belong to later phases, and the architecture below is
shaped so they can land without rewriting the UI.

### What happens when you paste a repository URL

**With the backend connected**, the repository is cloned into an isolated
workspace, analysed statically and then deleted. No repository code is ever
executed.

**Without it**, arbitrary repositories cannot be fetched, and the interface
says so rather than implying otherwise: a notice appears *before* you submit,
naming the repository and explaining that the sample dataset will be used
instead, and a **Sample** chip sits in the header so no screen can be mistaken
for a live analysis.

### Measurements that may be absent

The backend reports `null` for anything it did not measure, and the UI renders
that as "—" or "not scanned" rather than as a zero. The most important case is
**test coverage**: producing it means running the repository's test suite, and
the analyser never executes repository code. Coverage appears only when the
repository itself commits an LCOV or JaCoCo report; otherwise the Testing
dimension is scored from test presence with its confidence reduced, and says
so. The same applies to dependency vulnerabilities: "not checked" and "none
found" are different claims and are never conflated.

---

## Toolchain

| | |
|---|---|
| Angular | 18.2 — standalone components, signals, new control flow |
| TypeScript | 5.5, `strict` + `strictTemplates` |
| Change detection | `OnPush` everywhere, signal-driven |
| Runtime dependencies | **Angular and RxJS only** |
| SSR | Not enabled |
| Tests | Karma + Jasmine, 77 specs |

> **On the Angular version.** This machine runs Node v20.9.0. Angular 19 and
> 20 declare `node: ^20.11.1` and the CLI hard-exits on an unsatisfied
> engine, so they will not run here. Angular 18 provides everything this
> product needs. On Node 22 LTS, moving to Angular 20 is a routine
> `ng update`.

---

## Architecture

```
src/app/
  core/
    models/      Domain types — the contract between UI and any data source
    services/    AnalysisService (abstract) + MockAnalysisService + AnalysisStore
    mock/        The Atlas and Nova datasets, and the graph/risk derivations
    util/        The health scale: score → verdict → colour, in one place
  shared/
    dna/         Helix geometry, the canvas hero, the interactive SVG DNA
    ui/          Panel, numeral, meter, badges, icons, state views
    util/        Motion helpers
  features/      One folder per screen, each lazy-loaded
  layout/        The application shell and navigation
```

### The data seam

This is the most important structural decision in the codebase.

```
AnalysisService  (abstract class, used as the DI token)
     ├── MockAnalysisService   ← today
     └── HttpAnalysisService   ← later
```

Components inject the **abstract** `AnalysisService` and never learn where
data comes from. Swapping to a real backend is one line in
`src/app/app.config.ts`:

```ts
{ provide: AnalysisService, useClass: HttpAnalysisService }
```

No component changes. `AnalysisStore` is provided on the analysis shell
route, so the shell loads the analysis once and every child route reads from
it — no child route fetches anything.

### Internally consistent mock data

The dataset is not hand-typed numbers that can drift apart. Derived values
are computed from the source of truth:

- **Fan-in / fan-out** are counted from the actual edge list.
- **Circular dependencies** are found by depth-first search over that list.
- **Risk** is a weighted blend of complexity, coupling and coverage gaps.
- **Hotspot risk scores** combine churn, complexity, coverage and defects.
- **Directory health** is the size-weighted mean of the files beneath it.

So the analysis tells a coherent story: `UserService` scores *critical*
because it genuinely has the widest fan-out, the highest complexity and the
lowest coverage in the graph — and both detected cycles genuinely run
through it.

---

## Visualisation technology

No D3. No Three.js. The choice is per surface, based on what each one needs:

| Surface | Technology | Why |
|---|---|---|
| Landing hero DNA | **Canvas 2D** | A continuous animation over ~200 moving primitives with per-frame glow and cursor falloff. As SVG this would mean mutating 200 DOM nodes per frame. |
| Overview DNA | **SVG** | Every band is a focusable, labelled control with an accessible name. Semantics beat particle count, and the geometry is recomputed only on data or highlight change — never per frame. |
| Architecture graph | **SVG** + a hand-written layered layout | Bounded node count, free hit-testing, zoom/pan by transform, keyboard-navigable. |
| Hotspots, evolution, dependencies | **SVG** | Small mark counts that need to be accessible. |

**Why no D3:** it would add ~100 kB and an `any`-typed boundary in exchange
for roughly 150 lines of scale and layout maths that are clearer written
directly. **Why no Three.js:** ~600 kB to render a 2D helix projection that
Canvas 2D already draws at 60 fps.

**Why a layered graph layout, not a force simulation:** the data already
carries what a force layout spends hundreds of iterations discovering —
which architectural layer each unit belongs to. Layers become columns,
ordered within each column by barycentre. The result is readable on first
paint, identical on every reload, and free of the drifting settle animation
that makes force graphs hard to read and harder to click.

---

## Design system

Tokens live in `src/styles/_tokens.scss` and nothing hard-codes a colour.

- **Ground:** a near-black neutral ramp, dark-native by design.
- **Accent:** exactly two hues — electric blue for interaction, helix teal
  for the second strand and positive states.
- **Health:** a continuous red → amber → green ramp. Colour encodes health,
  never decoration, and the DNA, the graph, the hotspot map and the timeline
  all read from the same function.
- **Type:** Inter for the interface, JetBrains Mono for technical data and
  every figure, so numbers align and read as instrument output.

### Accessibility

- Every text tier clears WCAG AA on every surface it sits on (primary
  14.7:1, secondary 6.7:1, muted 4.6:1 at worst). `--text-faint` is 3.0:1
  and is reserved for decoration — never for content.
- The DNA, the graph nodes, the hotspot marks and the timeline years are all
  keyboard reachable, with accessible names carrying their values. Focus
  drives the same highlighting as hover.
- One `h1` per screen, no heading-level jumps, landmark regions throughout.
- `prefers-reduced-motion` is honoured at the token level (durations to
  zero), in the reset, and in each animated component — the canvas renders a
  single composed frame rather than animating.

### Motion

Every animation earns its place: the DNA draws itself in on generation,
scores count up once, panels rise in, the graph highlights relationships,
the timeline scrubs. The overview DNA deliberately does **not** idle-animate
— a perpetually rotating helix in a dashboard is exactly the kind of motion
that costs attention and returns nothing.

---

## Performance

Production build:

| | Transfer |
|---|---|
| Initial load | **99 kB** |
| Largest feature route | 7 kB |

Every feature route is its own lazy chunk. The landing page pulls 2.7 kB of
feature code. Animation loops run outside Angular's zone and stop when
scrolled out of view; the counting readouts write the DOM directly rather
than costing a change-detection pass per frame per numeral.

---

## The demo dataset

**Atlas Commerce Platform** — an Angular storefront over Spring Boot
services, backed by PostgreSQL and Redis. 1,284 files, 90,980 lines, 21,470
commits, 17 contributors, six years of history. Overall health **87**.

**Nova Platform** — a younger Next.js and Go system, included so the
comparison screen has something to say. Overall health **83**, and
deliberately the mirror image of Atlas: stronger on testing and security,
weaker on maintainability and evolution.

---

## Next phase

The seams are already in place:

1. Implement `HttpAnalysisService` against the real API.
2. Change one provider line in `app.config.ts`.
3. Replace `core/mock/doctor-responses.ts` with a model-backed endpoint —
   the `DoctorBlock` union it produces is already the render contract.

The UI does not change.

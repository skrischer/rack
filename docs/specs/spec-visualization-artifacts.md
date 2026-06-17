# Spec: Phase 6 — Agent visualization artifacts

> Created: 2026-06-17

An MCP client reads a user's training data and writes a visualization artifact into private Supabase Storage with a row in an RLS-protected `artifacts` table (`source='agent'`), and the native app lists and renders those stored artifacts.

This phase is built and verified **local-first**: the full Supabase stack runs locally via `supabase start` (API at `http://localhost:55321`, with generated anon/service_role keys, JWT secret, and local Postgres URL — these replace any managed keys for development), the rack-MCP runs as a plain Node process on `http://localhost:<port>` over Streamable HTTP (no Caddy/VPS/TLS), and the Android app reaches the stack at `http://10.0.2.2:55321` from an emulator (or the host LAN IP from a physical device). Local Supabase Storage and Realtime are part of the stack and work locally. Hosted/production deployment (managed Supabase + `db push`, VPS + Caddy + domain/TLS, Firebase/FCM) remains the eventual target per the constitution and is explicitly **DEFERRED** — not removed.

## Outcome

- [ ] An `artifacts` table exists with `user_id`, `name`, `type`, `storage_path`, `source`, `created_at`, `updated_at`, shipped with its RLS owner policy (`user_id = auth.uid()`) in the same migration, applied against the local stack (`supabase db reset` / migration up — safe, wipes only the local Docker DB).
- [ ] A private Supabase Storage bucket `artifacts` exists in the local stack with per-user access: a user (and an MCP acting as that user) can only read/write objects under their own `{user_id}/` path prefix; cross-user access is denied at the Storage policy layer.
- [ ] An MCP tool `create_artifact` accepts `name`, `type` (MIME), and `content`; uploads the bytes to `artifacts/{user_id}/{artifact_id}.{ext}` acting as the resolved user (never service-role), inserts the matching `artifacts` row with `source='agent'`, and returns the new artifact's id, name, type, and storage path — verified against the local stack.
- [ ] An MCP tool `list_artifacts` returns the calling user's artifacts (id, name, type, storage_path, source, created_at, updated_at), scoped by the API key alone — no `user_id` accepted from the caller.
- [ ] The MCP rejects `content` larger than 2 MB and any `type` outside the allowed set (`text/html`, `image/svg+xml`, `image/png`) at the Zod boundary with a clear error.
- [ ] A cross-user access test proves user A's API key cannot create, list, or read user B's artifacts (table rows and Storage objects), run against the local stack.
- [ ] The Android app (emulator pointed at `http://10.0.2.2:55321` with the local anon key) shows an "Artifacts" surface that lists the signed-in user's artifacts (name, type, date) via the repository layer, newest first.
- [ ] Opening an artifact renders it in the app: `text/html` and `image/svg+xml` in a sandboxed WebView (JavaScript enabled, no file/content access, no native bridge), `image/png` in an image view — content fetched via a short-lived signed URL from the private local bucket.
- [ ] Agent-written artifacts (`source='agent'`) are visually marked in the list using the Recomp accent (volt `#c8f23a`), consistent with the app's agent-edit highlighting language.
- [ ] `make verify` and `make build` are green; no `any`, no service-role write of user data, no `user_id` accepted from the MCP caller.
- [ ] (deferred — hosted deploy) The same migration applies to the managed Supabase project via `db push`, and the bucket + policies exist in the hosted project; the rack-MCP serves the tools over HTTPS via Caddy on the VPS domain. Recorded for the production cut-over, not required to complete or verify this phase locally.

## Scope

### In scope

- The `artifacts` table + RLS migration and the private `artifacts` Storage bucket + per-user Storage access policies (this phase introduces both), applied and verified on the **local** stack.
- MCP `create_artifact` and `list_artifacts` tools (Zod-validated, user-scoped, `source='agent'` on write), running as a plain Node process on localhost against the local stack.
- Android read-only Artifacts list + viewer (WebView for HTML/SVG, image view for PNG) behind a repository, with signed-URL fetch, exercised against the local stack from an emulator.
- Agent-edit visual marking of artifacts in the list (reuses the Phase 4 highlighting language; here a static `source` badge since the data is agent-authored).

### Out of scope

- In-app artifact authoring or editing — artifacts are agent-authored (vision non-goal; the agent authors, the app consumes). Why: matches the app's consumption-only stance.
- Realtime live-highlight of a newly arriving artifact. Why: the realtime mechanism is owned by Phase 4 (`docs/specs/spec-live-sync.md`); this phase only ensures the `source` field is present so a later subscription can highlight. A manual refresh / pull-to-refresh is sufficient here. (Local Realtime is available in the stack but its use is Phase 4's concern.)
- Server-side rendering, thumbnailing, or transformation of artifact content. Why: the agent ships final renderable bytes; the app just displays them.
- Chart libraries / native dashboards (Vico) — that is Phase 11 (`docs/specs/spec-dashboards.md`). Why: this phase displays agent-authored artifacts, not native charts.
- New read tools over training data — the agent uses the existing Phase 2 read tools (`docs/specs/spec-rack-mcp.md`) to gather data before authoring.
- (deferred — hosted deploy) Hosted production roll-out: `db push` of the migration to the managed Supabase project, VPS + Caddy + domain/TLS in front of the MCP, and HTTPS exposure of the tools. The constitution's hosting choices remain the production target; they are not built or verified in this phase.

## Constraints

Binds to `docs/constitution.md`: every user-owned table ships its RLS policy (`user_id = auth.uid()`) in the same migration; the MCP never writes user data with the service-role key and always acts as the resolved user; no MCP tool accepts a `user_id` from the caller; every mutation sets `source` and `updated_at`; TypeScript strict with no `any` / `as unknown as` / `@ts-ignore`; Zod at the MCP boundary; secrets server-side only (the Android client uses the anon key + a user JWT — never the service-role key); Android keeps no business logic in Composables, drives UI via ViewModel + StateFlow, and routes all Supabase access through a repository; functions ≤ 50 lines, files ≤ 400 lines (guideline). New MCP capability lands in `/mcp/src/tools/*`; the table + policy land in `/supabase/migrations`; the screen lands as an `/android` feature (per `docs/architecture.md`).

Local-dev constraints (primary path): the local anon key and API URL (`http://localhost:55321`, or `http://10.0.2.2:55321` from the emulator) come from `supabase start`; only the local anon key goes into `android/local.properties`. A Supabase Auth test user is created by signing up against the **local** Auth (no dashboard). Random secrets such as `API_KEY_PEPPER` are generated locally by the implementer. The local service_role key and JWT secret stay server-side in the MCP environment and never reach the Android client.

Known local caveat (not a blocker): some MCP clients want HTTPS even for `localhost`. If a given client refuses plain `http://localhost`, expose the MCP via a local cloudflared quick-tunnel or a local Caddy with a localhost cert. This is a local-dev convenience only; production TLS via Caddy on the VPS domain is the deferred hosted path.

## Prior art

- [Programmable agent access (MCP write-through to fitness data)](../prior-art.md#programmable-agent-access-mcp-write-through-to-fitness-data) — confirms a write tool that creates user data is in-scope and that 12-ish tools is a sane surface; `create_artifact` extends that write surface to artifacts and surpasses wger-mcp's no-push ceiling by landing in a synced app.
- [Real-time sync & live edit highlighting](../prior-art.md#real-time-sync--live-edit-highlighting) — the `source`-based agent-edit highlight pattern reused as the artifact's `source='agent'` badge; CRDT machinery deliberately skipped.
- [Charts & dashboards — Jetpack Compose (Phase 11)](../prior-art.md#charts--dashboards--jetpack-compose-phase-11) — relevant only as a boundary: native Compose charts are Phase 11; this phase renders agent-authored artifact bytes instead, so Vico is explicitly out of scope here.

## Human prerequisites

Local development (the primary path) needs only dev tooling — no human-delivered secrets and no managed project. `supabase start` generates and prints all keys (anon, service_role, JWT secret) and the Postgres URL; the test user is created by signing up against local Auth; `API_KEY_PEPPER` and similar secrets are generated locally by the implementer; the private `artifacts` bucket is created via migration/SQL (issue #39), so no dashboard step is required.

### Local (required now)

- [ ] Docker + the Supabase CLI installed so `supabase start` brings up the local stack (Postgres + Auth + Realtime + Storage).
- [ ] Node + the MCP toolchain installed to run the rack-MCP as a local process; Android Studio + an emulator (or a physical device on the host LAN) to run the app against `http://10.0.2.2:55321`.
- [ ] No human-delivered secrets, no managed project, no dashboard action. (Datasets are not needed for this phase — it consumes agent-authored bytes, not the wger/free-exercise-db catalog.)

### Deferred — hosted deploy (recorded, not blocking local work)

- [ ] A managed Supabase project + access token to `db push` the `artifacts` migration and provision the private bucket + policies in the hosted project.
- [ ] A VPS + domain + DNS + TLS/Caddy to serve the rack-MCP over HTTPS in production.
- [ ] (Not used by this phase) Firebase/FCM — listed only for completeness of the deferred hosted stack; artifacts have no push-delivery requirement.

## Prior decisions

| Decision | Rationale | Date |
| --- | --- | --- |
| The phase is built and verified on the local Supabase stack (`supabase start`), MCP on localhost, Android via the emulator (`10.0.2.2:55321`); hosted deploy (managed project `db push`, VPS/Caddy/domain/TLS) is deferred. | Local-first development needs no managed project or human-delivered secrets; the constitution's hosting choices remain the production target. | 2026-06-17 |
| Artifacts are stored as raw renderable bytes in a private Storage bucket, with a metadata row in `artifacts` holding the object `storage_path`. | Matches the architecture's `artifacts` table (Storage reference + metadata) and key flow 6; keeps large payloads out of Postgres. Local-stack Storage supports this. | 2026-06-17 |
| Supported `type` set is `text/html`, `image/svg+xml`, `image/png`; `text/html` is the primary/required-supported format. | The Recomp prototype (`artifact.html`) and MCP clients like Claude author HTML visualizations; SVG/PNG cover static charts the agent may render directly. A bounded allow-list keeps rendering and the WebView sandbox safe. | 2026-06-17 |
| Content size capped at 2 MB, enforced via Zod at the MCP boundary. | Visualization HTML/SVG/PNG is small; a hard cap prevents abuse and keeps uploads fast. | 2026-06-17 |
| Storage path convention `artifacts/{user_id}/{artifact_id}.{ext}`; Storage policies scope read/write to objects whose first path segment equals `auth.uid()`. | Per-user isolation enforced in Storage policies (mirrors table RLS), so the MCP acting as the user is naturally confined to its own prefix; no service-role needed. | 2026-06-17 |
| The MCP uploads and inserts acting as the resolved user (user-scoped Supabase client), never service-role. | Constitution: the MCP never writes user data with service-role and always acts as the resolved user so RLS/Storage policies apply. | 2026-06-17 |
| MCP tool surface for this phase is exactly `create_artifact` (write) + `list_artifacts` (read); no delete/update tool. | Minimal surface for the phase intent (author + display); deletion/editing is not required by the vision and would add policy surface without a current need. | 2026-06-17 |
| The app fetches artifact bytes via a short-lived signed URL from the private bucket, not a public URL. | The bucket is private for per-user isolation; signed URLs let the authenticated client read its own objects without exposing them publicly. Works against the local Storage stack. | 2026-06-17 |
| HTML and SVG render in a sandboxed Android WebView (JavaScript enabled, no file/content URL access, no JS-to-native bridge, no allowFileAccess); PNG renders in an image view. | Agent-authored HTML can contain inline JS for interactive charts (as `artifact.html` does); the sandbox limits blast radius while preserving rendering fidelity. | 2026-06-17 |
| The app surface is read-only list + viewer; no in-app create/edit. | Vision non-goal: the agent authors, the app consumes. | 2026-06-17 |
| Realtime auto-highlight of new artifacts is deferred to Phase 4's mechanism; this phase guarantees `source` is set and shows a static agent badge, with manual/pull-to-refresh to fetch new artifacts. | Avoids duplicating the realtime subscription owned by `docs/specs/spec-live-sync.md`; keeps this phase tightly bounded. | 2026-06-17 |
| The `artifacts` Storage bucket is created via SQL/migration in `/supabase` alongside its policies. | Keeps bucket + policies versioned in the repo with the table migration; consistent with "table + policy in the same migration" and applies cleanly to the local stack with no dashboard step. | 2026-06-17 |

## Tracking

This is a **full-spec** track phase: milestone **Phase 6: Agent visualization artifacts** (carries `Depends on milestone: #2` and `Depends on milestone: #3` in its description), with the dependency-ordered issues below. Each issue references this spec at `docs/specs/spec-visualization-artifacts.md`. No step list lives in this spec — the issues own the steps.

## Verification

All checks below run against the **local** stack (`supabase start`, MCP on localhost, emulator at `10.0.2.2:55321`).

- `make verify` — lint + typecheck + test (MCP) + Android static checks (ktlint/detekt) green.
- `make build` — MCP build + `./gradlew assembleDebug` green (this phase is app-affecting).
- Behavioral checks a human QA runs at the milestone gate (Test = `none yet`, so these are manual):
  - Migration applies cleanly to the local DB (`supabase db reset` / migration up); `artifacts` has RLS enabled with an owner policy; the private `artifacts` bucket exists with per-user path policies.
  - Via an MCP client (local process) with user A's API key: call `create_artifact` with a small HTML body and `type='text/html'` → a row appears in `artifacts` with `source='agent'`, `storage_path` set, and the object exists under `artifacts/{A}/...` in local Storage.
  - `list_artifacts` with user A's key returns that artifact and only A's artifacts.
  - Passing `content` > 2 MB, or a `type` outside the allow-list, is rejected with a clear Zod error; no row or object is created.
  - Cross-user test: user B's API key cannot `create_artifact` into A's space, `list_artifacts` does not return A's rows, and a direct signed-URL/object read of A's path is denied.
  - In the Android app (emulator, local anon key) signed in as user A: the Artifacts list shows the new artifact (name, type, date), newest first, with the volt agent badge for `source='agent'`.
  - Opening the HTML artifact renders it in the WebView; opening a PNG artifact renders the image; an SVG artifact renders in the WebView.
  - Confirm the WebView cannot reach a JS-to-native bridge and has no file/content access (smoke: the rendered content is sandboxed).
  - The app holds only the local anon key + user JWT (no service-role key in the client), verified by inspection.
- (deferred — hosted deploy) After cut-over: re-run the migration via `db push` against the managed project, confirm the hosted bucket + policies, and smoke the tools over HTTPS via Caddy on the VPS. Not required to close this phase.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Agent-authored HTML executing untrusted JS in the WebView could exfiltrate or misbehave. | Sandbox the WebView: enable JS for chart rendering but disable file/content access and add no JS interface/bridge; restrict to the single owner's own artifacts. Document the trust boundary (the user's own agent authored it). |
| MCP accidentally uses the service-role client for the Storage upload, bypassing per-user isolation. | The upload and insert both go through the user-scoped client resolved from the API key; the cross-user test and review gate catch a service-role regression. The local service_role key stays in the MCP env, never the client. |
| Storage policies and table RLS drift out of sync (object readable but row hidden, or vice versa). | Path-prefix Storage policy and table RLS both key on the user id; the cross-user QA test exercises both layers together on the local stack. |
| Large or malformed content stalls the upload or the WebView. | 2 MB Zod cap + bounded MIME allow-list at the MCP boundary; the app shows a fallback when bytes cannot be rendered. |
| Signed URL expiry races with slow rendering. | Use a sufficient TTL (e.g. 60 s) for the fetch and load bytes into the viewer before display; regenerate on retry. |
| `.ext` derivation from MIME type is ambiguous. | Map the allow-listed MIME set to fixed extensions (`text/html→html`, `image/svg+xml→svg`, `image/png→png`); reject anything else before upload. |
| An MCP client refuses plain `http://localhost` (wants HTTPS). | Known local caveat: front the local MCP with a cloudflared quick-tunnel or a local Caddy + localhost cert for that client; not a blocker for the phase. |
| Local behavior diverges from the hosted target on cut-over. | The local stack mirrors managed Supabase (same Postgres/Auth/Realtime/Storage); the deferred-hosted verification re-runs the migration and smokes the tools to confirm parity. |

## Decision log

- (local-first dev path adopted 2026-06-17; hosted deploy deferred) — the phase is developed and verified on the local Supabase stack (`supabase start`), the MCP on localhost, and the Android emulator at `10.0.2.2:55321`; managed-project `db push`, VPS/Caddy/domain/TLS, and Firebase/FCM stay the production target but are deferred and non-blocking.
- Stored artifacts as raw bytes in a private Storage bucket with an `artifacts` metadata row holding `storage_path` (auto-resolved under autonomous planning mandate, 2026-06-17).
- Allowed `type` set = `text/html` (primary), `image/svg+xml`, `image/png`; 2 MB content cap (auto-resolved under autonomous planning mandate, 2026-06-17).
- Storage path `artifacts/{user_id}/{artifact_id}.{ext}` with per-user path-prefix Storage policies mirroring table RLS (auto-resolved under autonomous planning mandate, 2026-06-17).
- MCP tool surface limited to `create_artifact` + `list_artifacts`, both user-scoped, write sets `source='agent'` + `updated_at` (auto-resolved under autonomous planning mandate, 2026-06-17).
- App is a read-only list + viewer; HTML/SVG in a sandboxed WebView (JS on, no file access, no native bridge), PNG in an image view; bytes fetched via short-lived signed URL (auto-resolved under autonomous planning mandate, 2026-06-17).
- Bucket + policies created via SQL/migration in `/supabase`, applied to the local stack with no dashboard step required (auto-resolved under autonomous planning mandate, 2026-06-17).
- Realtime auto-highlight deferred to Phase 4's mechanism; this phase only guarantees `source` and a static agent badge plus manual refresh (auto-resolved under autonomous planning mandate, 2026-06-17).
- Agent-written artifacts marked with the Recomp volt accent `#c8f23a` in the list, consistent with the agent-edit highlighting language (auto-resolved under autonomous planning mandate, 2026-06-17).
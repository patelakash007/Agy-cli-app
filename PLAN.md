# Agy-cli-app Delivery Plan

- **Updated:** 2026-11-11
- **Implementation baseline:** the repository's single `Initial version` root;
  always verify the current `HEAD` and working tree before implementing a task.
- **Plan state:** Active; the finished product does not exist yet.
- **Permanent product contract:** `AGENTS.md`
- **Supported initial direction:** ARM64 Android/Bionic, min API 24, sideloaded.

This is the only living implementation plan. It replaces old project summaries,
release claims, module READMEs, and generated teamwork handoffs. An AI should read
`AGENTS.md`, this plan, the current task's files, and the relevant tests—then do
one bounded task.

## 1. How to use this plan

Status values:

- **DONE** — acceptance evidence exists and is named.
- **ACTIVE** — one owner is currently implementing the task.
- **TODO** — ready only when all dependencies are DONE.
- **BLOCKED** — an owner decision, external artifact, protocol proof, license, or
  another named prerequisite is missing.

Rules:

1. Select one task or one explicitly named subtask.
2. Re-read current code; paths are durable hints, line numbers are not.
3. Confirm dependencies and reproduce the relevant failure before editing.
4. Add or retain a regression test, make the smallest coherent patch, and run
   the stated checks.
5. Mark DONE only with actual acceptance evidence. Otherwise leave TODO/BLOCKED
   and record what is missing.
6. Do not create another Markdown handoff. Report the result in the conversation
   and update this file only when status, ordering, risks, or decisions changed.

## 2. Current reality

### 2.1 What exists

The repository currently builds a small native Android **host** around the
Antigravity CLI. It has:

- A Java Android app with dashboard and hosted WebView surfaces.
- A foreground service, process supervisor, downloader, basic state handling,
  and log display.
- A native `agy-pty` helper and browser/open shims.
- Direct selection of the vendor's Android ARM64 manifest rather than the
  upstream installer's unreliable `uname -o` logic.
- SHA-512 download verification and a tar extractor with basic path checks.
- A manual Gradle-free APK pipeline using `aapt`, Java compilation, D8, alignment,
  and `apksigner`.
- An offline host-test entry point introduced by task A01.

### 2.2 What does not exist

The app is **not yet** the standalone development environment in `AGENTS.md`.
It does not currently have:

- A real app-specific `usr/` bootstrap, Bash/tool dependency closure, package
  manager, signed repository, or package UI.
- A user-facing terminal emulator with tabs, keys, resize, and attach/detach.
- A safe workspace manager, file browser/editor, or real SAF import/export.
- A verified ACP client or equivalent local agent Studio.
- Coherent service-owned lifecycle/auth/remote state.
- Transactional CLI/native-environment installers with robust rollback.
- Release signing, migration, licensing, redistribution, and device-test proof.

The dashboard's text log is not a terminal. A hosted URL is not a local IDE. A
system-shell fallback is not Bash. A small APK is not the full installed size.

### 2.3 Verified baseline and expected failures

Task **A01 is DONE**: the repository contains offline Python/Java tests and a
separate build-smoke command. The harness deliberately exposes known defects.

Run:

```bash
bash tests/run_host_tests.sh
```

As of 2026-11-11 it runs 14 Python cases and 14 Java checks and exits nonzero for
seven expected assertions:

- **A02 / R16:** configured min/target SDK values do not reach the manifest;
  invalid numeric and control-character config is accepted (3 failures).
- **A05 / R19:** ZIP data-descriptor input produces a corrupt aligned archive
  (1 failure).
- **B02 / R04:** HTTP, arbitrary-host, and suffix-lookalike session URLs are
  accepted (3 failures).

Do not suppress these failures. Fix the named tasks and make the same command
green.

Run the separate release/dev compile and package smoke check with:

```bash
bash tests/run_build_smoke.sh
```

It must not install an APK, use credentials, download the CLI, invoke a live
agent, sign a release, or modify checked-in source/config files.

### 2.4 Documentation consolidation

Repository guidance is now intentionally limited to `AGENTS.md` and `PLAN.md`.
The repository constitution is no longer staged into a user's vendor config.
The old runtime seed that claimed tools were already installed was removed; the
remaining onboarding fabrication and permissive defaults are still tracked by
B01 and must not be mistaken for completed safety work.

## 3. Findings register

These findings came from source review and local probes against the baseline.
Method names and current behavior outrank old line numbers.

| ID | Severity | Current evidence | Owning tasks |
|---|---|---|---|
| R01 | Goal blocker | Asset staging contains PTY/certs/seeds, not a native `usr` bootstrap or package ecosystem. | D01–D04 |
| R02 | Goal blocker | PATH/environment handling omits a coherent prefix; fallback “bash” is system `sh`; child setup can lose CLI/helper paths. | D03 |
| R03 | High | CLI starts in `filesDir`, mixing project work with credentials/runtime/package state. | F01 |
| R04 | High, reproduced | Session URL validation accepts HTTP, arbitrary hosts, and suffix lookalikes. | B02, C01 |
| R05 | High | Hosted WebView uses substring allowlists, UA manipulation, mixed-content compatibility, and broad permission grants. | B03 |
| R06 | High | Deep links/clipboard/token-file heuristics can inject or fabricate login success without a correlated active flow. | B04, C01 |
| R07 | High | Terminal text drives onboarding/TOS input; synthetic completed state and permissive defaults are seeded. | B01 |
| R08 | High | CLI updates are uncoordinated, non-transactional, weakly validated, and can replace a live working binary. | B06 |
| R09 | High | Tar parsing lacks full checksum/numeric/metadata/size/symlink policy needed for a general bootstrap. | B05, D02 |
| R10 | High | Activity booleans, static fields, URL extras, and cookies compete with process truth. | C01 |
| R11 | High | Stop/start races, stale input callbacks, stream ownership, and force-kill behavior can leak or truncate sessions. | C02 |
| R12 | High reliability | `readLine()` can stall on TUI output, CR updates, prompts, or URLs without LF. | C03 |
| R13 | Battery/reliability | Default auto-start, indefinite wake lock, 100 ms PTY polling, and fixed terminal size. | C04, C05, E01–E02 |
| R14 | Battery/privacy | Unrotated per-line logs and whole-file UI polling are unbounded; redaction is inconsistent. | C03, C04 |
| R15 | Architecture drift | Legacy Python/loopback/DNS/certificate fields are documented or present but are not the authoritative Java startup path. | D03, G01 |
| R16 | Build correctness, reproduced | SDK config is not applied consistently and config values lack strict validation. | A02 |
| R17 | Build provenance | Native helper build can mutate a tracked binary and silently use stale output after compiler failure; CI NDK provenance is absent. | A03 |
| R18 | Release security | Release path uses a tracked debug key/default credentials and does not enforce declared signing config. | A04 |
| R19 | Build correctness, reproduced | Python ZIP aligner mishandles local headers with data descriptors and can emit corrupt ZIPs. | A05 |
| R20 | Verification/truth | CI and old claims exceed device evidence; A01 now exposes some but not all missing gates. | G01, G02 |
| R21 | Future file-security risk | Broad provider roots and raw-path guesses could expose private state or misuse SAF grants. | F02, F03 |
| R22 | Goal blocker | No real terminal, workspace/files/editor, package UI, or local agent screen exists. | D04, E01–E02, F01–F05 |
| R23 | High | URL broadcast trust is weak on API 24–32; package targeting is not sender authentication. | B07 |

## 4. Owner decisions and external gates

Implementation agents must not invent answers to these gates.

### P1 — Vendor service and redistribution permission — BLOCKED

Clarify whether the intended app may download, bundle, redistribute, supervise,
and brand access to the Antigravity CLI/service. Official ACP documentation shows
an integration path but does not itself grant every redistribution or wrapper
right. Until resolved: build/test locally, do not claim endorsement, and do not
publish a new public release.

### P2 — Native package source and repository — BLOCKED

The owner must approve a pinned Bionic package build source/revision, fixed
release/dev prefixes, bootstrap package set, signing scheme/key custody, hosting,
maintenance policy, license/source obligations, and supported ABI/API/page-size
matrix. Until then: implement schemas and fixtures only; never fake `pkg`.

### P3 — Release signing and installed-user migration — BLOCKED

Choose the real release identity and migration path from existing debug-signed or
other installations, including API 24–27 behavior. Define CI secret custody,
expected certificate digest, version policy, rollback, backup/reinstall guidance,
and whether app IDs remain stable. Never silently delete app data to solve this.

### P4 — Terminal component and license — BLOCKED

Approve an exact terminal emulator/view/native PTY component and revision (a
small reviewed subset, not an unexplained copy of an entire app). Confirm license,
notices, Java/API dependencies, JNI build inputs, and maintenance plan.

### P5 — ACP capability and service policy — BLOCKED

Verify `agy acp` on the actual Android binary/version and record its framing,
schema, lifecycle, auth, permissions, cancellation, and coexistence rules with
Remote Control. Obtain owner approval for the service-integration model before
building the production local Studio.

### P6 — Long-term Android target strategy — DECISION DEFERRED

The initial downloadable-native design keeps target SDK 28. Measure installation
and execution on supported current devices. Separately design a modern-target
variant using APK-installed native code/update packaging before changing target
SDK. This is an architecture decision, not a manifest cleanup.

## 5. Milestones and honest completion gates

| Milestone | Completion criterion |
|---|---|
| **M0 — Safe usable native CLI host** | Reproducible builds; strict URL/web/auth/consent rules; transactional CLI install; coherent service lifecycle; bounded power/logging; real interactive CLI setup terminal. |
| **M1 — Standalone native workspace** | App-specific signed bootstrap/repository; real Bash/tools/packages; selected internal project; terminal, files/editor, import/export; no runtime dependency on Termux. |
| **M2 — Integrated agent Studio** | Verified in-app protocol client with chat/tools/diffs/questions/approvals/cancel, plus isolated hosted Remote Control where eligible. |
| **M3 — Releasable product** | Signing/migration/legal gates resolved; CI and device matrix pass; provenance, limitations, size, performance, and security claims are measured and truthful. |

Removing unsafe automatic setup may temporarily expose that first-time setup is
not usable. Do not restore auto-consent to hide the gap; M0 remains incomplete
until E02 supplies the real terminal.

## 6. Recommended execution order

The next recommended task is **B01a** (stop automatic consent/input and permissive
fresh-install defaults). Then:

1. **Immediate safety:** B01a → B01b → B02 → B03 → B04.
2. **Known build regressions:** A02 and A05 can proceed independently after B01a;
   make their existing tests green.
3. **State and external transport:** C01 → B07 → C02 → C03.
4. **Native/build/install reliability:** A03 → B05 → B06 → C05 → C04.
5. **Workspace and real terminal:** F01; then E01 → E02.
6. **Native tool environment:** D01 → D02 → D03 → D04, subject to P2/P3/P4.
7. **Files and hosted experience:** F02 → F03 → F04.
8. **Local agent Studio:** F05, subject to P1/P5.
9. **Release:** A04, G01, G02 and all applicable device/performance gates.

Do not postpone URL, consent, signing, or workspace isolation until after adding
more privileged filesystem and package features.

## 7. Task catalog

### A — Build, tests, native provenance, and signing

#### A01 — Offline regression entry point — DONE

- **Evidence:** `tests/run_host_tests.sh`, Python fixtures, the Java host runner,
  and `tests/run_build_smoke.sh` exist and run without credentials or live services.
- **Important:** the host suite is intentionally red for A02/A05/B02; A01 did not
  declare those defects fixed.

#### A02 — Truthful build configuration and SDK metadata — TODO

- **Depends on:** A01.
- Strictly validate numeric SDK/version fields, package IDs, XML values, and Java
  literals. Apply configured min/target SDK to generated manifest and D8 while
  keeping compile SDK, target SDK, app version, runtime schema, and CLI version
  distinct.
- Keep normal min 24/target 28. A min 26/target 31 fixture must update all outputs;
  malformed numeric/control-character input must fail clearly; both flavors must
  compile without modifying checked-in generated files.

#### A03 — Reproducible fail-closed native host build — TODO

- **Depends on:** A02.
- Pin/setup the Android NDK, compile helper output under `build/native/arm64-v8a`,
  never mutate/use a stale tracked source-tree binary implicitly, and verify ELF
  machine, Android linker, dependencies, RUNPATH, API target, and page-size support.
- Missing/failed toolchains must fail. Local and CI checks must agree and record
  versions/provenance.

#### A04 — Separate development and private release signing — BLOCKED (P3)

- **Depends on:** A01 and owner signing/migration decision.
- Dev may use an explicit debug identity. Release without owner-controlled inputs
  must fail. Verify expected certificate digest, flavor, app ID, version,
  debuggable state, native artifacts, and schemes valid for min API.
- No secret in git/arguments/logs and no publication until update/data migration
  is approved and tested.

#### A05 — Repair or reject unsupported ZIP alignment inputs — TODO

- **Depends on:** A01.
- Use reliable central-directory metadata; correctly preserve/reconstruct data
  descriptors or deliberately reject them. Validate signatures/bounds and reject
  unsupported ZIP64/encryption rather than emit a corrupt success.
- Stored/deflated payload bytes, CRCs, names, extras, flags, and comments must
  survive supported alignment. Existing data-descriptor regression must pass or
  receive a documented nonzero unsupported error.

### B — Consent, web/auth trust, archives, and installers

#### B01 — Remove fabricated onboarding and unsafe consent — TODO

- **Depends on:** A01. Split into **B01a input/defaults** and **B01b seed/state UI**.
- Stop automatic login selection, TOS/navigation keystrokes, permissive
  `always-proceed` settings, fixed installation state, guessed vendor project
  records, and overwriting incomplete state. Preserve nonempty user files.
- Show `SETUP_REQUIRED` and require terminal interaction. Suspicious output such
  as `[Done]` must never write stdin. Existing installs get a visible review path,
  not silent settings changes.
- **Partial note:** documentation consolidation removed repository `AGENTS.md`
  injection only; all other B01 behavior still requires implementation/tests.

#### B02 — One strict trusted-URL policy — TODO

- **Depends on:** A01.
- Centralize URI parsing and classify before acting. Initial session rule: HTTPS,
  exact `antigravity.google.com`, no userinfo, default/443 port, exactly one opaque
  segment after `/r/`; preserve valid query/fragment encoding.
- Reject HTTP, lookalike/suffix hosts, controls, encoded slash/traversal, extra
  path segments, and auth-looking arbitrary hosts. Migrate every consumer.
- Existing R04 host failures must become green while official encoded examples
  remain intact.

#### B03 — Safe hosted WebView navigation and permissions — TODO

- **Depends on:** B02.
- Exact top-level origin policy; OAuth goes to supported external browser/Custom
  Tab; no UA spoofing, mixed content, TLS bypass, unknown permission grant,
  arbitrary scheme, or local shell bridge. Keep deliberate file upload working.
- Verify origin and ask the user before any supported camera/mic grant. Do not
  log raw callbacks/session URLs or infer auth from cookies.

#### B04 — Correlated authentication input — TODO

- **Depends on:** B01, B02 and proof of the actual vendor Android auth flow.
- Ignore unsolicited/stale/duplicate callbacks. Remove automatic clipboard reads.
  Explicit paste must bound/control-check one input and target the active awaiting
  generation. Token files mean “possible saved state,” not authenticated.
- Cancellation/retry must recover; an intent match alone never writes stdin or
  announces success.

#### B05 — Bounded policy-specific archive extraction — TODO

- **Depends on:** A01.
- Validate tar checksum/numeric fields, overflow, truncation, supported metadata,
  entry count, total bytes, duplicate conflicts, parent symlinks, and actual
  destination containment. CLI extraction permits only its expected layout;
  bootstrap extraction uses a separately explicit policy.
- Extract only into fresh host-owned staging; preserve intended modes; traversal,
  absolute/cross-prefix links, devices, unsupported metadata, and oversize input
  must fail without escaping or touching the live install.

#### B06 — One transactional native CLI installer — TODO

- **Depends on:** A02, B05; C02 before live update wiring.
- Explicit user Install/Update; ABI gate before network; strict manifest/version/
  SHA-512/origin/redirect validation; unique staging; streaming bounded IO;
  timeout, cancellation, one job, conservative free-space check, and ELF/spawn
  verification.
- Keep the old CLI until same-filesystem atomic publication succeeds while the
  session is stopped. Persist version/hash/source/schema. Every failure and two
  rapid taps must leave one known runnable install and at most one job.

#### B07 — Remove or authenticate external URL broadcasts — TODO

- **Depends on:** B02, A02, C01; A04 if a signature-permission fallback remains.
- Prefer owned output/internal service events and remove public broadcast input.
  If unavoidable, use flavor-specific protection on API 24–32 and NOT_EXPORTED on
  newer APIs, then apply URL policy and active-generation checks.
- A foreign app, other flavor, stale event, package-targeted intent, or public
  debug key must not drive URL/auth/state/input behavior.

### C — Runtime state, processes, output, power, and PTY

#### C01 — Service-owned immutable runtime state — TODO

- **Depends on:** B01, B02.
- Replace activity/static authorities with independent engine and remote states,
  active generation, workspace, installer state, endpoint, and structured error.
  Start/Stop/Retry are idempotent; activity recreation only observes.
- Remove unconditional auto-start/sticky behavior. URL/cookie/token/intent cannot
  set RUNNING/authenticated. Local engine remains usable without Remote Control.

#### C02 — Owned process lifetime and serialized input — TODO

- **Depends on:** C01.
- Recheck cancellation after process start; clear every process/input reference on
  exit/failure; one bounded generation-aware input owner; graceful group stop then
  bounded escalation; join/drain workers with real deadlines.
- No global `/proc` substring killing and no prompt/package replay after crash.
  Race fixtures must leave no child, callback, descriptor, or wrong-session kill.

#### C03 — Chunked terminal output and bounded parsing — TODO

- **Depends on:** C01, C02.
- Carry raw byte chunks to terminal; incrementally decode only for redacted
  diagnostics/events. Handle no-LF prompts, CR, split UTF-8/ANSI/OSC/URLs, and
  bound every parser buffer.
- One log writer owner; raw terminal bytes are not mutated by redaction; arbitrary
  child text cannot send input or prove auth.

#### C04 — Bounded background operation and diagnostics — TODO

- **Depends on:** C01–C03.
- On-demand default; accurate notification with Stop/Open; explicit timed/renewed
  keep-running policy; release locks on stop/error/auth wait. Respect Doze limits.
- Rotate/redact logs, bound entry and aggregate memory, update UI with deltas off
  disk/main thread, pause hidden refresh, and preserve user scroll position.

#### C05 — Correct PTY helper I/O, signals, EOF, and resize contract — TODO

- **Depends on:** A03, C02.
- Replace 100 ms polling with blocking readiness plus child/signal wakeup. Check
  session/ioctl/dup/signal/close results; handle EINTR, partial IO, EIO, EOF,
  SIGPIPE, child status, bounded drain, and owned-group shutdown.
- Initial geometry is configurable; runtime resize belongs to E01/E02. Native
  fake-child tests cover no-output exit, large output, EOF, signal, and Stop.

### D — App-specific native tools and packages

#### D01 — Produce real native tool artifacts — BLOCKED (P2, P3)

- **Depends on:** A03 and approved package/repository decisions.
- Reproducibly build separate matching release/dev prefixes (unless relocation is
  proven) with Bash, terminfo, core tools, curl/CA, Git, ripgrep, package manager,
  libraries/keyring; add Python/Node/compiler profiles only after closure tests.
- Generate signed bounded bootstrap/repository metadata, provenance, licenses,
  hashes, sizes, architecture/API/page-size support, and inventories. No Termux
  prefix paths or copied phone `usr` may remain.

#### D02 — Verified first-install bootstrap and explicit repair — BLOCKED (D01)

- **Depends on:** D01, B05, B06 coordination.
- Validate trusted manifest/signature, app ID, prefix, ABI/API/page size, inventory,
  limits, and storage before install. Stage with no active prefix users, publish
  transactionally, test at the real final prefix, and roll back on smoke failure.
- Normal launch never resets packages. Repair/upgrade preserves package databases,
  projects, `.gemini`, and user state; cancellation removes only owned staging.

#### D03 — One environment builder and native runtime doctor — BLOCKED (D02)

- **Depends on:** D02, F01.
- Implement the `AGENTS.md` environment contract for CLI/terminal/packages/tests;
  remove fake Bash, desktop/DNS assumptions, broad preload tricks, and dependence
  on Termux's prefix.
- Doctor safely reports version/ABI/prefix/cwd/tool/linker/CA/DNS/permission health
  without secrets or live prompts. Git helpers/TLS/tools must work in both CLI
  and terminal from the selected project.

#### D04 — Real package operations and package UI — BLOCKED (D01–D03)

- **Depends on:** D01–D03, E02, F01.
- Argument-array backend for search/show/list/install/remove/update; authenticated
  repository/architecture/prefix checks; visible dependency plan/progress/errors;
  confirmation for removal/large downloads; bounded cancellation behavior.
- Serialize every prefix mutation and respect package-manager recovery/locks. A
  real installed tool must become executable in terminal and CLI; no `trusted=yes`
  or fake-success wrapper.

### E — Real terminal

#### E01 — Qualify a small terminal component — BLOCKED (P4)

- **Depends on:** A03, C02 and owner component/license approval.
- Pin exact source/revision and prove manual-build compatibility, JNI/native PTY,
  raw byte IO, resize, exit, process ownership, UI detach, service lifecycle,
  dependencies, packaging/alignment, and notices using `/system/bin/sh` in a dev
  spike. Do not invent an emulator/API or import a whole app blindly.

#### E02 — Managed terminal tabs and interactive CLI setup — BLOCKED (E01)

- **Depends on:** E01, C01–C03; D03 for full Bash mode.
- Service-owned attachable sessions; expose the actual one CLI session for setup
  and approvals; do not nest `agy-pty` if the selected backend already owns a PTY.
- Tabs, explicit system-shell recovery label, Bash when ready, Ctrl/C/Esc/Tab/
  arrows, resize, bounded scrollback, user-confirmed paste, and per-session close.
  Activity recreation must not duplicate or kill unrelated sessions.

### F — Workspace, files, hosted UX, and local agent UI

#### F01 — App-owned workspace selection and correct cwd — TODO

- **Depends on:** C01, B01.
- Stable registry and default `projects/default`; create/select/rename; reject host
  state/prefix/runtime roots, traversal, aliasing, and unapproved symlink escape.
- Set CLI/shell cwd before spawn. Switching workspace is a controlled session
  transition; HOME/auth/history stay stable and vendor records are not fabricated.

#### F02 — Bounded file browser/editor and narrow sharing — TODO

- **Depends on:** F01, E02.
- Browse/create/open/save/rename within selected project; confirm destructive
  actions; bound text preview/edit size; detect binary/unsupported encodings;
  checked atomic saves with visible failure and no original-data loss.
- FileProvider roots/grants must exclude credentials/runtime/prefix/full app data;
  default exact-file read-only shares; deny traversal and symlink escape.

#### F03 — Real SAF import/export — TODO

- **Depends on:** F01, F02.
- Use ContentResolver/DocumentsContract streams and grants, never guessed raw
  paths. Bounded/cancellable internal import with collision policy; selected export;
  handle cloud/nonlocal/read-only/revoked providers, spaces, and Unicode.
- Internal projects work with storage permission denied; shared storage remains
  non-executable and a SAF grant is not shell filesystem access.

#### F04 — Honest hosted Remote Control UX — TODO

- **Depends on:** B02, B03, C01, E02, F01.
- Display advertised endpoint, actual observable remote state, owning generation,
  and workspace without calling URL discovery “connected.” Keep local terminal
  usable when hosted service is offline/ineligible/disabled.
- Safe hosted navigation, clearly separate external-browser login/cookies, clear
  stale endpoints on exit/switch, and no desktop service-installer assumptions or
  background Activity launch per parsed URL.

#### F05 — Capability-gated local Studio via documented ACP — BLOCKED (P1, P5)

- **Depends on:** F04, D03, E02, F01 and verified Android ACP support.
- Implement actual framing/schema negotiation, auth-required/session/prompt/
  stream/tool/diff/question/approval/cancel/exit with request IDs, bounded queues,
  supervised cwd/environment, stale-message rejection, and capability honesty.
- Never auto-approve or expose a second unrestricted shell. Keep local assets and
  narrow authenticated bridge isolated from hosted WebView. Verify whether ACP
  and Remote Control can coexist before allowing concurrent workspace editing.

### G — Truthful release gates

#### G01 — Reconcile all user-facing claims and legacy components — TODO/continuous

- **Depends on:** update after every task; final pass after M1.
- Audit in-app text, CI release body, metadata, comments, flags, and active versus
  legacy Python/loopback components. State unofficial host status, real sizes,
  versions, tested devices, hosted/local distinction, approval policy, and missing
  features. Remove or accurately classify unused flags/components only after
  reference/test inspection.
- Keep the two-document policy; update this plan instead of recreating module docs.

#### G02 — Make publication depend on meaningful evidence — BLOCKED

- **Depends on:** A01–A05, applicable B–F tasks, P1–P4, and owner release approval.
- CI runs host tests, both flavors, pinned native/toolchain/bootstrap checks, and
  artifact/flavor/version/certificate validation before publication with least
  permissions. Release attaches hashes, certificate digest, provenance, runtime
  requirements, limitations, and device-test record—never secrets/home backups.
- Wrong test/ELF/flavor/version/certificate blocks publication. Ubuntu/Termux
  compilation is never presented as standalone Android proof.

## 8. Test and acceptance matrix

### 8.1 Offline tests

Use deterministic fixtures and fake subprocesses; no credentials, paid prompt,
package mirror, existing build output, or live vendor service.

| Area | Required cases |
|---|---|
| URL/web | Official encoded URL; HTTP; arbitrary/suffix/userinfo/port/control/path attacks; auth path containing `/r/`; exact navigation/permission origins. |
| Config/build | Release/dev; min/target propagation; invalid fields; namespace vs app ID; D8 min API; missing tools; no source-tree native output. |
| ZIP/tar | Stored/deflated/descriptors/extras/Unicode/truncation/unsupported formats; CRC/payload equivalence; tar checksum/limits/traversal/symlinks/conflicts. |
| Installers | Manifest/hash/origin/redirect/ABI/ELF/space/cancel/concurrency/atomic publish/rollback/stale callback. |
| Lifecycle/input | Stop/start/auth/crash/stream races; stale generation; bounds/order; independent sessions; no replay. |
| Output/logs | No LF, CR, ANSI/OSC, split UTF-8/URL, bounded buffers, redaction, rotation, no UI whole-file scan. |
| Workspace/files | Stable HOME/cwd, traversal/symlinks/aliases, switch race, failed atomic save, provider credential denial, SAF without raw path. |
| Packages | Signature/architecture/prefix/dependencies, transaction lock/recovery, interrupted job, tool visible to CLI and terminal. |
| ACP | Real negotiated schema; auth-required; request IDs; stale/disconnect; permission/cancel; no unsupported capability. |

### 8.2 Android/device tests

Record device, Android API, ABI, kernel page size, flavor/app ID, signing
certificate, CLI version/hash, runtime schema, bootstrap/repository revision, and
toolchain. Untested combinations remain explicitly untested.

1. Fresh install in a profile with no usable Termux prefix; do not uninstall or
   damage the owner's Termux to simulate this.
2. Storage permission denied: internal project, install/setup, terminal, and
   files still work.
3. Metered/unmetered install plus cancel/offline/low-space/interruption/wrong ABI/
   wrong flavor fixtures.
4. Real first-time CLI setup and supported browser auth, with user-controlled
   terms/trust/approvals and zero unsolicited input.
5. Stop during launch, auth, command, remote connection, CLI update, bootstrap,
   and package job; verify cleanup/rollback/recovery.
6. Activity recreation, rotation, keyboard resize, background/foreground, screen
   off/Doze, network switch, process death, memory pressure, notification Stop.
7. Terminal: Bash version, `pwd`, quoted names, Ctrl-C/arrows/Tab/paste/resize/
   reopen/multiple tabs/bounded scrollback.
8. Native tools: local Git workflow, TLS/curl, ripgrep, Python/Node fixtures when
   installed, and compile/run only if compiler profile exists.
9. Signed package install/remove/update and interrupted recovery; every path and
   library belongs to this app's prefix.
10. User editor and agent edit the same selected project; switching never leaves
    an agent silently writing the old workspace.
11. SAF nonlocal provider, revoked/read-only grant, collisions, cancellation, and
    internal-only native execution.
12. Hosted Remote Control eligible/ineligible/offline and external-browser fallback;
    local terminal remains usable.
13. ACP approval/tool/edit/cancel only when F05 is enabled and owner-authorized.
14. Release/dev isolation and approved signing/data migration preserving state.

### 8.3 Performance/resource evidence

Measure repeatable scenarios: app stopped; dashboard visible; terminal idle;
local session with Remote Control off/on and UI visible/hidden; screen on/off;
small task; large output; initial install/package transaction; repeated start/stop.

Record child/app CPU, PSS/RSS where available, wake-lock duration, log/storage
growth, UI responsiveness, download/installed size, and command time on named
hardware. Pass requires no idle helper polling, default indefinite wake lock,
whole-log UI reads, unbounded files/buffers, or repeated-session resource leak.
Set numeric budgets only after measuring a baseline.

## 9. Task completion report

Every task result must state:

```text
Task/subtask:
Files changed:
Behavior fixed:
Regression tests added or retained:
Commands actually run and exact result:
Device tests performed (or NOT performed):
User data/signing/account/network changes:
Known limitation or blocker:
PLAN.md status change:
Recommended next task:
```

Do not mark a parent task DONE when only a subtask passed. Do not mark a blocked
artifact/protocol/legal task DONE using a mock.

## 10. Primary references to re-verify

Online behavior and terms can change. Record URL, version, and verification date
when a task relies on them.

- Native Android CLI release: <https://github.com/google-antigravity/antigravity-cli/releases/tag/1.2.15>
- Upstream installer: <https://antigravity.google/cli/install.sh>
- Android ARM64 manifest: <https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/android_arm64.json>
- Remote Control: <https://antigravity.google/docs/remote-control.md>
- ACP integration: <https://antigravity.google/docs/ide/extensions/acp.md>
- ACP protocol: <https://agentclientprotocol.com>
- CLI reference: <https://antigravity.google/docs/cli/reference/>
- Vendor terms: <https://antigravity.google/terms>
- OAuth native-app policy: <https://developers.google.com/identity/protocols/oauth2/native-app>
- Android downloaded-executable restriction: <https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission>
- Android 16 KB pages: <https://developer.android.com/guide/practices/page-sizes>
- Termux package build/layout references: <https://github.com/termux/termux-packages>
- Termux app/component license: <https://github.com/termux/termux-app/blob/master/LICENSE.md>

## 11. Final completion checklist

- [ ] One APK initializes and works without a separately installed Termux runtime.
- [ ] The official native Android CLI is selected/verified; no Linux fallback or
      translation layer exists.
- [ ] Real Bash, core tools, libraries, signed bootstrap, and authenticated package
      repository work from this app's prefix.
- [ ] CLI, terminal, packages, and diagnostics use one correct environment.
- [ ] User and agent share the selected project; HOME/credentials are not cwd.
- [ ] Setup, terms, trust, and approvals are visible and user-controlled.
- [ ] Terminal, files/editor, SAF transfer, package UI, and Studio are functional.
- [ ] Hosted and local surfaces have honest labels and separate trust boundaries.
- [ ] Untrusted URLs/pages/intents cannot access local privilege or fabricate state.
- [ ] Stop/update/cancel/crash cannot leak processes, callbacks, locks, or logs.
- [ ] Download/installed size, memory, CPU, battery, and tested-device scope are
      measured and reported honestly.
- [ ] Signing/migration, licenses, and vendor distribution/service permission are
      owner-approved.
- [ ] Offline, device, security, and release gates pass without weakened tests.

**End state:** a native, app-contained Android coding workspace—not a prototype
that hides missing shell, package, filesystem, consent, or security work behind a
WebView.

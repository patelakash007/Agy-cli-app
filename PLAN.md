# Agy-cli-app Work Board, Bug Register, and Delivery Plan

- **Updated:** 2026-11-13
- **Current integrated revision:** `80c35d0` on `main` (plan record; code at
  `9719035`, the F02/G01 share, permission and truthfulness pass with local
  A03/A08 native evidence). Before those were `0606eb7`/`41cdd05` (B06 installer
  slice), `4168c62` (review/hardening pass), `0f9250b`, and `33e2bd7`; the
  original reviewed baseline was `d728f94` (`Initial version`). Always inspect
  current `HEAD`, working-tree changes, and the latest instruction snapshot
  before work.
- **Plan state:** Active; the finished product does not exist yet.
- **Permanent product contract:** `AGENTS.md`
- **Supported initial direction:** ARM64 Android/Bionic, min API 24, sideloaded.

This is the only living implementation plan. An AI should be able to receive a
task ID, read both documents, and know what it may change, what it must prove,
what it must not claim, and how to hand off the result. This is a human-readable
work board, not a lock service or an automated multi-agent runner.

## 1. Owner quick start — no coding knowledge required

### 1.1 Which number do I give an AI?

- **`R24` is a bug ID:** a permanent record of a problem. The findings register
  maps it to **`C06`, its fix task**. Say either ID; the AI must resolve it.
- **`C06` is a task ID:** the bounded implementation and its acceptance checks.
- **`B01a` is a subtask:** a safe smaller part of B01. Finishing it does not finish
  B01b or the parent B01.
- **`P3` is an owner gate:** a decision about signing/migration, not a bug an AI
  can repair by inventing a key or deleting user data.

Do **not** delete a fixed bug. Its record explains what happened, what fixed it,
and which test protects it. An AI updates the status/evidence automatically.
If it comes back, reopen the same ID instead of losing that history.

### 1.2 Copy-and-paste prompts

**One AI, one task (recommended starting point):**

```text
Read AGENTS.md and PLAN.md. Implement B01a only. Check its dependencies and the
current working tree first. Add regression tests, run the relevant checks,
update PLAN.md with honest status/evidence, and tell me the exact next task.
Do not commit, publish, sign a release, or use live accounts/prompts.
```

**A specific bug:**

```text
Read AGENTS.md and PLAN.md. Fix R24 through its owning task. If a prerequisite
is missing, explain it and propose a safe bounded subtask; do not skip the gate.
Test it and maintain PLAN.md yourself. Do not commit or publish.
```

**Continue after a task:**

```text
Read the current AGENTS.md and PLAN.md. Verify the previous task's evidence,
then implement the next executable unassigned task. Update the plan and report
what remains. Do not silently mark untested device behavior DONE.
```

**Several independent AIs:** give this to ONE coordinator first, not all workers:

```text
Read AGENTS.md and PLAN.md. Coordinate five independent AIs using the initial
five-worker allocation. Prepare isolated working copies with the same current
instruction snapshot, assign one bounded task per worker, and give me a complete
pasteable prompt for each. You alone maintain the canonical PLAN.md. Integrate
and test results one at a time. Do not commit/push/publish without permission.
If you cannot isolate copies, serialize edits and use parallel read-only review.
```

**Fix everything that can safely be fixed:**

```text
Read AGENTS.md and PLAN.md. Act as coordinator and process all currently
executable bug-fix tasks in dependency order, using isolated parallel workers
where supported and authorized. Use a bounded patch and evidence checkpoint per
task. Maintain the canonical plan. Stop at owner/artifact/device/protocol gates
and explain the remaining choices; do not pretend the full product is finished.
Do not commit, publish, sign a release, or invoke paid/live prompts.
```

“All” cannot make missing native packages, a terminal license decision, a fresh
test device, vendor permission, or release credentials appear. A useful result
may be **all ready safety fixes implemented, some verification still pending**.

### 1.3 Status is progress; readiness is a separate question

| Status | Meaning | Who records it on the canonical plan? |
|---|---|---|
| TODO | Not implemented. May be ready or waiting for a prerequisite. | Single agent/coordinator |
| ACTIVE | Assigned and being worked on; identify the worker and base. | Single agent/coordinator |
| REVIEW | Patch exists, but integration/review or required acceptance is incomplete. Name what is missing. | Single agent/coordinator |
| DONE | Accepted code is present and all task-specific checks have named evidence. | Single agent/coordinator after verification |
| BLOCKED | A named external decision/artifact/proof is missing. Preserve partial work and state the gate. | Single agent/coordinator |

Usual transition: `TODO → ACTIVE → REVIEW → DONE`. A single agent can pass through
REVIEW in the same session if it performs all checks. Reopen DONE to TODO or
ACTIVE if evidence is invalidated or the bug returns. REVIEW is not a failure;
it is the honest status when, for example, Android device tests are unavailable.

**Ready to implement** means the implementation prerequisites are accepted and
present in this working copy, no external gate prevents that scope, and its write
scope is assigned. **Ready to finish** also requires integration and acceptance
evidence. A task may be implementable before it is verifiable on a device.

### 1.4 Coordinator's live assignment board

No implementation workers are assigned by this documentation change. The review
agents used to audit these documents are read-only, not fix-task owners.

| Worker | Task | Base + instruction snapshot | Isolated directory/branch | Exclusive writes + test command | State / next action |
|---|---|---|---|---|---|
| Single agent / 2026-11-13 | A02, A04a, A05, A06, A07, A08, A09, B01a, B01b, B02, B04a, B06 (preparation slice), C06, C07, C08a–c, F02 (share/permission mitigation), G01 (first pass), G02a | `80c35d0` + AGENTS/PLAN snapshot; clean worktree | this chat worktree | builder/tests + the runtime files named in each card | REVIEW/DONE per card; B06 live integration gated by C02; device acceptance batch is next |

Replace/add rows when allocating work; clear them only after recording the
completion receipt or abandoned-work reason. A chat session that disappears does
not authorize deleting its changes. Inspect and explicitly reassign first.

Only the coordinator writes this board during parallel work. Workers return
receipts rather than racing to edit this file. Shared-folder workers must receive
serialized edit/build windows; a Markdown ACTIVE label is not an atomic lock.

### 1.5 Worker prompt template — supplied with a real assignment

```text
Role: worker <unique label>. Task: <ID>. Base: <exact revision/patch snapshot>.
Working copy: <isolated path>. Read its current AGENTS.md and PLAN.md.
Exclusive write scope: <production paths + dedicated test paths>.
Prerequisites already present: <IDs/evidence>. Acceptance: <task checks>.
Do not edit the canonical working tree, shared runners, or canonical PLAN.md.
If scope/interfaces must change, pause and notify the coordinator.
Return a reviewable patch and the section 9 receipt, including commands/results,
missing device evidence, and proposed status. Do not commit/publish/sign or use
live accounts/prompts. Do not claim integrated DONE from an isolated test pass.
```

New worktrees/clones do not inherit uncommitted edits to these documents. The
coordinator must supply the latest approved snapshot explicitly, or ask for
permission to commit it before preparing copies. Do not start workers against
the old workflow just because their Git HEAD matches.

## 2. Current reality

### 2.1 What exists

The repository currently builds a small native Android **host** around the
Antigravity CLI. It has:

- A Java Android app with dashboard and hosted WebView surfaces.
- A foreground service, process supervisor, basic state handling and log
  display; CLI downloading is now the transactional installer below.
- A native `agy-pty` helper and browser/open shims.
- Direct selection of the vendor's Android ARM64 manifest rather than the
  upstream installer's unreliable `uname -o` logic.
- SHA-512 download verification and a tar extractor with basic path checks.
- A transactional CLI installer preparation slice (B06): strict manifest/artifact
  policy, ABI gate, bounded staging, AArch64 ELF check, atomic publish and an
  install record; live session stop/restart and device evidence are pending.
- A read-only share provider limited to `filesDir/share` with an offline
  containment policy (F02/R34 mitigation), and no shared-storage or all-files
  permissions (F02/R35).
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

### 2.3 Verified baseline and known failures

Task **A01 is DONE**: the repository contains offline Python/Java tests and a
separate build-smoke command. The harness now carries persistent regressions for
the defects fixed on 2026-11-13 (section 2.3).

Run:

```bash
bash tests/run_host_tests.sh
```

On 2026-11-13 the offline suite was extended and is green: `bash
tests/run_host_tests.sh` ran **41 Python cases and 38 Java checks, all passing
(exit 0)** after the B06 installer-policy and F02 share/permission cases. The former seven expected
failures (A02/R16, A05/R19, B02/R04) are now regressions, not permanent reds:

- A02/R16: SDK propagation, invalid numeric input and control/escape characters.
- A05/R19: data-descriptor input is rebuilt from central-directory metadata.
- B02/R04: strict trusted URL policy, including encoded slash, extra segments,
  non-default port, trailing-dot host and control characters.
- A07/A08/A09: secondary DEX preservation, fail-closed runtime assets, and the
  real `--fast` ZIP method (`tests/python/test_package_inputs.py`).
- A04a/G02a: release signing refusal and no-unattended-publication workflow
  policy (`tests/python/test_release_guard.py`).
- C07: concurrent atomic publication and failure preservation (Java runner).
- B06: manifest/artifact origin, digest/version, ABI gate, free-space preflight,
  AArch64/Android-linker ELF identity and strict install-record policy.
- F02/R34–R35: share-provider containment/read-only policy and manifest
  permission minimization.

The Java suite needs a JDK on `PATH`; this VM has none preinstalled, so the
2026-11-13 pass used a workspace-local Temurin JDK 17. The suite still does not
cover the Android API, process races, consent, downloader/installers, device
behaviour, or full tar security behavior.

Run the separate release/dev compile and package smoke check with:

```bash
bash tests/run_build_smoke.sh
```

The review ran `bash tests/run_build_smoke.sh`: both flavors PASS, exit 0. It
packages empty staging assets and used the system ZIP aligner on this host; it
does not prove native-asset staging or the Python aligner's correctness.

It must not install an APK, use credentials, download the CLI, invoke a live
agent, sign a release, or modify checked-in source/config files. No device tests,
APK installation, release signing, publication, or live agent prompts were
performed in the review. `file modules/core/pty/agy-pty` identified ARM64 Android
ELF with `/system/bin/linker64`; that is metadata, not native runtime proof.

### 2.4 Documentation consolidation

Repository guidance is now intentionally limited to `AGENTS.md` and `PLAN.md`.
The repository constitution is no longer staged into a user's vendor config.
The old runtime seed that claimed tools were already installed was removed; the
remaining onboarding fabrication and permissive defaults are still tracked by
B01 and must not be mistaken for completed safety work.

## 3. Findings register

These findings came from source review and local probes against the baseline.
Method names and current behavior outrank old line numbers. On 2026-11-13,
**R04, R16, R19 and R35 are FIXED (offline evidence)**, **R07, R18, R20, R33 and
R34 are PARTIAL**, and the remaining R01–R23 are still OPEN. Section 3.2 records
the per-bug state. “Reproduced” in this table means offline fixture
reproduction, not a device test, unless explicitly stated. Other implementation
findings are source-confirmed; goal blockers record missing capabilities.

Fix requests resolve through the owning task's card. For a multi-task bug, an AI
must name which part it is fixing and leave the bug open until the full listed
scope is accepted. Closure is recorded here and in section 9, never by deletion.

| ID | Severity | Current evidence | Owning tasks |
|---|---|---|---|
| R01 | Goal blocker | Asset staging contains PTY/certs/seeds, not a native `usr` bootstrap or package ecosystem. | D01–D04 |
| R02 | Goal blocker | PATH/environment handling omits a coherent prefix; fallback “bash” is system `sh`; child setup can lose CLI/helper paths. | D03 |
| R03 | High | CLI starts in `filesDir`, mixing project work with credentials/runtime/package state. | F01 |
| R04 | High, reproduced | Session URL validation accepts HTTP, arbitrary hosts, and suffix lookalikes. | B02, C01 |
| R05 | High | Hosted WebView uses substring allowlists, UA manipulation, mixed-content compatibility, and broad permission grants. | B03 |
| R06 | High | Deep links/clipboard/token-file heuristics can inject or fabricate login success without a correlated active flow. | B04a mitigation; B04b, C01 complete flow |
| R07 | High | Terminal text drives onboarding/TOS input; synthetic completed state and permissive defaults are seeded. | B01 |
| R08 | High | CLI updates are uncoordinated, non-transactional, weakly validated, and can replace a live working binary. | B06 |
| R09 | High | Tar parsing lacks full checksum/numeric/metadata/size/symlink policy needed for a general bootstrap. | B05, D02 |
| R10 | High | Activity booleans, static fields, URL extras, and cookies compete with process truth. | C01 |
| R11 | High | Stop/start races, stale input callbacks, stream ownership, and force-kill behavior can leak or truncate sessions. | C02 |
| R12 | High reliability | `readLine()` can stall on TUI output, CR updates, prompts, or URLs without LF. | C03 |
| R13 | Battery/reliability | Default auto-start, indefinite wake lock, 100 ms PTY polling, and fixed terminal size. | C01, C04b, C05, E01–E02 |
| R14 | Battery/privacy | Unrotated per-line logs and whole-file UI polling are unbounded; redaction is inconsistent. | C03, C04a |
| R15 | Architecture drift | Legacy Python/loopback/DNS/certificate fields are documented or present but are not the authoritative Java startup path. | D03, G01 |
| R16 | Build correctness, reproduced | SDK config is not applied consistently and config values lack strict validation. | A02 |
| R17 | Build provenance | Native helper build can mutate a tracked binary and silently use stale output after compiler failure; CI NDK provenance is absent. | A03 |
| R18 | Release security | Release path uses a tracked debug key/default credentials and does not enforce declared signing config. | A04a fail-closed guard; A04b identity/migration |
| R19 | Build correctness, reproduced | Python ZIP aligner mishandles local headers with data descriptors and can emit corrupt ZIPs. | A05 |
| R20 | Verification/truth | CI and old claims exceed device evidence; A01 now exposes some but not all missing gates. | G01, G02a publication guard; G02b release gates |
| R21 | Future file-security risk | Broad provider roots and raw-path guesses could expose private state or misuse SAF grants. | F02, F03 |
| R22 | Goal blocker | No real terminal, workspace/files/editor, package UI, or local agent screen exists. | D04, E01–E02, F01–F05 |
| R23 | High | URL broadcast trust is weak on API 24–32; package targeting is not sender authentication. | B07 |

### 3.1 Additional findings from the 2026-11-11 and later review passes

R24–R32 came from the 2026-11-11 review. R33 (installer), R34 (share provider)
and R35 (over-permission) were registered on 2026-11-13 and are kept in the same
table so stable IDs are not renumbered.

**R24–R32 status (2026-11-13):** R29–R32 are FIXED with persistent offline
regressions; R24–R28 have bounded production fixes in the working tree but are
**PARTIAL** because the required Android device evidence is still missing. The
build cases were originally reproduced with isolated offline review fixtures;
the new tests are checked in (section 3.2).

| ID | Severity / evidence level | Trigger and consequence | Exact starting evidence | Owning task |
|---|---|---|---|---|
| R24 | High, source + Android API reference | Stop/startup-timeout cleanup calls an API-26 method on supported API 24/25; `NoSuchMethodError` escapes `catch (Exception)` and aborts teardown. | `app/src/main/java/com/agy/core/CoreProcessSupervisor.java:251–260`; `ProcessUtils.java:38–45` under `app/src/main/java/com/agy/util/` also uses API-26 timed wait | C06 |
| R25 | High reliability, source-confirmed | Concurrent `writeText` calls share `<target>.tmp`; one can publish an inode while another still writes, then the fallback deletes the working target when the temp pathname is gone. | `app/src/main/java/com/agy/util/AtomicFileWriter.java:15–35`; active callers `app/src/main/java/com/agy/runtime/NetworkConfigWriter.java:23–35`, `NetworkMonitorHolder.java:18–27` | C07 |
| R26 | High update reliability, source-confirmed | An executable installed PTY helper larger than 1,000 bytes is always reused; upgrading the APK does not deliver changed native helper fixes. | `app/src/main/java/com/agy/runtime/ShellBridgeInstaller.java:69–91`; `RuntimeManager.java:105–110` | C08a |
| R27 | High reliability, source-confirmed | Interrupted CA extraction after 1,000 bytes leaves a partial final file that future starts accept as complete; new bundled CA bytes are also ignored. | `app/src/main/java/com/agy/runtime/CoreInstaller.java:29–49`; `app/src/main/java/com/agy/core/CoreEnvironment.java:30` | C08b |
| R28 | High reliability, source-confirmed | Delayed refresh truncates live executable URL shims; concurrent execution or failed writes can observe incomplete scripts and lose auth/session URL delivery. | `app/src/main/java/com/agy/runtime/ShellBridgeInstaller.java:99–107,267–279`; `app/src/main/java/com/agy/core/CoreLauncher.java:83–96` | C08c |
| R29 | Medium test correctness, fixture-reproduced | Combining `--python-only --java-only` disables both suites yet prints PASS with exit 0. | `tests/run_host_tests.sh:29–32,44,54,93–95` | A06 |
| R30 | Low build/claim correctness, fixture-reproduced | `FAST_BUILD=0` and `1` both deflate a plain-text asset, despite advertised uncompressed/cache behavior; the orchestrator clears intermediates. | `modules/builder/06_package.sh:36,45–46`; `modules/builder/build_apk.sh:80–81,93–96`; `build.sh:6` | A09 |
| R31 | Medium latent build correctness, fixture-reproduced | A valid secondary `classes2.dex` next to `classes.dex` is omitted silently from a successful APK. Today's small app is not claimed to require multidex. | `modules/builder/06_package.sh:12,33–37`; `modules/builder/04_dex.sh:22–28` | A07 |
| R32 | High build verification, fixture-reproduced | Missing runtime asset input still produces a successful asset-free APK; existing compile smoke uses empty assets and cannot catch this packaging loss. | `modules/builder/06_package.sh:38–46`; `tests/run_build_smoke.sh:39,48–58`; runtime asset use `app/src/main/java/com/agy/runtime/CoreInstaller.java:38` | A08 |
| R33 | High installer reliability, source-confirmed | `CliDownloader` checked no ABI, followed redirects off-origin, read the manifest without a bound, deleted the live binary before extraction, auto-started an install at launch, kept no install identity/record, and allowed two independent jobs. | `app/src/main/java/com/agy/runtime/CliDownloader.java` (deleted by the B06 preparation patch); `MainActivity.triggerDownload/promptDownload` | B06 |
| R34 | High file-sharing exposure, source-confirmed | `AppFileProvider` addressed absolute app paths and allowed `filesDir`, `cacheDir`, the whole app data directory, external storage and external files/cache — including `.gemini` credentials, the CLI and prefix — and accepted write/read-write modes. | `app/src/main/java/com/agy/util/AppFileProvider.java` (deleted broad-root resolver); `UriDispatcher`/`DocumentsContractHelper` (deleted, unused) | F02 |
| R35 | Medium over-permission, source-confirmed | The manifest requested all-files access, legacy shared-storage read/write, notification and battery-exemption permissions, and every launch opened the all-files settings page, although the app uses only app-internal storage and SAF. | `app/src/main/AndroidManifest.xml:35–41`; `MainActivity.requestStoragePermissions` | F02, G01 |

Fixture evidence from the read-only build audit: R29 ran the unchanged runner
with both selection flags (exit 0, no suites); R30 packaged a fresh 4 KiB text
asset with both FAST_BUILD values (both exit 0, DEFLATED/type 8); R31 packaged two
independently compiled/D8-generated DEX fixtures (only primary present, ZIP/CRC
valid); R32 packaged valid inputs with a nonexistent assets directory (exit 0,
zero assets). Fixtures were temporary and not committed. The owning tasks must
add persistent regressions and rerun these cases against their patch.

R26–R28 are distinct from A03 build provenance and B06 downloaded CLI replacement:
they affect already-installed APK-owned helper/certificate/shim copies. R25 is an
active configuration-write race, not merely a future editor risk. The suspected
D8 space-containing-path failure was disproved by the audit and is **not** a bug
entry. Do not convert unverified suspicions into findings.

### 3.2 Status after the 2026-11-13 single-agent pass

Statuses below describe work committed through the revisions named in section 9
(latest `9719035`) unless a row states otherwise. “FIXED” means the named offline
regression passes; “PARTIAL” means the code is present but a required
device/integration check is still missing.

| ID | Status | Evidence / remaining gate |
|---|---|---|
| R01 | PARTIAL | Orphan cleanup is scoped to this flavor's exact `filesDir` via `ProcessScope` (release/dev cannot kill each other); the stale tracked helper was removed. The app-specific `usr` bootstrap/repository (D01–D04) does not exist yet. |
| R04 | FIXED | Strict `CoreRuntimeState.isValidSessionUrl`; consumers use it; 15 Java URL checks pass. Auth URLs are exact-host HTTPS only, with no arbitrary query heuristic. C01/F04 consumer work still open. |
| R05 | PARTIAL | WebView keeps its real UA, uses `MIXED_CONTENT_NEVER_ALLOW`, denies every permission request, refuses third-party cookies, and only exact `https://antigravity.google.com` stays in-app; `file:`/`content:`/`intent:`/`javascript:`/`data:` are blocked. Cleartext is disabled outside loopback. WebView device tests still pending. |
| R07 | PARTIAL | Automatic login/TOS keystrokes and the Tab-Tab-Enter dashboard shortcut removed; settings/onboarding seeding deleted. No B01a/B01b device tests yet; E02 still needed for usable setup. |
| R09 | PARTIAL | Pure `TarPolicy` verifies checksum/strict numerics/type/size/path/symlink rules; extractor enforces entry/total bounds and duplicates. Offline policy tests pass; real-archive/device extraction pending. |
| R16 | FIXED | Config validation + manifest `<uses-sdk>` propagation + D8 min-api wiring; 7 Python config tests pass; both flavors compile in smoke. |
| R17 | PARTIAL | Staging always compiles `agy-pty.c` into `build/native/arm64-v8a` with a required NDK clang, verifies ELF64/AArch64/linker/16 KB `p_align`, stages no fabricated seed state, and never reads or mutates the tracked binary (deleted). A real NDK/CI build remains the missing evidence. |
| R18 | PARTIAL | Release signing fails closed without owner inputs (exit 3, signer not invoked); A04b identity/migration still BLOCKED (P3). |
| R19 | FIXED | Aligner rebuilds headers from the central directory; data descriptors preserved; ZIP64/encryption/unknown methods rejected. |
| R20 | PARTIAL | G02a guard: tags no longer publish, least-privilege permissions, explicit `PUBLISH` + protected environment, release job still refuses. First G01 pass removed false release-body claims, the dead vendor-input download and unused notification/battery permissions; the final claims pass remains. G02b remains blocked. |
| R23 | PARTIAL | Public `ACTION_OPEN_URL` broadcast input and the shim's `am broadcast` fallback were removed; discovery uses owned CLI stdout only. C01 generation-aware state remains required for full closure. |
| R24 | PARTIAL | API-26 `destroyForcibly`/timed `waitFor` guarded or replaced; teardown steps isolated with `Throwable`. API 24/25 device run still pending. |
| R25 | PARTIAL | Writer-owned unique temporaries, atomic rename, no in-place fallback; host concurrency tests pass. Android callers not device-tested. |
| R26 | PARTIAL | Helper published by content hash + AArch64 ELF check; old helper preserved on failure. APK upgrade device test pending. |
| R27 | PARTIAL | CA bundle validated by SHA-256 and published atomically; interrupted partial repaired. Installed-tool TLS still D03. |
| R28 | PARTIAL | Scripts staged/mode-checked/renamed; identical content skipped; delayed startup refresh removed. Bionic execution device evidence pending. |
| R29 | FIXED | Both selection flags exit 2; unknown args exit 2; zero-case discovery fails; PASS prints executed counts. |
| R30 | FIXED | `--fast` stores plain-text assets (ZIP_STORED) and the banner/cache claim is truthful. |
| R31 | FIXED | All `classes*.dex` packaged or the build fails; invalid DEX/empty output rejected. |
| R32 | FIXED | Runtime packaging fails when assets/marker/helper/CA are missing; `--compile-only` is explicit; smoke is labelled compile-only. |
| R34 | PARTIAL | `SharePathPolicy` + hardened `AppFileProvider` expose only read-only regular files under `filesDir/share` via relative ids; absolute app paths, `.gemini`/CLI/prefix/log data, traversal, dot/empty segments, control characters, symlink escapes, directories and write modes are rejected. Offline R34 regression passes. Full F02 browser/editor and device grant tests remain. |
| R35 | FIXED | All-files, legacy shared-storage, notification and battery-exemption permissions, `requestLegacyExternalStorage` and the launch all-files prompt were removed; static manifest regression plus share-provider tests pass. No device matrix run. |
| R33 | PARTIAL | `CliInstallPolicy`/`CliInstallMetadata` + `CliInstaller` replace `CliDownloader`: ABI gate before network, exact manifest URL and approved HTTPS artifact host, redirects refused, bounded manifest and declared-size download, streaming SHA-512, conservative free-space check, unique cache staging, AArch64+linker ELF verification, unique sibling temp + same-directory atomic rename, strict install record, one job at a time, cancellation API, explicit Install/Update with no launch-time download. Offline regressions green; C02 live stop/restart integration, device install/upgrade and a real vendor download remain pending. |

## 4. Owner decisions and external gates

Implementation agents must not invent answers to these gates.
An owner decision is not the same as an engineering detail. The owner can ask:
“Read gate P4, explain two realistic options in plain language, recommend one,
and tell me exactly what I must approve.” The AI investigates the details; the
owner chooses legal/product/key-custody commitments. No need to answer every gate
before the independent safety tasks start.

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

### V1 — Actual Android CLI authentication flow — BLOCKED (engineering evidence)

This is a protocol-proof gate, not a demand that the owner invent an auth design.
Record CLI version/hash, documented browser/callback or code-entry flow, waiting
state, correlation/nonce rules, error/cancel behavior, and supported Android
observations. User participation is required for real login/consent; do not run
live paid agent prompts or automate approval. B04a can remove dangerous behavior
without this proof; B04b cannot invent a working supported flow. P5 separately
covers ACP and its integration approval.

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

### 6.1 The next single-agent task

B05 and B03 are now implemented and need device/real-archive acceptance. **B06's
preparation slice is implemented and REVIEW**: the transactional installer and
its offline policy exist, but live stop/restart integration is C02 and no device
install/upgrade or real vendor download has run. The next executable work is
therefore the device/real-environment acceptance batch: B01a/B01b/B04a, B03, B05,
C06–C08 and B06 on API 24/25 and API 26+. C01/C02 become implementable only after
B01 is accepted; C02 then owns live CLI replacement. F02's share/permission
mitigation (R34/R35) and G01's first truthful-text pass are REVIEW with offline
evidence; they do not finish their parent features. Build/parser fixes did not
wait for consent work: that was a priority suggestion, not a technical
dependency.

### 6.2 Initial five-worker allocation

These are **suggested assignments, not active claims**. Each depends only on the
existing A01 harness. Check the actual code/base and assignments before launch.
All are ready for implementation; device-dependent acceptance can remain REVIEW.
Test paths marked “new” are allocations to create, not files claimed to exist.

| Worker | Task / bug | Exclusive production writes | Exclusive tests / entry point | Why independent now |
|---|---|---|---|---|
| AI-1 | B01a / R07 | `app/src/main/java/com/agy/core/OpenUrlWatcher.java`, `app/src/main/java/com/agy/runtime/OnboardingSeeder.java`, `app/src/main/java/com/agy/ui/MainActivity.java` | New `tests/java/b01a/` and `tests/run_b01a_tests.sh` | Removes unsolicited input/defaults without inventing the future terminal or state model. |
| AI-2 | A02 / R16 | `modules/builder/01_gen_config.py`, `modules/builder/build_apk.sh`, `modules/builder/04_dex.sh` if needed | `tests/python/test_gen_config.py`, `tests/run_build_smoke.sh` | SDK/config propagation is independent of runtime consent, tar, and ZIP payload handling. |
| AI-3 | A05 / R19 | `modules/builder/align.py` | `tests/python/test_align.py` | ZIP transformation has its own fixtures and no runtime interface changes. |
| AI-4 | B05 / R09 | `app/src/main/java/com/agy/install/TarExtractor.java`, `TarHeader.java`, `TarPath.java` in that same directory; a small dedicated policy class there if needed | New `tests/java/b05/` and `tests/run_b05_tests.sh` | Archive policy can be tested offline before transactional installer integration. |
| AI-5 | C06 / R24 | `app/src/main/java/com/agy/core/CoreProcessSupervisor.java`, `CoreServerService.java` in that same directory, `app/src/main/java/com/agy/util/ProcessUtils.java` | New `tests/java/c06/` and `tests/run_c06_tests.sh`; explicit API 24/25 device checks | API compatibility/exception-safe cleanup is a narrow fix, not the full C02 lifecycle redesign. |

This table is an initial ownership envelope, not permission to change every file
listed. Use the smallest actual patch. Never widen it into another worker's files
or tests without reallocation. The coordinator alone owns `PLAN.md`/`AGENTS.md`
and integrations. Do not edit `tests/run_host_tests.sh` concurrently to wire new
runners: workers provide standalone checks; the coordinator wires approved checks
serially after integration. No production fix is implemented by this allocation.

Run tests/builds in isolated working copies. Existing scripts use shared
`build-test/` paths; running them concurrently in one folder is not safe even when
source writes are disjoint. A03 also changes native build behavior and must not
run the old source-mutating full build in the coordinator's folder.

### 6.3 Other work that can start without waiting for the whole app

- **B02:** ready after A01, but serialize with B01a/B04a/B01b because URL consumers
  share `OpenUrlWatcher`, `MainActivity`, and state APIs. Isolation permits parallel
  exploration, not blind merging of conflicting rewrites.
- **B04a:** defensive removal of unsolicited callbacks/clipboard reads is ready;
  B04b's complete auth flow is separately gated. Shares UI with B01a/B02/B03.
- **A04a:** fail closed on unconfigured release signing without choosing the real
  key. Serialize shared builder edits with A02/A03. Full A04b still needs P3.
- **G02a:** stop unattended publication without claiming the release is ready.
  One writer for the workflow; coordinate with A03/G01/A04.
- **G01:** read-only claims/legacy audit can run alongside fixes. Apply code/CI text
  corrections in the owning lane, not by overwriting another worker's patch.
- Future task-specific policy fixtures can be prepared early if the coordinator
  names a separate subtask, interface, and test-only write scope. A fixture is not
  completion of a blocked production feature.

### 6.4 Dependency reasons — not arbitrary waiting

Task cards in section 7 are authoritative for scope/acceptance. This table explains
why an implementation must wait; file conflicts from section 6.2/6.3 are separate.
Unless marked “acceptance only,” prerequisites must be accepted and available in
the worker's code before implementing the dependent integration.

| Task | Real prerequisites / gate | Why it needs them |
|---|---|---|
| A02, A05, B01a, B05, C06 | A01 | Deterministic regressions; these five do not need each other. |
| A06, A07, A09, C07, C08b, C08c | A01 | Bounded runner/package/publication fixes; coordinate their shared files separately. |
| A08 | A01; A03 for native-asset-inclusive acceptance | Fail-closed package inputs can be tested with fixtures; shipped helper inventory must use reproducible native output. |
| C08a | A03, C02 for integrated helper update | Validate the shipped ELF and replace it only under owned session/update rules; offline staging fixtures can be prepared earlier. |
| A03 | A02 | Native API/build metadata must agree with validated generated configuration. |
| A04a | A01 | Offline guard tests; no private identity is needed to refuse unsafe release signing. |
| A04b | A02, A03, A04a, P3 | Validate actual artifact metadata/native provenance and approved identity/migration. |
| B01b | B01a | Finish honest seed/UI behavior without reintroducing the removed automatic input/defaults. |
| B02 | A01 | Define/test the shared exact URL policy; no terminal or package artifacts required. |
| B03 | B02 | Navigation and permission decisions must use the agreed URI/origin policy. |
| B04a | A01 | Refusing unsolicited input does not require understanding a successful vendor auth flow. |
| B04b | B01, B02, B04a, C01, C02, V1 | Correlate user input with an owned active process/generation, not global guesses. |
| C01 | B01, B02 | State must not be driven by fabricated setup or untrusted URLs. |
| C02 | C01, C06 | Process/input ownership uses the accepted generation model and API-compatible cleanup. |
| B07 | B02, A02, C01; A04b only if signature permission is retained | Exact URL/flavor identity plus generation ownership; public debug signing is not sender trust. |
| C03 | C01, C02 | Raw output events must stay attached to the correct process; join/drain must precede stream replacement. |
| C04a | C03 | Bound/rotate diagnostic output after writer and stream ownership is defined. |
| C04b | C01, C02 | Lease/notification state must follow real owned engine/session state. |
| C05 | A03, C02 | Reproducible helper build plus agreed owned-group stop contract. |
| B06 | A02, B05; C02 before live update integration | Validate artifact identity, extract safely, and publish only when no live session owns the binary. |
| F01 | C01, B01 | Workspace switching must be an owned state transition; vendor setup must remain user-owned. |
| E01 | A03, C02, P4 | Approved component/license plus reproducible native build and process contract. |
| E02 | E01, C01, C02, C03; D03 for full Bash mode | Real terminal IO/state; explicit recovery-shell mode can precede the native bootstrap. |
| D01 | A03, P2, P3 | Real app-specific artifacts need source/prefix/repository choices and key custody, not copied Termux files. |
| D02 | D01, B05, B06 | Authentic bootstrap and shared staging/transaction rules prevent live-prefix corruption. |
| D03 | D02, F01 | End-to-end environment/tool proof needs real native tools and the selected project. |
| D04 | D01, D02, D03, E02, F01 | Real signed repository/dependencies, authoritative environment, terminal, and project. |
| F02 | F01, E02 | Browser/editor operate on the approved project and share only narrow project files. |
| F03 | F01, F02 | SAF imports/exports need project bounds and collision/atomic-save policy. |
| F04 | B02, B03, C01, E02, F01 | Safe hosted UI must not replace or disrupt the local project/terminal. |
| F05 | F04, D03, E02, F01, P1, P5 | Verified documented protocol, approval/lifecycle behavior, and approved integration boundary. |
| G02a | A01 | Publication can be disabled before legal/signing/device gates are resolved. |
| G02b | A01–A05, G02a, applicable B–F tasks, P1–P4, owner publication approval | Publication requires combined security/artifact/device/legal evidence, not a green compile. |

Example: five AIs can fix ZIP alignment, config validation, tar extraction,
automatic consent, and API-24 cleanup independently. But a package manager cannot
be finished before real packages/repository exist, and generation-aware auth
cannot be finished before a process-generation model exists. Removing a dangerous
callback now is nevertheless possible: that is why B04a and B04b are separate.

When a prerequisite changes, the coordinator updates both this explanation and
the affected card, records why, and refreshes assignments. Do not silently remove
a gate to mark work DONE. Do not postpone URL, consent, signing, or workspace
isolation until after adding more privileged filesystem/package features.

## 7. Task catalog

Each card is an assignment contract, not a declaration that all code in its
starting files may be rewritten. Allocate actual write paths and dedicated tests
on the live board. **Starting files** are current evidence locations; re-read
them. **Depends on** is an implementation requirement unless the card explicitly
splits preparation from integration. **Acceptance** includes the existing bullets
and relevant section 8 checks. Required device evidence cannot be replaced by
compile/source checks. Statuses here and the board must agree.

Parent tasks with children close only when every child is accepted. Requests for
a parent or multi-task bug start with its next executable child, not a giant fix.

### A — Build, tests, native provenance, and signing

#### A01 — Offline regression entry point — DONE

- **Evidence:** `tests/run_host_tests.sh`, Python fixtures, the Java host runner,
  and `tests/run_build_smoke.sh` exist and run without credentials or live services.
- **Important:** the host suite is intentionally red for A02/A05/B02; A01 did not
  declare those defects fixed.

#### A02 — Truthful build configuration and SDK metadata — DONE (offline)

- **Depends on:** A01.
- **Starting files / writes:** `modules/builder/01_gen_config.py`,
  `modules/builder/build_apk.sh`, `modules/builder/04_dex.sh` if needed;
  `tests/python/test_gen_config.py`, `tests/run_build_smoke.sh`.
- **Conflict:** A03/A04a/A09 share the orchestrator; A08 shares the smoke runner.
- Strictly validate numeric SDK/version fields, package IDs, XML values, and Java
  literals. Apply configured min/target SDK to generated manifest and D8 while
  keeping compile SDK, target SDK, app version, runtime schema, and CLI version
  distinct.
- Keep normal min 24/target 28. A min 26/target 31 fixture must update all outputs;
  malformed numeric/control-character input must fail clearly; both flavors must
  compile without modifying checked-in generated files.

#### A03 — Reproducible fail-closed native host build — REVIEW (real NDK/CI build pending)

- **Depends on:** A02.
- **Starting files:** `modules/builder/05_stage_assets.py`,
  `modules/builder/build_apk.sh`, `.github/workflows/build-and-release.yml`,
  `modules/core/pty/agy-pty.c`. Reserve shared builder/workflow ownership first.
- Pin/setup the Android NDK, compile helper output under `build/native/arm64-v8a`,
  never mutate/use a stale tracked source-tree binary implicitly, and verify ELF
  machine, Android linker, dependencies, RUNPATH, API target, and page-size support.
- Missing/failed toolchains must fail. Local and CI checks must agree and record
  versions/provenance.
- **Local evidence 2026-11-13:** NDK `27.0.12077973`
  (`aarch64-linux-android24-clang`) compiled `agy-pty.c`; `05_stage_assets.py`
  verified ELF64/AArch64/`/system/bin/linker64`/16 KB alignment and staged it
  with the CA bundle. The full pipeline compiled 88 classes, D8 produced one
  DEX, and non-compile-only packaging produced a 359,681-byte APK containing the
  marker, helper and CA bundle (ELF magic confirmed inside the archive). CI
  provenance and device execution remain the missing acceptance.

#### A04 — Separate development and private release signing — BLOCKED (P3)

- **Children:** A04a is ready; A04b needs A02/A03/A04a and P3. The parent remains
  blocked; refusing unsafe signing is not choosing/validating a release identity.
- Dev may use an explicit debug identity. Release without owner-controlled inputs
  must fail. Verify expected certificate digest, flavor, app ID, version,
  debuggable state, native artifacts, and schemes valid for min API.
- No secret in git/arguments/logs and no publication until update/data migration
  is approved and tested.

##### A04a — Refuse unconfigured release signing — DONE (offline)

- **Depends on:** A01. **Conflict:** A02/A03/G02a shared builder/workflow changes.
- **Writes:** `modules/builder/build_apk.sh`, `08_sign.sh`, `09_verify.sh` in that
  directory; dedicated offline signing-policy fixtures (fake signer, not real key).
- Make the release route fail before signing when explicit owner-controlled
  inputs/expected identity are absent. Keep an explicitly labelled dev route.
  No automatic key generation as a release fallback; no secrets in command text.
- **Acceptance:** fake-tool tests prove missing/default/wrong-flavor release inputs
  fail without invoking signing; permitted dev behavior is clear. Do not sign an
  actual release while testing the guard. Leave P3/A04b blocked.

##### A04b — Approved identity, verification, and migration — BLOCKED (P3)

- **Depends on:** A02, A03, A04a, P3.
- **Starting files:** the A04a signing/build files and workflow; narrow allocation
  after the owner approves certificate, secret custody, and migration design.
- **Acceptance:** all parent A04 identity/flavor/version/scheme checks plus named
  update/data-preservation device evidence. No default public debug key accepted
  for release or internal signature trust.

#### A05 — Repair or reject unsupported ZIP alignment inputs — DONE (offline)

- **Depends on:** A01.
- **Writes:** `modules/builder/align.py`, `tests/python/test_align.py`.
- Use reliable central-directory metadata; correctly preserve/reconstruct data
  descriptors or deliberately reject them. Validate signatures/bounds and reject
  unsupported ZIP64/encryption rather than emit a corrupt success.
- Stored/deflated payload bytes, CRCs, names, extras, flags, and comments must
  survive supported alignment. Existing data-descriptor regression must pass or
  receive a documented nonzero unsupported error.

#### A06 — Never report green when no tests ran — DONE (offline)

- **Depends on:** A01. **Bug:** R29.
- **Writes:** `tests/run_host_tests.sh`, dedicated Python subprocess tests for the
  runner. **Conflict:** coordinator registration of other workers' new runners.
- Reject contradictory suite flags; count actual suite/case execution so missing
  discovery or omitted suites cannot silently produce an all-tests-pass claim.
  Keep `--help` a successful non-test operation labelled as help, not PASS.
- **Acceptance:** both flag orders fail nonzero; each single-suite selection runs
  only that suite; default runs both; unknown args fail. Baseline regression exit
  codes remain visible. Regression commands must not recursively invoke their own
  discovery or depend on existing `build-test/` output.

#### A07 — Preserve or explicitly reject secondary DEX output — DONE (offline)

- **Depends on:** A01. **Bug:** R31.
- **Writes:** `modules/builder/06_package.sh`, optionally `04_dex.sh` and
  `build_apk.sh` in that directory for the handoff; dedicated packaging fixtures.
- Package every valid numbered DEX output, or fail clearly before emitting an APK
  when unsupported additional outputs are present. Do not silently discard code.
  Supporting legacy multidex execution is a separate Android architecture/test
  decision; packaging all files alone is not proof of API-24 multidex startup.
- **Acceptance:** two independently D8-generated fixtures are both preserved with
  exact hashes/ZIP CRCs, or explicitly rejected nonzero. Normal single-DEX APKs
  still package. No claim that the current app already exceeds DEX limits.
- **Conflict:** A02 may own `04_dex.sh`/orchestrator; A08/A09 own package-stage
  changes. Serialize this packaging lane or assign one shared writer.

#### A08 — Asset-inclusive packaging validation — DONE (offline, native gate A03 pending)

- **Implementation prerequisite:** A01; **native-asset acceptance gate:** A03.
  Fixture-only input/inventory policy can be implemented before the native gate.
- **Bug:** R32. **Writes:** `modules/builder/06_package.sh`,
  `tests/run_build_smoke.sh` or a new dedicated asset-smoke runner; stage changes
  only by agreement with A03. Conflict with A07/A09 and A02's smoke changes.
- Make absent/invalid runtime input fail on runtime-bearing packaging paths. Keep
  any intentional compile-only/empty-asset mode explicit and accurately labelled.
  Add an offline asset-inclusive check rather than relabelling empty smoke as proof.
- **Acceptance:** missing assets/required inventory fails before a usable-artifact
  success report; fixture assets survive with exact bytes; shipped runtime marker,
  helper ELF, and CA inventory are checked using A03 output. No signing/network or
  mutation of a tracked native binary. Keep existing compile smoke coverage.
- **Native-asset evidence 2026-11-13:** with the NDK 27 helper, non-compile-only
  packaging required and included `runtime/runtime.marker`,
  `runtime/bin/agy-pty` and `runtime/certs/ca-certificates.crt`.

#### A09 — Make fast-build behavior truthful — DONE (offline)

- **Depends on:** A01. **Bug:** R30. **Low priority**, not a prerequisite for the
  runtime safety tasks.
- **Writes:** `build.sh`, `modules/builder/build_apk.sh`, `06_package.sh` in that
  directory; dedicated compression-mode fixtures. Serialize with A02/A07/A08.
- Either implement the advertised uncompressed mode with measurable ZIP behavior,
  or remove the misleading option/claim and document real CLI behavior in `--help`.
  Do not claim a cache while deleting/rebuilding the advertised cached intermediates.
- **Acceptance:** normal/fast plain-text asset fixtures show the documented ZIP
  methods, or the removed option fails clearly; payloads/CRCs unchanged. Real
  caching, if added, must have correct input invalidation, not stale-artifact reuse.

### B — Consent, web/auth trust, archives, and installers

#### B01 — Remove fabricated onboarding and unsafe consent — TODO

- **Children:** B01a and B01b; both must be accepted before the parent closes.
- Stop automatic login selection, TOS/navigation keystrokes, permissive
  `always-proceed` settings, fixed installation state, guessed vendor project
  records, and overwriting incomplete state. Preserve nonempty user files.
- Show `SETUP_REQUIRED` and require terminal interaction. Suspicious output such
  as `[Done]` must never write stdin. Existing installs get a visible review path,
  not silent settings changes.
- **Partial note:** documentation consolidation removed repository `AGENTS.md`
  injection only; all other B01 behavior still requires implementation/tests.

##### B01a — Stop unsolicited input and permissive defaults — REVIEW

- **Depends on:** A01. **Bug:** the input/defaults part of R07.
- **Writes:** `app/src/main/java/com/agy/core/OpenUrlWatcher.java`,
  `app/src/main/java/com/agy/runtime/OnboardingSeeder.java`,
  `app/src/main/java/com/agy/ui/MainActivity.java`; dedicated B01a tests/runner.
- Remove output-triggered login/color/TOS keystrokes, backup threads, and the
  dashboard's synthetic Tab-Tab-Enter “finish setup” shortcut. Do not replace them
  with another automatic acceptance path. Fresh defaults must not enable
  `always-proceed` or non-workspace access. Preserve existing user settings.
- **Acceptance:** fake stdin receives zero bytes for login/TOS/`[Next]`/`[Done]`
  output, repeated/split suspicious text, and stop/restart callbacks. Fresh
  defaults are non-permissive; existing nonempty settings remain byte-identical.
  UI indicates setup needs real user interaction; device verification is named
  or pending. Seed fabrication removal belongs to B01b, usable setup to E02.
- **Conflict:** B01b/B02/B03/B04a/C01 share these files; do not start overlapping
  integration rewrites simultaneously. C07 may change the text-writer interface.

##### B01b — Preserve vendor state and show honest setup requirements — REVIEW

- **Depends on:** B01a. **Bug:** the fabricated/overwritten state part of R07.
- **Starting files:** `app/src/main/java/com/agy/runtime/OnboardingSeeder.java`,
  `CoreInstaller.java` in that directory, `app/src/main/java/com/agy/ui/MainActivity.java`,
  `modules/core/seed/`. Allocate a bounded slice; inspect asset references before
  removing anything. No new Markdown files or user-project instruction injection.
- Stop seeding completed onboarding/fixed vendor installation records/guessed
  project entries. Preserve all nonempty user state, including incomplete wizard
  state. Offer visible review for existing unsafe settings, not silent migration.
- **Acceptance:** absent/completed/incomplete/malformed/nonempty vendor fixtures
  are not fabricated or overwritten; startup reports SETUP_REQUIRED honestly.
  B01a no-input regressions stay green. M0 remains incomplete without E02.

#### B02 — One strict trusted-URL policy — DONE (offline; consumers migrated)

- **Depends on:** A01.
- **Starting files:** `app/src/main/java/com/agy/core/CoreRuntimeState.java`,
  `OpenUrlWatcher.java` in that directory; `app/src/main/java/com/agy/oauth/AuthUrlMatcher.java`,
  `app/src/main/java/com/agy/ui/MainActivity.java`, URL utilities and
  `tests/java/HostTestRunner.java`. Split policy definition and consumer migration
  if needed; do not mark the parent DONE until every active consumer is migrated.
- **Conflict, not prerequisite:** B01a/B01b/B03/B04a/C01 share URL/UI/state files.
- Centralize URI parsing and classify before acting. Initial session rule: HTTPS,
  exact `antigravity.google.com`, no userinfo, default/443 port, exactly one opaque
  segment after `/r/`; preserve valid query/fragment encoding.
- Reject HTTP, lookalike/suffix hosts, controls, encoded slash/traversal, extra
  path segments, and auth-looking arbitrary hosts. Migrate every consumer.
- Existing R04 host failures must become green while official encoded examples
  remain intact.

#### B03 — Safe hosted WebView navigation and permissions — REVIEW (device tests pending)

- **Depends on:** B02.
- **Starting files:** `app/src/main/java/com/agy/ui/MainActivity.java`,
  `app/src/main/java/com/agy/oauth/CustomTabLauncher.java`, shared URL policy;
  dedicated origin/permission fixtures plus Android WebView tests.
- Exact top-level origin policy; OAuth goes to supported external browser/Custom
  Tab; no UA spoofing, mixed content, TLS bypass, unknown permission grant,
  arbitrary scheme, or local shell bridge. Keep deliberate file upload working.
- Verify origin and ask the user before any supported camera/mic grant. Do not
  log raw callbacks/session URLs or infer auth from cookies.

#### B04 — Correlated authentication input — BLOCKED (V1)

- **Children:** B04a defensive removal is ready; B04b completes the correlated
  flow after B01/B02/C01/C02 and actual versioned Android auth evidence.
- Ignore unsolicited/stale/duplicate callbacks. Remove automatic clipboard reads.
  Explicit paste must bound/control-check one input and target the active awaiting
  generation. Token files mean “possible saved state,” not authenticated.
- Cancellation/retry must recover; an intent match alone never writes stdin or
  announces success.

##### B04a — Refuse unsolicited auth input/success — REVIEW

- **Depends on:** A01. No successful-vendor-flow proof is needed to refuse unsafe
  input. **Writes:** `app/src/main/java/com/agy/oauth/DeepLinkHandler.java`,
  `app/src/main/java/com/agy/ui/MainActivity.java`; dedicated callback/clipboard tests.
- Disable callback-to-stdin without a verified active correlation, automatic
  clipboard reads, and unconditional “authentication successful” announcements.
  Do not silently accept the other flavor's scheme or use token/cookie heuristics
  as proof. Keep intentional user input clearly distinct from unsolicited input.
- **Acceptance:** foreign-flavor/unsolicited/stale/duplicate/multiline/oversize
  callback fixtures cause no stdin bytes or fabricated success; resuming the UI
  does not read clipboard automatically. Tests state what valid auth is not yet
  supported. Serialize UI edits with B01/B02/B03/C01.
- **Residual cleanup 2026-11-13:** the dead success-callback/toast plumbing in
  `MainActivity` was removed; deep links are shape-checked and ignored.

##### B04b — Verified generation-bound auth flow — BLOCKED (V1)

- **Depends on:** B01, B02, B04a, C01, C02, V1.
- **Starting files:** OAuth classes, service input/state APIs, Activity consent UI.
  Inspect the vendor's exact version/flow before allocating writes.
- **Acceptance:** only the active awaiting generation accepts bounded user-approved
  input; state/nonce/callback rules match the documented observed flow; stale,
  cancelled, duplicate, wrong-flavor, malformed callbacks fail. Real setup/auth
  device evidence requires the user's participation, not live automated consent.

#### B05 — Bounded policy-specific archive extraction — REVIEW (device extraction pending)

- **Depends on:** A01.
- **Writes:** `app/src/main/java/com/agy/install/TarExtractor.java`, `TarHeader.java`,
  `TarPath.java` in that directory and a small dedicated policy seam if needed;
  dedicated B05 offline fixtures/runner. Do not redesign shared filesystem utilities
  or wire live CLI updates in this packet.
- Validate tar checksum/numeric fields, overflow, truncation, supported metadata,
  entry count, total bytes, duplicate conflicts, parent symlinks, and actual
  destination containment. CLI extraction permits only its expected layout;
  bootstrap extraction uses a separately explicit policy.
- Extract only into fresh host-owned staging; preserve intended modes; traversal,
  absolute/cross-prefix links, devices, unsupported metadata, and oversize input
  must fail without escaping or touching the live install.

#### B06 — One transactional native CLI installer — REVIEW (preparation slice implemented)

- **Depends on:** A02, B05; C02 before live update wiring.
- **Starting files:** `app/src/main/java/com/agy/runtime/CliInstaller.java`,
  `CliInstallPolicy.java`, `CliInstallMetadata.java` in that directory,
  `app/src/main/java/com/agy/ui/MainActivity.java`, service installer/state API;
  dedicated fake-manifest/server/ELF fixtures. Split preparation from live update
  integration explicitly before assigning work that can proceed without C02.
- Explicit user Install/Update; ABI gate before network; strict manifest/version/
  SHA-512/origin/redirect validation; unique staging; streaming bounded IO;
  timeout, cancellation, one job, conservative free-space check, and ELF/spawn
  verification.
- Keep the old CLI until same-filesystem atomic publication succeeds while the
  session is stopped. Persist version/hash/source/schema. Every failure and two
  rapid taps must leave one known runnable install and at most one job.
- **Implemented 2026-11-13 (preparation slice, committed as `41cdd05`):** `CliInstallPolicy`
  and `CliInstallMetadata` are Android-free policy/codec classes; `CliInstaller`
  replaces `CliDownloader`. ABI gate before network, exact manifest URL,
  approved HTTPS artifact host, redirects refused, bounded manifest and
  declared-size download, streaming SHA-512, conservative free-space preflight,
  unique cache staging, AArch64 plus Android-linker ELF verification, unique
  sibling temp and same-directory atomic rename, strict install record, one job
  at a time, cancellation API, and explicit Install/Update only (launch no
  longer downloads). `triggerDownload` refuses while a published session, a
  stdin owner, or onboarding is live. **Evidence:** B06 host cases in
  `tests/java/HostTestRunner.java`; full suite PASS (38 Python + 37 Java at that
  revision; 41 + 38 after the later F02 cases); `03_compile_java.sh` compiles 78
  classes against `tools/android.jar`; full `tests/run_build_smoke.sh` PASS for
  both flavors (100 classes, compile-only assets).
- **Why still REVIEW:** live update stop/restart orchestration is C02; no device
  install/upgrade run, no real vendor download, no interrupted-download/
  low-space device test, and the ELF/linker check is metadata, not a spawn proof
  or Bionic execution proof.

#### B07 — Remove or authenticate external URL broadcasts — REVIEW (input removed; C01 pending)

- **Depends on:** B02, A02, C01; A04b if a signature-permission fallback remains.
- **Starting files:** `app/src/main/java/com/agy/core/OpenUrlWatcher.java`,
  `app/src/main/java/com/agy/runtime/ShellBridgeInstaller.java`,
  `app/src/main/AndroidManifest.xml`; dedicated foreign-app/flavor/API fixtures.
- Prefer owned output/internal service events and remove public broadcast input.
  If unavoidable, use flavor-specific protection on API 24–32 and NOT_EXPORTED on
  newer APIs, then apply URL policy and active-generation checks.
- A foreign app, other flavor, stale event, package-targeted intent, or public
  debug key must not drive URL/auth/state/input behavior.

### C — Runtime state, processes, output, power, and PTY

#### C01 — Service-owned immutable runtime state — TODO

- **Depends on:** B01, B02.
- **Starting files:** `app/src/main/java/com/agy/core/CoreRuntimeState.java`,
  `CoreServerService.java`, `CoreProcessSupervisor.java`, `OpenUrlWatcher.java` in
  that directory and `app/src/main/java/com/agy/ui/MainActivity.java`. Split the
  shared state interface and migrations into named slices; reserve one state/UI
  writer. F01/B04b/C02 consumers need the accepted interface, not a guess.
- Replace activity/static authorities with independent engine and remote states,
  active generation, workspace, installer state, endpoint, and structured error.
  Start/Stop/Retry are idempotent; activity recreation only observes.
- Remove unconditional auto-start/sticky behavior. URL/cookie/token/intent cannot
  set RUNNING/authenticated. Local engine remains usable without Remote Control.
- **Specific regression:** after supervisor failure the service object can remain
  alive while `onStartCommand()` does no restart; the Activity's RETRY label can
  still use a true `serviceRunning` boolean and merely stop. Test retry from a
  terminal failure and recreated UI against actual service state, not button text.

#### C02 — Owned process lifetime and serialized input — TODO

- **Depends on:** C01, C06.
- **Starting files:** `app/src/main/java/com/agy/core/CoreProcessSupervisor.java`,
  `CoreLauncher.java`, `OpenUrlWatcher.java` in that directory, process utilities;
  dedicated controlled-spawn/stop/input fixtures. Preserve C06 compatibility tests.
- Recheck cancellation after process start; clear every process/input reference on
  exit/failure; one bounded generation-aware input owner; graceful group stop then
  bounded escalation; join/drain workers with real deadlines.
- No global `/proc` substring killing and no prompt/package replay after crash.
  Race fixtures must leave no child, callback, descriptor, or wrong-session kill.

#### C03 — Chunked terminal output and bounded parsing — TODO

- **Depends on:** C01, C02.
- **Starting files:** `app/src/main/java/com/agy/core/CoreProcessSupervisor.java`,
  `OpenUrlWatcher.java`, `CoreLogWriter.java` in that directory; actual runtime
  redactor `app/src/main/java/com/agy/util/SensitiveDataRedactor.java`.
- Carry raw byte chunks to terminal; incrementally decode only for redacted
  diagnostics/events. Handle no-LF prompts, CR, split UTF-8/ANSI/OSC/URLs, and
  bound every parser buffer.
- One log writer owner; raw terminal bytes are not mutated by redaction; arbitrary
  child text cannot send input or prove auth.

#### C04 — Bounded background operation and diagnostics — TODO

- **Children:** C04a diagnostics and C04b background leases; both must be accepted.
- On-demand default; accurate notification with Stop/Open; explicit timed/renewed
  keep-running policy; release locks on stop/error/auth wait. Respect Doze limits.
- Rotate/redact logs, bound entry and aggregate memory, update UI with deltas off
  disk/main thread, pause hidden refresh, and preserve user scroll position.

##### C04a — Bounded logs and off-main-thread dashboard updates — TODO

- **Depends on:** C03 (which depends on C01/C02).
- **Starting files:** `app/src/main/java/com/agy/core/CoreLogWriter.java`,
  `app/src/main/java/com/agy/log/LogRingBuffer.java`,
  `app/src/main/java/com/agy/ui/MainActivity.java`; dedicated large-output/rotation
  fixtures and Android responsiveness checks.
- Bound line/parser size and aggregate bytes, rotate files, use deltas or bounded
  tails off the UI thread, pause hidden polling, preserve user scroll. Test the
  production redactor rather than only the separate `LogRedactor` smoke helper.
- **Acceptance:** repeated/large/no-LF output cannot create unbounded memory/files
  or UI whole-log scans; callbacks after recreation/stop are ignored; redacted
  diagnostics and raw terminal bytes stay separate. Named resource/device evidence.

##### C04b — On-demand background leases and stoppable notification — TODO

- **Depends on:** C01, C02. Does not require C04a log rotation to begin.
- **Starting files:** `app/src/main/java/com/agy/core/WakeLockHolder.java`,
  `ForegroundNotification.java`, `CoreServerService.java` in that directory and
  deliberate background opt-in UI. Coordinate service/UI ownership with C04a.
- **Acceptance:** no default auto-run/indefinite lock; explicit bounded lease,
  accurate Open/Stop notification, release on stop/failure/auth wait, no stale
  renewal after generation changes; measured screen-off/Doze/device behavior.

#### C05 — Correct PTY helper I/O, signals, EOF, and resize contract — TODO

- **Depends on:** A03, C02.
- **Writes:** `modules/core/pty/agy-pty.c`, dedicated native fake-child tests;
  build interface changes by agreement with A03, not a tracked-binary rewrite.
- Replace 100 ms polling with blocking readiness plus child/signal wakeup. Check
  session/ioctl/dup/signal/close results; handle EINTR, partial IO, EIO, EOF,
  SIGPIPE, child status, bounded drain, and owned-group shutdown.
- Initial geometry is configurable; runtime resize belongs to E01/E02. Native
  fake-child tests cover no-output exit, large output, EOF, signal, and Stop.

#### C06 — API-24-compatible, exception-safe process cleanup — REVIEW

- **Depends on:** A01. **Bug:** R24; fifth initial worker packet.
- **Writes:** `app/src/main/java/com/agy/core/CoreProcessSupervisor.java`,
  `CoreServerService.java` in that directory, `app/src/main/java/com/agy/util/ProcessUtils.java`
  as needed; dedicated C06 tests/runner. Do not redesign generations or global
  state here; C02 owns that follow-up.
- Guard/replace API-26 process methods on API 24/25 and make cleanup release
  callbacks/locks/streams even if a teardown operation fails. Preserve min API 24;
  do not “fix” compatibility by raising it. Do not replace force-stop with broader
  `/proc` matching. The unused timed-wait helper must not remain an API-24 trap.
- **Acceptance:** offline policy/fake-process tests reproduce the unsupported
  method path and failure cleanup; Stop, startup timeout, child exit, and repeated
  stop on API 24/25 and API 26+ have named device evidence, no linkage crash or
  leaked ownership. Until these runtime checks exist, leave required acceptance
  REVIEW rather than claiming host compilation proves Android compatibility.

#### C07 — Safe concurrent atomic text/configuration writes — REVIEW

- **Depends on:** A01. **Bug:** R25. Independent offline fix, not blocked on tools.
- **Writes:** `app/src/main/java/com/agy/util/AtomicFileWriter.java`; dedicated
  barrier/failure-injection tests. Allocate affected caller error handling only
  if the API changes; `NetworkConfigWriter`/`OnboardingSeeder` are shared callers.
- Use writer-owned unique sibling temporaries, checked publication, safe cleanup,
  and explicit failure reporting. Never delete a good target because rename failed
  or fall back to an unsafe in-place write while calling it atomic. Keep UTF-8,
  required permissions, and the existing durability intent.
- **Acceptance:** deterministic concurrent writes leave exactly one complete
  payload, never partial/missing; injected write/sync/rename failures preserve the
  old file; no worker deletes another's temporary. Add byte-write fallback tests
  if the common writer API is changed. No device/vendor account needed for pure
  filesystem concurrency tests; Android caller integration is separately stated.
- **Conflict:** B01/C08 may use/change the utility or its callers. Define one
  publication/error contract before allowing dependent caller edits.

#### C08 — Validated, updateable APK-owned runtime assets — TODO

- **Children:** C08a helper, C08b certificates, C08c executable shims. This is
  distinct from D02 package bootstrap and B06 downloaded CLI installation.
- Allocate one publication utility/interface writer if the children share code.
  Concurrent child edits to `ShellBridgeInstaller` are a file conflict, not an
  excuse to invent a dependency on the package ecosystem.

##### C08a — Refresh the installed PTY helper safely — REVIEW

- **Integrated prerequisites:** A03, C02. Offline identity/staging fixtures can be
  prepared earlier as a named test-only scope; they do not complete this task.
- **Bug:** R26. **Starting files:**
  `app/src/main/java/com/agy/runtime/ShellBridgeInstaller.java`,
  `RuntimeManager.java` in that directory, a small asset-publication utility.
- Compare content/version identity with the current APK asset, validate staged
  ELF, and publish atomically under owned-session/update rules. Length/executable
  bits are not installation identity. Keep a valid old helper on failure.
- **Acceptance:** asset A→B APK upgrade installs B, unchanged asset avoids rewrite,
  wrong ELF/interrupted write/cancel preserves A, active helper replacement follows
  the agreed stop/restart contract. Device upgrade tests required for final closure.

##### C08b — Repair and refresh the bundled CA file atomically — REVIEW

- **Depends on:** A01 for the bundled-asset repair, not D01/D02/P2.
- **Bug:** R27. **Starting files:**
  `app/src/main/java/com/agy/runtime/CoreInstaller.java`, small asset-publication
  utility; reserve its interface against C07/C08a/C08c.
- Validate complete bundled content/identity, stage and atomically publish, and
  surface failure. Preserve the previous valid bundle. Do not treat a partial
  file larger than 1,000 bytes as installed or trust arbitrary downloaded CAs.
- **Acceptance:** injected interruption after several KiB is repaired on retry;
  changed bundle replaces old bytes; failed update preserves old valid bytes;
  exact-content fixture checks use no live network. Android startup/native-tool
  TLS verification is named separately; full installed-tool TLS remains D03.

##### C08c — Publish complete executable shims, not torn live files — REVIEW

- **Depends on:** A01 for atomic-publication behavior. If delayed work is retained,
  its lifecycle integration also requires C02; remove redundant work rather than
  inventing a whole environment install that does not exist.
- **Bug:** R28. **Starting files:**
  `app/src/main/java/com/agy/runtime/ShellBridgeInstaller.java`,
  `RuntimeManager.java` in that directory, `app/src/main/java/com/agy/core/CoreLauncher.java`.
- Stage complete executable siblings, set/check modes before publication, skip
  unchanged scripts, and replace atomically. Serialize with B07's broadcast changes.
- **Acceptance:** fake opener invocation during repeated refresh sees complete
  old/new scripts and correct quoted URL markers; failed write preserves the old
  executable; identical warm starts do not truncate/rewrite; cancelled startup
  leaves no stale delayed mutation. Android executable-mode behavior needs device
  evidence; host scripts alone do not prove Bionic/app-internal execution.

### D — App-specific native tools and packages

#### D01 — Produce real native tool artifacts — BLOCKED (P2, P3)

- **Depends on:** A03 and approved package/repository decisions.
- **Starting area:** approved package build recipes/artifact schemas under
  `modules/`; allocate paths only after source/prefix/key choices. Existing
  `modules/core/manifest/` helpers are evidence to inspect, not a signed package
  repository or permission to reuse incompatible artifacts.
- Reproducibly build separate matching release/dev prefixes (unless relocation is
  proven) with Bash, terminfo, core tools, curl/CA, Git, ripgrep, package manager,
  libraries/keyring; add Python/Node/compiler profiles only after closure tests.
- Generate signed bounded bootstrap/repository metadata, provenance, licenses,
  hashes, sizes, architecture/API/page-size support, and inventories. No Termux
  prefix paths or copied phone `usr` may remain.

#### D02 — Verified first-install bootstrap and explicit repair — BLOCKED (D01)

- **Depends on:** D01, B05, B06 coordination.
- **Starting files:** `app/src/main/java/com/agy/install/EnvPaths.java`, runtime
  installer/state interfaces and approved manifest schemas. Do not invent missing
  artifact paths as though they are installed native tools.
- Validate trusted manifest/signature, app ID, prefix, ABI/API/page size, inventory,
  limits, and storage before install. Stage with no active prefix users, publish
  transactionally, test at the real final prefix, and roll back on smoke failure.
- Normal launch never resets packages. Repair/upgrade preserves package databases,
  projects, `.gemini`, and user state; cancellation removes only owned staging.

#### D03 — One environment builder and native runtime doctor — BLOCKED (D02)

- **Depends on:** D02, F01.
- **Starting files:** `app/src/main/java/com/agy/core/CoreEnvironment.java`,
  `app/src/main/java/com/agy/runtime/ShellBridgeInstaller.java`, `RuntimeManager.java`
  in that directory, network helpers and `modules/env/` reference consumers.
  A separately named recovery-shell/policy subtask can remove misleading behavior
  early; full native-tool proof cannot be completed with a system-shell fixture.
- Implement the `AGENTS.md` environment contract for CLI/terminal/packages/tests;
  remove fake Bash, desktop/DNS assumptions, broad preload tricks, and dependence
  on Termux's prefix.
- Doctor safely reports version/ABI/prefix/cwd/tool/linker/CA/DNS/permission health
  without secrets or live prompts. Git helpers/TLS/tools must work in both CLI
  and terminal from the selected project.

#### D04 — Real package operations and package UI — BLOCKED (D01–D03)

- **Depends on:** D01–D03, E02, F01.
- **Starting area:** new argument-array package backend/UI at allocated Java paths,
  consuming D02/D03 interfaces and approved repository metadata. No package-manager
  API is presumed to exist today; fixtures must be labelled as fixtures.
- Argument-array backend for search/show/list/install/remove/update; authenticated
  repository/architecture/prefix checks; visible dependency plan/progress/errors;
  confirmation for removal/large downloads; bounded cancellation behavior.
- Serialize every prefix mutation and respect package-manager recovery/locks. A
  real installed tool must become executable in terminal and CLI; no `trusted=yes`
  or fake-success wrapper.

### E — Real terminal

#### E01 — Qualify a small terminal component — BLOCKED (P4)

- **Depends on:** A03, C02 and owner component/license approval.
- **Starting area:** approved component/revision and a narrow Java/native build
  spike. Reserve new paths after P4; `modules/core/pty/agy-pty.c` is current helper
  evidence, not already a terminal emulator or a reason to nest PTYs.
- Pin exact source/revision and prove manual-build compatibility, JNI/native PTY,
  raw byte IO, resize, exit, process ownership, UI detach, service lifecycle,
  dependencies, packaging/alignment, and notices using `/system/bin/sh` in a dev
  spike. Do not invent an emulator/API or import a whole app blindly.

#### E02 — Managed terminal tabs and interactive CLI setup — BLOCKED (E01)

- **Depends on:** E01, C01–C03; D03 for full Bash mode.
- **Starting area:** approved terminal view/session interfaces plus service-owned
  state/input; `app/src/main/java/com/agy/ui/MainActivity.java` integration is one
  serialized UI lane shared with hosted/workspace tasks.
- Service-owned attachable sessions; expose the actual one CLI session for setup
  and approvals; do not nest `agy-pty` if the selected backend already owns a PTY.
- Tabs, explicit system-shell recovery label, Bash when ready, Ctrl/C/Esc/Tab/
  arrows, resize, bounded scrollback, user-confirmed paste, and per-session close.
  Activity recreation must not duplicate or kill unrelated sessions.

### F — Workspace, files, hosted UX, and local agent UI

#### F01 — App-owned workspace selection and correct cwd — TODO

- **Depends on:** C01, B01.
- **Starting files:** `app/src/main/java/com/agy/core/CoreLauncher.java`,
  `app/src/main/java/com/agy/runtime/RuntimePaths.java`, service state; new
  host-owned registry classes and dedicated path/registry fixtures at assigned paths.
- Stable registry and default `projects/default`; create/select/rename; reject host
  state/prefix/runtime roots, traversal, aliasing, and unapproved symlink escape.
- Set CLI/shell cwd before spawn. Switching workspace is a controlled session
  transition; HOME/auth/history stay stable and vendor records are not fabricated.

#### F02 — Bounded file browser/editor and narrow sharing — REVIEW (provider mitigation implemented; browser/editor gated)

- **Depends on:** F01, E02.
- **Starting files:** `app/src/main/java/com/agy/util/AppFileProvider.java`,
  `UriDispatcher.java`, `PathSafety.java` in that directory, new project-only UI;
  provider grants and project boundary device tests. Inspect actual reachable
  sharing paths before removing legacy code or claiming an exposed vulnerability.
- Browse/create/open/save/rename within selected project; confirm destructive
  actions; bound text preview/edit size; detect binary/unsupported encodings;
  checked atomic saves with visible failure and no original-data loss.
- FileProvider roots/grants must exclude credentials/runtime/prefix/full app data;
  default exact-file read-only shares; deny traversal and symlink escape.
- **Mitigation implemented 2026-11-13 (offline):** `SharePathPolicy` and the
  hardened, read-only `AppFileProvider` expose only regular files below
  `filesDir/share` using relative ids. Absolute app paths, `.gemini`/CLI/prefix/
  log data, traversal, dot/empty segments, control characters, symlink escapes,
  directories and write modes are rejected. The unused raw-path
  `DocumentsContractHelper` and `UriDispatcher` were deleted. **Evidence:** R34
  case in `tests/java/HostTestRunner.java`; manifest-permission regression in
  `tests/python/test_manifest_permissions.py`. **Still gated:** the real file
  browser/editor (F01/E02), SAF import/export (F03) and device grant tests.

#### F03 — Real SAF import/export — TODO

- **Depends on:** F01, F02.
- **Starting files:** `app/src/main/java/com/agy/util/DocumentsContractHelper.java`,
  Activity/result handling, new ContentResolver stream import/export paths; do not
  call raw-path guesses a SAF implementation.
- Use ContentResolver/DocumentsContract streams and grants, never guessed raw
  paths. Bounded/cancellable internal import with collision policy; selected export;
  handle cloud/nonlocal/read-only/revoked providers, spaces, and Unicode.
- Internal projects work with storage permission denied; shared storage remains
  non-executable and a SAF grant is not shell filesystem access.

#### F04 — Honest hosted Remote Control UX — TODO

- **Depends on:** B02, B03, C01, E02, F01.
- **Starting files:** `app/src/main/java/com/agy/ui/MainActivity.java`, service
  remote/workspace state and owned URL events. Shared UI/state writes need a fresh
  assignment after dependent integrations are present.
- Display advertised endpoint, actual observable remote state, owning generation,
  and workspace without calling URL discovery “connected.” Keep local terminal
  usable when hosted service is offline/ineligible/disabled.
- Safe hosted navigation, clearly separate external-browser login/cookies, clear
  stale endpoints on exit/switch, and no desktop service-installer assumptions or
  background Activity launch per parsed URL.

#### F05 — Capability-gated local Studio via documented ACP — BLOCKED (P1, P5)

- **Depends on:** F04, D03, E02, F01 and verified Android ACP support.
- **Starting area:** new bounded protocol client/local UI under assigned Java
  paths, exact verified protocol/version fixtures, owned process/environment APIs.
  No undocumented local bridge/server may substitute for missing ACP proof.
- Implement actual framing/schema negotiation, auth-required/session/prompt/
  stream/tool/diff/question/approval/cancel/exit with request IDs, bounded queues,
  supervised cwd/environment, stale-message rejection, and capability honesty.
- Never auto-approve or expose a second unrestricted shell. Keep local assets and
  narrow authenticated bridge isolated from hosted WebView. Verify whether ACP
  and Remote Control can coexist before allowing concurrent workspace editing.

### G — Truthful release gates

#### G01 — Reconcile all user-facing claims and legacy components — REVIEW (first truthful-text pass done; final pass after M1)

- **Depends on:** update after every task; final pass after M1.
- **Starting files:** `app/src/main/java/com/agy/ui/MainActivity.java`,
  `.github/workflows/build-and-release.yml`, builder help/config and legacy
  modules. Audit can be read-only in parallel; assign each actual edit to its lane.
- Audit in-app text, CI release body, metadata, comments, flags, and active versus
  legacy Python/loopback components. State unofficial host status, real sizes,
  versions, tested devices, hosted/local distinction, approval policy, and missing
  features. Remove or accurately classify unused flags/components only after
  reference/test inspection.
- Keep the two-document policy; update this plan instead of recreating module docs.
- **First pass 2026-11-13:** the UI subtitle now says it is an unofficial host
  rather than claiming official status; the dashboard says the CLI is running
  with a Remote Control session available instead of "connected", and no longer
  presents the heuristic CLI-output email as authenticated; dead
  authentication-success callbacks/toasts were removed. The unreachable release
  body now states unofficial status, development-only scope and unmeasured
  items; workflow and job names are development-only; the unused vendor-input
  download was removed. The final claims/legacy audit stays after M1.

#### G02 — Make publication depend on meaningful evidence — BLOCKED

- **Children:** G02a can stop unsafe publication now; G02b requires A01–A05,
  G02a, applicable B–F fixes (including added build/runtime regressions), P1–P4,
  and owner release approval. A04 includes A04a/A04b.
- CI runs host tests, both flavors, pinned native/toolchain/bootstrap checks, and
  artifact/flavor/version/certificate validation before publication with least
  permissions. Release attaches hashes, certificate digest, provenance, runtime
  requirements, limitations, and device-test record—never secrets/home backups.
- Wrong test/ELF/flavor/version/certificate blocks publication. Ubuntu/Termux
  compilation is never presented as standalone Android proof.

##### G02a — Prevent unattended public release — DONE (offline policy)

- **Depends on:** A01; no legal/key decision is needed to refuse publication.
- **Writes:** `.github/workflows/build-and-release.yml`, dedicated workflow-policy
  checks. Reserve against A03/A04/G01. Do not trigger a live publication to test it.
- **Acceptance:** push/tag/manual triggers cannot publish without explicitly
  approved conditions; ordinary build/test artifacts remain clearly development
  artifacts; least permissions; no `continue-on-error` bypass for security tests.
  Offline fixtures prove the publication guard. This does not resolve P1/P3.

##### G02b — Approved evidence-gated release pipeline — BLOCKED (release gates)

- **Depends on:** parent G02 prerequisites and G02a. Owner must explicitly authorize
  actual release signing/publication after all mandatory gates are satisfied.
- **Writes:** the workflow and artifact-verification interfaces at assigned paths.
- **Acceptance:** host tests, both flavors, runtime-inclusive artifact inventory,
  native/toolchain/bootstrap provenance, cert/version/flavor checks, required
  device/migration/performance/legal evidence. Tampered/failed fixture checks must
  block publication; no user home/credentials in uploaded artifacts.

#### G03 — Coordination-ready instructions and work board — DONE

- **Scope:** owner-requested documentation rewrite only: `AGENTS.md`, `PLAN.md`.
- Preserve the product contract, existing IDs, unfinished defects, acceptance
  matrices, and legal/artifact gates. Add owner prompts, role/ownership rules,
  isolated-worker allocation, dependency reasons, new evidenced findings, and
  automatic honest progress handoff.
- **Acceptance:** structural/reference checks pass, product constraints are not
  weakened, read-only review finds no contradictory ownership/dependency/status
  rules, baseline test results are recorded accurately. No device test is required
  to accept a documentation workflow, and no production bug closes with G03.
- **Evidence:** section 9 records structural checks, unchanged baseline test
  results, and the independent read-only workflow review. Documentation acceptance
  only: all R01–R32 remain open and no production task was implemented here.

## 8. Test and acceptance matrix

These are required acceptance cases, **not claims that the tests already exist**.
Every worker names its exact standalone command; the coordinator registers new
checks serially into the offline entry point and reruns them on combined code.
Pure policy checks do not need a device, but device/API/platform acceptance does.

### 8.1 Offline tests

Use deterministic fixtures and fake subprocesses; no credentials, paid prompt,
package mirror, existing build output, or live vendor service.

| Area | Required cases |
|---|---|
| URL/web | Official encoded URL; HTTP; arbitrary/suffix/userinfo/port/control/path attacks; auth path containing `/r/`; exact navigation/permission origins. |
| Config/build | Release/dev; min/target propagation; invalid fields; namespace vs app ID; D8 min API; missing tools; no source-tree native output. |
| Runner/package inputs | Contradictory/empty test selection; real case counts; secondary DEX; missing runtime assets; explicit compile-only mode; asset inventory/hashes; truthful compression options. |
| ZIP/tar | Stored/deflated/descriptors/extras/Unicode/truncation/unsupported formats; CRC/payload equivalence; tar checksum/limits/traversal/symlinks/conflicts. |
| Installers | Manifest/hash/origin/redirect/ABI/ELF/space/cancel/concurrency/atomic publish/rollback/stale callback. |
| Lifecycle/input | Stop/start/auth/crash/stream races; stale generation; bounds/order; independent sessions; no replay. |
| Compatibility/host assets | API 24/25 process teardown and API 26+; helper A→B upgrade; interrupted/changed CA bundle; shim execution during refresh; no stale delayed mutation. |
| Atomic writes | Barrier-controlled concurrent writes; unique temporaries; write/sync/rename failures; no deletion/in-place corruption of a valid target; complete UTF-8 payloads. |
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
15. API 24/25 Stop/timeout/child-exit cleanup, API 26+ comparison, and APK upgrade
    delivering the changed helper/certificates without losing valid old assets.

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

### 9.1 Receipt and automatic plan maintenance

The **single agent or coordinator updates this plan itself**. The owner does not
need to say “edit PLAN.md now” after every fix. Parallel workers supply this
receipt to the coordinator; they do not edit the canonical board. Keep all IDs
and record partial acceptance honestly.

```text
Task/subtask:
Worker/role:
Base revision + current instruction snapshot:
Result revision or patch identity (no automatic commit required):
Files changed:
Behavior fixed:
Regression tests added or retained:
Commands actually run and exact result:
Remaining baseline failures by ID / new failures:
Device tests performed (or NOT performed):
User data/signing/account/network changes:
Review/integration state:
Known limitation or blocker:
PLAN.md status change:
Recommended next task:
Exact next prompt for the owner:
```

Do not mark a parent task DONE when only a subtask passed. Do not mark a blocked
artifact/protocol/legal task DONE using a mock.

**After integration:** update the card/assignment, append a concise evidence row
below, update the relevant finding to FIXED only when its whole scope is accepted,
and recalculate which dependent tasks are now executable. Remove “known failure”
claims when the regressions actually pass, but keep the tests and old evidence.
If a worker implemented a patch but device proof is missing, record REVIEW plus
the exact missing check, not DONE and not “nothing accomplished.”

### 9.2 Accepted work and evidence ledger

| Task / date | Scope and evidence | Status / remaining limits |
|---|---|---|
| A01 / 2026-11-11 baseline | Offline entry point exists; review ran 14 Python + 14 Java checks. Compile/package smoke passed both flavors. | DONE for harness only; seven known assertions fail, no device proof. |
| G03 / 2026-11-11 | Uncommitted documentation patch on `d728f94`, only `AGENTS.md`/`PLAN.md`. Inline Python validation PASS: original product sections 2–5 preserved verbatim, all old task IDs retained, 51 unique cards, all task references resolve, R01–R32 each registered once, P1–P6 retained, balanced fences, only two Markdown paths changed. `git diff --check` PASS. Independent read-only workflow review PASS (no blocking scope/dependency/ownership contradictions). | DONE for documentation only; all production bugs remain open. No commit, release, signing, account/user-data change, or device test. |
| Single-agent pass / 2026-11-13 (`33e2bd7`) | Committed and pushed to `main`. Build: A02, A05–A09, A04a guard. Runtime: B01a/B01b consent removal, B02 URL policy, B04a unsolicited-auth removal, C06 API-24 cleanup, C07 atomic writer, C08a–c asset publication, G02a workflow guard. Second slice in the same commit: B03 WebView exact-origin/permission policy, B05/R09 pure tar policy + extractor wiring, B07 public-broadcast removal. Checks actually run: `bash tests/run_host_tests.sh` → PASS, 33 Python + 29 Java cases; `bash tests/run_build_smoke.sh` → PASS both flavors (98 classes, D8, package, system zipalign); `bash -n` every edited shell script → clean; `08_sign.sh` release guard → exit 3 without invoking the signer. Workspace-local JDK 17 and build-tools r34 were used only for checks and removed. | DONE (offline): A02, A04a, A05, A06, A07, A08, A09, B02, G02a. REVIEW (device/real-archive/integration evidence missing): A03 (not started), B01a, B01b, B03, B04a, B05, B07, C06, C07, C08a–c. PARTIAL bugs: R05, R07, R09, R18, R20, R23, R24–R28. Not performed: device/API 24–25 tests, APK install/upgrade, real signing, vendor login, real tar extraction, WebView device matrix, lifecycle (C01/C02), or any publication. |
| Review/hardening pass / 2026-11-13 (`0f9250b`) | Exact-host HTTPS auth policy; per-flavor `/proc` cleanup scope; URL watcher no longer retains the Process or writes stdin; non-browser WebView schemes blocked; cleartext disabled outside loopback; write failures surfaced; archive comments preserved; NDK fail-closed helper staging with ELF/16 KB checks; tracked stale helper removed; CI builds only the development edition and publication stays refused. Added `ProcessScope`, `test_stage_assets.py`, align/package/host cases. Checks: host suite PASS 38 Python + 30 Java; build smoke PASS both flavors; `bash -n` clean; workflow YAML parses. | DONE (offline): A03 staging policy, A06, B03/B05/B07 hardening. PARTIAL: R01/R05/R17/R23 pending device/NDK/CI evidence. No device run, real NDK compile, signing, network, vendor login, or publication. |
| B06 preparation slice / 2026-11-13 (`41cdd05`, pushed to `main`) | Replaced `CliDownloader` with `CliInstallPolicy` + `CliInstallMetadata` + `CliInstaller`: explicit Install/Update only, ABI gate before network, exact manifest URL, approved artifact host, redirects refused, bounded manifest/download, streaming SHA-512, free-space preflight, unique staging, AArch64+linker ELF check, atomic publish, strict install record, one job, cancellation. `triggerDownload` refuses while a published session, stdin owner, or onboarding is live; the dashboard shows the installed version. Checks: `bash tests/run_host_tests.sh` → 38 Python + 37 Java PASS, exit 0; `01_gen_config.py` + `03_compile_java.sh` → 78 classes, exit 0; `bash tests/run_build_smoke.sh` → PASS both flavors, 100 classes, exit 0 (compile-only assets, no native/runtime proof). | REVIEW: live stop/restart integration is C02; no device install/upgrade, real vendor download, or interrupted/low-space device run. ELF check is metadata, not spawn/Bionic proof. No APK install, signing, or publication. |

| A03/A08 native evidence / 2026-11-13 (`9719035`, pushed to `main`) | Built the PTY helper with NDK `27.0.12077973`; staging verified ELF64/AArch64/linker64/16 KB; full pipeline compiled 88 classes, D8 produced one DEX, and non-compile-only packaging produced a 359,681-byte APK containing 1 DEX and the 3 required runtime assets. | PARTIAL: A03 still needs CI provenance and device execution; A08 stays DONE offline with its native gate now locally exercised. No signing, install or publication. |
| F02/G01 safety + truthfulness pass / 2026-11-13 (`9719035`, pushed to `main`) | Share: `SharePathPolicy` + read-only `AppFileProvider` limited to `filesDir/share`, relative ids, no write modes, traversal/dot/empty/control/symlink rejection; deleted unused raw-path `DocumentsContractHelper` and `UriDispatcher`. Permissions: removed all-files, legacy shared-storage, notification and battery-exemption permissions, `requestLegacyExternalStorage` and the launch prompt. Claims: honest UI subtitle/status, suppressed heuristic email, removed dead auth-success callbacks, rewrote the release body, development-only workflow/job names, removed the dead vendor-input download. Checks: `bash tests/run_host_tests.sh` → 41 Python + 38 Java PASS, exit 0; `03_compile_java.sh` → 77 classes, exit 0; `bash tests/run_build_smoke.sh` → PASS both flavors, exit 0; workflow YAML parses; `git diff --check` clean. | PARTIAL: R34/F02 offline mitigation only (file browser/editor and device grant tests pending); R35 fixed offline; G01 first pass only. No device run, signing, download or publication. |

The 2026-11-13 passes left the suite green: `bash tests/run_host_tests.sh` → 41
Python + 38 Java cases, exit 0. `bash tests/run_build_smoke.sh` → exit 0, both
flavors compile/dex/package/align (still compile-only assets and the system ZIP
aligner, not native/runtime proof). A06 was verified by direct invocation:
`--python-only --java-only` → exit 2, unknown arg → exit 2, `--help` → exit 0,
single-suite runs report real case counts. No persistent non-recursive runner
test was added (a test that invokes the runner would recurse into its own
discovery), so A06 remains REVIEW. No signing, installation, network or device
operation was performed.

**Next task:** device acceptance for B01a/B01b/B04a and C06–C08 on API 24/25 and
API 26+, then B05's real-archive extraction, B03's WebView origin/permission
matrix, B06's device install/update, and F02's share-provider grant tests. B06's
live replacement of a running CLI is C02 after C01; G01's final claims audit is
after M1. No worker is assigned; see the ledger and section 3.2.

Future rows must name actual commands, results, accepted revision/patch identity,
reviewer (if any), and missing device/artifact evidence. Do not paste entire logs
or secrets. Uncommitted accepted patches need an unambiguous diff/snapshot identity
and must be present in the canonical working copy before dependents rely on them.

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
- Android process APIs: <https://developer.android.com/reference/java/lang/Process#destroyForcibly()>;
  reference checked during the 2026-11-11 review: `destroyForcibly()` and timed
  `waitFor(long, TimeUnit)` added in API 26, not a device-runtime test.
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

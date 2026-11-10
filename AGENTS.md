# Agy-cli-app Product Constitution

> This is the permanent contract for the project. Every AI or human contributor
> must read it before making a plan or changing code. `PLAN.md` records the
> current route and progress; this file defines the destination and the rules
> that must not be traded away for an easier implementation.

## 1. Authority and sources of truth

When information conflicts, use this order:

1. The owner's latest explicit instruction.
2. The non-negotiable product contract in this file.
3. Approved decisions and task status in `PLAN.md`.
4. Current code, tests, and measured device behavior for implementation facts.
5. Versioned upstream documentation for Android, Antigravity, ACP, and reused
   open-source components.

Old release text, screenshots, commit messages, generated agent handoffs, and a
green build are not proof that a feature works. Never preserve a false claim
merely because it appeared in an earlier release.

Only the owner may weaken or replace the product contract. If a requested task
would conflict with it, explain the conflict and propose a compliant route
instead of silently changing the goal.

## 2. North-star outcome

Build **one sideloadable Android application** that provides an app-contained,
Android-native coding workspace powered by the official native Antigravity CLI.
The installed app should provide:

- An integrated agent experience: chat, streamed work, tool activity, questions,
  approvals, cancellation, and change review.
- A real interactive terminal with Bash, terminal keys, resizing, selection,
  paste, bounded scrollback, and multiple managed sessions.
- An app-owned project filesystem plus file browser, text editor, safe import,
  and safe export.
- Android/Bionic-compatible developer tools such as Git, curl, ripgrep, Python,
  Node.js, and optional compiler profiles when those packages are proven.
- A real package manager backed by a matching, authenticated package repository.
- The official hosted Remote Control experience where supported, without making
  local terminal/files functionality depend on it.
- A local in-app agent interface through a documented protocol such as ACP only
  after the Android CLI and protocol behavior are verified.

The user and the agent must operate on the **same selected project**, with the
same installed tools and authoritative process environment.

Normal operation must not require a separately installed Termux app. Building
or debugging the project from Termux is allowed; depending on Termux at runtime
is not.

In one sentence:

> A native Android development environment with Antigravity as its agent engine,
> not a desktop Linux distribution hidden inside Android.

## 3. Definition of complete

The ambition is achieved only when all of the following are demonstrated on a
fresh supported device or test profile:

1. One APK installs and initializes without borrowing another app's private
   files, binaries, libraries, or package prefix.
2. The app selects an Android artifact from the device ABI, verifies it, and
   runs it with Android's native linker. It never falls back to a generic Linux
   build.
3. A new user can read and complete CLI setup, authentication, terms, workspace
   trust, and permission prompts interactively. The app does not answer for the
   user.
4. The terminal is a real PTY terminal, Bash is genuine Bash, and Ctrl-C,
   resizing, EOF, process exit, and reopening behave correctly.
5. CLI, terminal, package jobs, and diagnostics share the intended environment
   and selected working directory.
6. A signed bootstrap installs real native tools into this app's prefix, and a
   matching authenticated repository can install, update, and remove packages.
7. The user and agent can both edit the selected internal project. Import and
   export work through Android document APIs without pretending a `content://`
   URI is a raw filesystem path.
8. Hosted Remote Control is described and isolated as a Google-hosted service.
   Any local Studio uses a verified documented integration and a separate trust
   boundary.
9. Stop, cancel, update, process failure, activity recreation, low storage,
   network loss, and interrupted installation do not corrupt state or leak
   owned processes, callbacks, wake locks, or unbounded logs.
10. Standalone device tests, security tests, and measured resource checks pass;
    unsupported and untested cases are stated plainly.
11. App signing, update/data migration, third-party licenses, and permission to
    distribute or wrap the vendor CLI/service are resolved before publication.

A mock button, placeholder package command, dashboard log view, URL discovery,
successful compilation, or screenshot does not satisfy an end-to-end criterion.

## 4. Non-goals and honest limits

- No PRoot, chrooted Ubuntu, full Linux distribution, root requirement, or
  `ptrace`-based syscall/CPU translation.
- No promise that arbitrary glibc Linux executables, manylinux wheels, native npm
  prebuilds, Docker, systemd, or desktop browser automation will work.
- No access to another app's private data and no cross-app prefix symlinks.
- No authentication, regional, quota, policy, or permission bypasses.
- No automatic acceptance of terms, workspace trust, tool permissions, or
  destructive actions.
- No claim that a cloud-backed model is offline merely because the UI is local.
- No promise of zero heat, zero battery use, or a tiny complete installation.
  Download size, installed size, RAM, CPU, and battery are separate measurements.
- No claim of Google endorsement or “official app” status without explicit
  authorization. The CLI can be official while this host remains unofficial.

## 5. Permanent architecture contracts

### 5.1 Execution platform

- Initial supported runtime: Android ARM64/Bionic, minimum API 24.
- Map `arm64-v8a` to the vendor's `android_arm64` artifact and verify AArch64
  ELF metadata plus `/system/bin/linker64` before publication.
- Use Android ABI APIs such as `Build.SUPPORTED_ABIS`; never use `uname` to pick
  a Linux/Android download.
- Fail clearly on unsupported devices. Do not silently fetch glibc or musl.
- Release and development application IDs are `com.agy` and `com.agy.dev` unless
  the owner approves a migration. Their private prefixes and package artifacts
  must remain isolated.
- Target SDK 28 is an explicit initial strategy for downloaded native execution,
  not an incidental constant. Do not silently raise it. A modern-target design
  requires a separately tested APK-installed native-code/update strategy.
- Sideloading does not bypass SELinux, Android background limits, private app
  isolation, shared-storage `noexec`, or device installation policy.

### 5.2 Filesystem ownership

Resolve real locations through Android `Context`; do not assume a hardcoded
`/data/user/0/...` path.

```text
filesDir/                         stable HOME; preserve user/vendor state
├── cli/                         native Antigravity CLI + install metadata
├── usr/                         this app's native package prefix
│   ├── bin/ lib/ libexec/ share/ etc/
│   └── var/                     package databases and mutable package state
├── runtime/                     host-owned helpers, URL opener, certificates
├── projects/<workspace-id>/     normal internal project workspaces
├── workspaces.json              host-owned workspace registry
├── .gemini/                     vendor-owned auth/history/configuration
└── logs/                        bounded and redacted diagnostics

cacheDir/                        downloads, imports, and installer staging only
```

- HOME, `.gemini`, `cli`, `usr`, `runtime`, and `logs` are never the default
  project workspace.
- Set every managed process's working directory to the selected project.
- The host owns its workspace registry; do not invent or overwrite undocumented
  vendor project/history records.
- Keep executable tools and package prefixes on permitted app-internal storage.
  Use Storage Access Framework streams for shared/cloud documents.
- Preserve credentials, projects, CLI history, installed packages, and package
  databases across normal starts and upgrades.

### 5.3 One authoritative process environment

CLI, terminal, package jobs, and diagnostics must derive from one tested builder:

```text
HOME      = filesDir
PREFIX    = filesDir/usr
TMPDIR    = cacheDir
PATH      = cli : host helpers : PREFIX/bin : Android system PATH
SHELL     = real compatible Bash, or explicitly labelled recovery system shell
TERM      = xterm-256color
TERMINFO  = matching installed terminfo when present
TLS/CA    = verified Android/native-tool certificate configuration
CWD       = selected internal project
```

- Do not discard CLI/helper path entries when launching a tool.
- Do not call `/system/bin/sh` “Bash.”
- Do not apply a broad `LD_PRELOAD`/library path to unrelated processes.
- Prefer Android's native DNS behavior. Do not silently bypass VPN/private DNS
  with public resolvers or fabricate desktop display settings.
- Environment variables cannot relocate binaries with compiled-in prefixes.

### 5.4 UI, web, authentication, and consent boundaries

Keep these surfaces distinct:

1. Google-hosted Remote Control: hosted page ↔ Google service ↔ CLI.
2. App-owned Studio: native/local UI ↔ verified ACP or another documented API.
3. Terminal: native terminal component ↔ PTY ↔ Bash/CLI/tools.

- Never expose a filesystem/shell JavaScript bridge to Google-hosted or arbitrary
  web content.
- Exact origins, schemes, ports, paths, senders, and active session generations
  must be validated. Substring host checks are forbidden.
- Loopback is not an authentication boundary; other apps in the same Android
  profile can reach loopback. Prefer app-internal Binder/service communication.
- Use the vendor-supported browser or genuine Custom Tab authentication flow.
  Do not spoof a desktop user-agent to evade embedded-login restrictions.
- Browser and WebView cookie stores are separate. A URL, cookie name, token file,
  email string, or external “success” intent is not proof of authentication.
- Clipboard reads, code paste, terms acceptance, project trust, and permission
  approvals require a visible user action.
- Repository `AGENTS.md` is contributor guidance. Never seed it into a user's
  project or vendor configuration; user/project instructions are user-owned.

### 5.5 Process lifecycle and resource use

- The service, not an Activity, owns immutable runtime state and process/session
  generations. UI recreation observes state; it does not start duplicate work.
- Track local engine state independently from hosted remote state. An advertised
  endpoint is not the same as an authenticated, connected engine.
- Start, stop, retry, install, update, and input operations are idempotent and
  generation-aware. Stale callbacks cannot affect a new session.
- Gracefully stop only owned sessions, wait with a bound, then escalate. Never
  kill processes by broad `/proc` substring matching.
- Default operation is on demand. Any “Keep running” mode must be explicit,
  visible, stoppable from its notification, and use bounded wake-lock leases.
- No busy polling, whole-log reads on the UI thread, unlimited terminal buffers,
  unlimited log files, or whole-archive/binary reads into memory.

### 5.6 Native bootstrap and packages

A package button is not a package ecosystem. Before claiming package support,
the project needs:

- A pinned and reproducible Bionic package source/revision and maintained patches.
- Artifacts built for the real application ID, fixed prefix, ABI, API, and page
  size; separate release/dev artifacts unless genuine relocation is proven.
- A signed initial bootstrap with Bash, terminfo, essential tools, certificates,
  package manager, libraries, and keyring.
- A matching authenticated repository and dependency closure.
- A signed bounded manifest containing identity, prefix, ABI/API, sizes, hashes,
  inventory, repository identity, provenance, and licenses.
- Transactional staging, validation at the final prefix, rollback, repair, and
  serialization of all prefix-mutating jobs.

Never copy a phone's Termux `usr`, point this app at Termux repositories built for
another prefix, disable repository authentication, use placeholder hashes, or
report a system-shell fallback as a complete native environment.

### 5.7 Supply chain, signing, and legal gates

- Download only from approved HTTPS origins with bounded redirects, sizes,
  timeouts, streaming hashes, cancellation, ABI/ELF checks, and atomic publish.
- Preserve a working CLI/prefix until its replacement passes validation.
- Development keys are not release identity. Release builds require explicit
  owner-controlled credentials, expected certificate verification, and a tested
  update/data migration path. Secrets never enter git, logs, or command text.
- Track exact licenses and source/notice obligations for terminal, package, and
  other reused components.
- Resolve vendor terms and redistribution/integration permission before public
  distribution. Technical compatibility is not legal permission.

## 6. Engineering and evidence rules

- Read current files before editing; line numbers and plans become stale.
- Implement one `PLAN.md` task or explicitly named subtask at a time. Respect its
  dependencies and stop honestly at owner/artifact/protocol gates.
- Prefer a reviewable patch of roughly 3–5 production files. Split broad tasks.
- Preserve the existing Java/manual APK pipeline unless an approved task changes
  it. Do not migrate frameworks merely to make a task easier.
- Put pure policy/parser/state behavior in offline host tests. Put Android API,
  PTY, WebView, lifecycle, and package-prefix behavior in device tests.
- Use fixtures, fake CLI processes, and local servers before live Google services.
- Every bug fix needs a regression test that fails before and passes after.
- “Done” requires the task's acceptance evidence. A compile pass, grep, mock,
  screenshot, or untested assertion is insufficient.
- Never weaken, delete, skip, or rewrite a failing test merely to obtain green.
- Do not overwrite unfamiliar changes, user data, credentials, workspaces, or
  package state. Do not perform destructive git operations, publish, sign a
  release, run paid/live agent prompts, or commit automatically without explicit
  owner authorization.
- End each implementation with changed paths, commands actually run and results,
  device tests performed or not performed, blockers, and the next task ID.

The owner is ambitious but does not need to be a coding expert. Explain outcomes
in plain language, recommend a safe default, distinguish an owner decision from
an engineering detail, and never hide uncertainty behind confident jargon.

## 7. Documentation contract

The repository intentionally has exactly two Markdown documents:

- `AGENTS.md` — enduring goal, constraints, architecture, and contributor rules.
- `PLAN.md` — current findings, decisions, ordered work, status, and evidence.

Do not add README, release-note, module, test, handoff, or nested AGENTS Markdown
files. Put stable local interface detail in code, schemas, tests, or `--help`
output; put cross-project implementation guidance in `PLAN.md`. Change this file
only when the owner changes an enduring requirement. Update `PLAN.md` when task
status, dependencies, evidence, risks, or approved decisions change.

If public distribution later requires separate user documentation, obtain the
owner's explicit approval to revise this two-document policy rather than allowing
documentation to multiply silently.

## 8. Final invariant

When forced to choose between a quick demo and the stated product, preserve the
product. Report the missing artifact, decision, protocol, test, or legal approval
and leave the task blocked. Never substitute PRoot, fake tools, automatic consent,
unsafe trust, or misleading claims for the native app-contained workspace.

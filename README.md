# IRCafe

IRCafe is a Java 25 desktop chat client with a Swing UI and a Spring Boot backend, supporting IRC, Quassel Core, and Matrix backends.

<figure>
  <img width="50%" height="619" alt="Screenshot_20260301_164811" src="https://github.com/user-attachments/assets/b22feaac-3d33-469f-879b-a2efb22cca20" />
  <figcaption style="font-size: 0.8rem; text-align: center; color: grey;">
      <BR>
      <sub>* Light Theme</sub>
  </figcaption>
</figure>
<P></P>
<figure>
  <img width="50%" height="620" alt="Screenshot_20260301_164951" src="https://github.com/user-attachments/assets/92365818-1839-4471-b3dd-52688925f272" />
  <figcaption style="font-size: 0.8rem; text-align: center; color: grey;">
      <BR>
      <sub>* Dark Theme</sub>
  </figcaption>
</figure>

## Quick start

```bash
./gradlew bootRun
```

This launches the Swing app and loads runtime config from `${XDG_CONFIG_HOME}/ircafe/ircafe.yml` (or `~/.config/ircafe/ircafe.yml`).

## Features

### Core IRC

- Multi-server connections with add/edit/manage server dialogs.
- TLS and SASL (`AUTO`, `PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-1`, `EXTERNAL`, `ECDSA-NIST256P-CHALLENGE`).
- Auto-reconnect with backoff + jitter, plus heartbeat timeout detection.
- Global SOCKS5 proxy plus per-server proxy override (auth, remote DNS, connect/read timeouts, proxy test).
- Auto-join channels or PM targets and per-server perform-on-connect commands.
- Rich slash command support for connection/session control, channel/user ops, invite workflows, monitor, ignore/soft-ignore, CTCP, DCC, CHATHISTORY, filters, and raw IRC.

### Matrix Backend

- Matrix transport backend with homeserver probe + token-authenticated connect flow.
- Room and DM messaging, room joins/leaves, room directory/listing, and sync-based timeline ingestion.
- Matrix-backed compose features mapped into IRCafe flows, including reply/react/edit/redact, typing, read markers, and history navigation.
- Matrix support is active and tested, but full slash-command parity with IRC is still in progress.

### IRCv3 and Bouncer Support

- Capability negotiation with per-capability runtime toggles.
- Broad IRCv3 coverage including `message-tags`, `server-time`, `echo-message`, `standard-replies`, `labeled-response`, `typing`, `read-marker`, `multiline`, `batch`, `chathistory`, `znc.in/playback`, plus compose-related capabilities (`draft/reply`, `draft/react`, `message-edit`, `message-redaction`).
- IRCv3 `sts` policy learning and persistence, with automatic TLS/port upgrades on later connects.
- Message reply/reaction/edit/redaction flows in chat UI and command layer.
- Typing indicators (send + receive) and read-marker updates.
- ZNC and soju network discovery with ephemeral server entries, per-network auto-connect preferences, and optional save of discovered entries to the main server list.

### Chat UX

- Docked chat workspaces with per-buffer state.
- Multiple buffers can be open on screen simultaneously.
- Unread + highlight counters in the server tree.
- Presence folding (join/part/quit/nick condensation).
- Transcript context menu actions (reply, react, redact, inspect, must be enabled on the server).
- Find-in-transcript support, load-older controls, and history context actions.
- Command history, nick completion, and alias expansion.
- Nick context menu actions for query/whois/ctcp, moderation, DCC, and ignore toggles.
- CTCP request routing options and configurable CTCP auto-replies (`VERSION`, `PING`, `TIME`).

### Appearance and Preferences

- Theme system with FlatLaf, DarkLaf, curated IntelliJ themes, optional full IntelliJ pack list, and live preview.
- Accent overrides, chat palette controls (timestamp/system/mention), mention strength, and chat font family/size controls.
- FlatLaf tweak controls for density and corner radius, plus optional global UI font override.
- Preferences tabs for appearance, startup, tray/notifications, chat, CTCP replies, IRCv3, embeds/previews, history/storage, notifications, commands, diagnostics, filters, network, and user lookups.

### Monitor

- Persistent per-server monitor nick list with `/monitor` and `/mon`, plus a dedicated monitor panel.
- Automatic monitor resync on connect; ISON fallback polling when MONITOR is unavailable.

### Filtering and Ignore

- Hard ignore and soft ignore lists (with CTCP behavior toggles).
- WeeChat-style render-time filters with:
  - Scope matching
  - Direction/kind/source/tag/text matching
  - Global defaults and per-scope overrides
- Filter placeholders/hints with collapse behavior and history-batch controls.

### Logging and History

- Optional embedded HSQLDB logging with Flyway-managed schema.
- Remote-only history mode when DB logging is disabled.
- Logging controls for PM logging, soft-ignored line logging, and retention.
- History loading integrates local DB data with IRCv3 CHATHISTORY and ZNC playback remote fill.
- Duplicate suppression in storage paths (message-id and exact-match checks where applicable).
- Per-server Log Viewer node with async search/export (off-EDT), sortable columns, and configurable column visibility.
- Log Viewer filters: nick, message text, hostmask, channel, date range, and protocol/server-event suppression controls.

### Interceptors

- Per-server interceptor system with a dedicated `Interceptors` tree group.
- Multiple interceptor definitions, each with:
  - Server scope (`this server` or `any server`)
  - Channel include/exclude rules (`All`, `None`, `Like`, `Glob`, `Regex`)
  - Trigger rules by event type plus message/nick/hostmask matching
- Action pipeline per interceptor:
  - Status bar notification
  - Desktop toast
  - Sound (built-in or custom file)
  - External script execution
- Captured hits table and per-server hit counts shown in the tree.

### Notifications

- System tray integration (close-to-tray, minimize-to-tray, start minimized).
- Desktop notifications for highlights, private messages, and connection state.
- Notification sound selection (built-in and custom).
- Event-driven notification rules (kicks, bans, invites, modes, joins/parts, PM/notice/CTCP, topic changes, netsplit, etc.).
- Rule actions can route to toast, status bar, sound, notifications node, script, and optional Pushy forwarding.
- Rule cooldown plus toast dedupe/rate-limiting behavior to reduce spam.
- A number of built-in sounds to choose from, or customize with your own.

### Media and Previews

- Optional inline image embedding for direct image links (`png`, `jpg`, `gif`, `webp`).
- Optional animated GIF playback.
- Link previews (OpenGraph/oEmbed + dedicated resolvers for sites such as Wikipedia, YouTube, Slashdot, IMDb, Rotten Tomatoes, X, Instagram, Imgur, GitHub, Reddit, Mastodon, and major news pages).

### DCC

- DCC chat/file workflows (`/dcc ...`, `/dccmsg`) plus a DCC Transfers panel with action hints and quick follow-up actions.

### Utility Panels

- Channel List node for `/list` results with filtering and double-click join.
- User command alias editor with HexChat `commands.conf` import.
- Monitor and Notifications nodes per server for quick operational actions.
- Runtime diagnostics surfaces under `Application` (Unhandled Errors, AssertJ Swing, jHiccup, Inbound Dedup, JFR, Spring, Terminal), including bounded event feeds plus clear/export actions.

## Requirements

- Java 25 for local run/build tasks (`./gradlew bootRun`, `./gradlew test`, `./gradlew jpackage`, `java -jar ...`).
- GNU Make + Docker only if using the optional Makefile Docker shortcuts.
- Flatpak + `flatpak-builder` only if building Flatpak bundles on Linux.

## Project layout

- Entry point: `src/main/java/cafe/woden/ircclient/IrcSwingApp.java`
- `src/main/java/cafe/woden/ircclient/ui`: Swing UI, docking, dialogs, rendering, tray integration.
- `src/main/java/cafe/woden/ircclient/app`: orchestration, mediator flow, command dispatch, notifications.
- `src/main/java/cafe/woden/ircclient/irc`: IRC/Matrix/Quassel protocol integrations and capability handling.
- `src/main/java/cafe/woden/ircclient/config`: runtime config models, property binding, registries.
- `src/main/java/cafe/woden/ircclient/logging`: log persistence, history ingestion, log viewer services.
- `src/main/java/cafe/woden/ircclient/net`: TLS/proxy bootstrapping and lightweight HTTP utilities.
- `src/test/java/cafe/woden/ircclient`: unit + integration + architecture tests.
- `src/functionalTest/java/cafe/woden/ircclient`: Swing functional tests (`*FunctionalTest`).

## Makefile shortcuts (optional)

The repository includes a root `Makefile` with convenience commands that call Gradle directly.

```bash
make help
make build
make test
make check
```

If you prefer a Make-first workflow, these cover most tasks:

| Workflow | Make | Gradle equivalent |
|---|---|---|
| Run app from source | `make bootrun` | `./gradlew bootRun` |
| Build runnable jar | `make jar` | `./gradlew bootJar` |
| Run lint checks | `make lint` | `./gradlew lint` |
| Run unit/non-functional tests | `make test` | `./gradlew test` |
| Run integration tests | `make integration-test` | `./gradlew integrationTest` |
| Run architecture guardrails | `make architecture-test` | `./gradlew architectureTest` |
| Run Swing functional tests | `make functional-test` | `./gradlew functionalTest` |
| Build app image | `make jpackage` | `./gradlew jpackage` |
| Run full verification | `make check` | `./gradlew check` |

Run any Gradle task(s) via:

```bash
make gradle TASKS="bootRun"
make gradle TASKS="integrationTest --tests 'cafe.woden.ircclient.irc.QuasselCoreContainerIntegrationTest'"
```

Build/test in Docker (no local JDK install required):

```bash
make docker-image
make docker-build
make docker-check
make docker-gradle TASKS="test"
# Also available: make docker-lint, make docker-integration-test, make docker-architecture-test, make docker-functional-test
```

Notes:

- Local Make targets default `GRADLE_USER_HOME` to `./.gradle-local` (override with `LOCAL_GRADLE_USER_HOME=/path`).
- Docker shortcuts run `./gradlew` inside a local image built from versioned `Dockerfile.build`.
- `Dockerfile.build` pins `eclipse-temurin:25-jdk` by digest for reproducible toolchain resolution.
- Docker targets auto-build the image if missing (override with `DOCKER_IMAGE=...` if needed).
- A persistent repo-local cache directory (`./.gradle-docker`) is used for Docker Gradle cache reuse between runs.

## Run from source

From the project root:

```bash
./gradlew bootRun
# or:
make bootrun
```

This launches the Swing UI.

## Build a runnable jar

```bash
./gradlew bootJar
# or:
make jar
java -jar build/libs/*.jar
```

## Build an app image (jpackage)

```bash
./gradlew jpackage
# or:
make jpackage
```

Output is written under `build/dist/` using the platform's app-image layout.

On Linux, the generated app image also includes:

- `IRCafe.desktop`
- `install.sh`
- `uninstall.sh`

You can customize the Linux desktop launcher install prefix used in generated desktop entries:

```bash
./gradlew jpackage -PappInstallDir=/opt/IRCafe
```

To install just the desktop entry for the current user on Linux:

```bash
./gradlew installLinuxDesktopEntry
```

## Build a Flatpak bundle (Linux)

Install tooling (example for Debian/Ubuntu):

```bash
sudo apt-get update
sudo apt-get install -y flatpak flatpak-builder
flatpak remote-add --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
```

Build the app image and Flatpak repo/bundle:

```bash
./gradlew jpackage generateFlatpakMetainfo
flatpak-builder --force-clean --repo=build/flatpak/repo --default-branch=stable --install-deps-from=flathub build/flatpak/workdir packaging/flatpak/cafe.woden.IRCafe.yml
flatpak build-bundle build/flatpak/repo build/dist/ircafe.flatpak cafe.woden.IRCafe stable --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo
```

On tagged GitHub releases, CI now publishes `ircafe-<version>-linux-x64.flatpak` as a release asset.

## Developer workflow

Common local checks:

```bash
./gradlew lint
./gradlew test
./gradlew check
```

One-time contributor setup:

```bash
./scripts/setup-git-hooks.sh
```

This enables the repo's `commit-msg` hook for Conventional Commit validation.

Targeted suites:

```bash
# Spring/context integration tests from src/test (classes ending with IntegrationTest)
./gradlew integrationTest

# Modulith/jMolecules/ArchUnit guardrails
./gradlew architectureTest

# Swing UI functional tests from src/functionalTest (classes ending with FunctionalTest)
./gradlew functionalTest
```

Quassel support integration matrix:

- `QuasselCoreRealServerIntegrationTest`: validate against an already-running Quassel Core.
- `QuasselCoreContainerIntegrationTest`: validate containerized Quassel bootstrap/connect/sync.
- `QuasselCoreContainerNetworkE2eIntegrationTest`: validate Quassel + real ircd network flow.
- All Quassel integration tests are opt-in and gated by `quassel.it.*` properties.

Real Quassel Core integration (opt-in):

```bash
# Validates live Quassel Core connect/auth/sync, lag probe heartbeat, disconnect, reconnect.
# Requires a reachable Quassel Core and credentials.
QUASSEL_IT_LOGIN=alice QUASSEL_IT_PASSWORD=secret \
./gradlew integrationTest --tests 'cafe.woden.ircclient.irc.QuasselCoreRealServerIntegrationTest' \
  -Dquassel.it.enabled=true \
  -Dquassel.it.host=127.0.0.1 \
  -Dquassel.it.port=4242 \
  -Dquassel.it.login=alice \
  -Dquassel.it.password=secret
```

Optional knobs (env vars or `-D` properties): `quassel.it.server-id`, `quassel.it.tls`,
`quassel.it.nick`, `quassel.it.real-name`.

Containerized Quassel Core integration via Testcontainers (also opt-in):

```bash
# Starts Quassel Core in Docker and validates setup/connect/sync/heartbeat/reconnect.
./gradlew integrationTest --tests 'cafe.woden.ircclient.irc.QuasselCoreContainerIntegrationTest' \
  -Dquassel.it.container.enabled=true
```

Optional knobs: `quassel.it.container.image`, `quassel.it.container.login`,
`quassel.it.container.password`, `quassel.it.container.startup-timeout-seconds`.
Default image is pinned to `linuxserver/quassel-core:0.14.0` for amd64 compatibility.

Expanded two-container Quassel E2E (Quassel Core + local ngIRCd):

```bash
# Validates Quassel create/connect network flow plus live channel message ingress.
./gradlew integrationTest --tests 'cafe.woden.ircclient.irc.QuasselCoreContainerNetworkE2eIntegrationTest' \
  -Dquassel.it.container.e2e.enabled=true
```

Optional knobs: `quassel.it.container.e2e.quassel-image`,
`quassel.it.container.e2e.irc-image`, `quassel.it.container.e2e.channel`,
`quassel.it.container.e2e.message`, `quassel.it.container.e2e.login`,
`quassel.it.container.e2e.password`.
Note: this test skips when the selected Quassel image does not expose runtime
network creation to remote clients.

Direct IRC backend E2E (Pircbotx + local ngIRCd, no Quassel):

```bash
# Validates direct IRC backend connect/ready/join/inbound+outbound messages/reconnect.
./gradlew integrationTest --tests 'cafe.woden.ircclient.irc.PircbotxContainerNetworkE2eIntegrationTest' \
  -Dirc.it.container.e2e.enabled=true
```

Optional knobs: `irc.it.container.e2e.irc-image`, `irc.it.container.e2e.channel`,
`irc.it.container.e2e.bot-message`, `irc.it.container.e2e.app-message`,
`irc.it.container.e2e.nick`, `irc.it.container.e2e.login`.

Matrix Synapse container integration (opt-in):

```bash
# Starts a local Synapse container and validates probe/whoami/send/sync/history flows.
./gradlew integrationTest --tests 'cafe.woden.ircclient.irc.matrix.MatrixSynapseContainerIntegrationTest' \
  -Dmatrix.it.container.enabled=true
```

Optional knobs (property/env): `matrix.it.container.image` / `MATRIX_IT_CONTAINER_IMAGE`,
`matrix.it.container.server-name` / `MATRIX_IT_CONTAINER_SERVER_NAME`,
`matrix.it.container.http-port` / `MATRIX_IT_CONTAINER_HTTP_PORT`,
`matrix.it.container.startup-timeout-seconds` / `MATRIX_IT_CONTAINER_STARTUP_TIMEOUT_SECONDS`,
`matrix.it.container.registration-secret` / `MATRIX_IT_CONTAINER_REGISTRATION_SECRET`.

When to run which tests:

- Spring Modulith/jMolecules/ArchUnit or module-boundary refactors: `./gradlew architectureTest test`
- Spring wiring/integration changes (new beans, events, transactional boundaries): `./gradlew integrationTest`
- Swing UI behavior changes (dialogs, tree interactions, panel flows, rendering): `./gradlew functionalTest test`
- Non-UI feature work: `./gradlew test`
- Pre-merge on larger changes: `./gradlew check` plus relevant targeted suites above

Auto-fix workflow (Error Prone auto-corrects first, then Spotless formatting):

```bash
./gradlew spotlessApply
# Run Error Prone auto-corrects, then Spotless lint check
./gradlew spotlessLint
# Optional: override which Error Prone patch checks are applied
./gradlew spotlessApply -PerrorPronePatchChecks=CheckReturnValue,ReturnValueIgnored
```

Additional verification/reporting tasks:

```bash
./gradlew mutationTest
./gradlew themeValidate
./gradlew dependencyCheckAnalyze
./gradlew cyclonedxBom
```

## Command discovery

- `/help` shows the main command groups (including backend-specific helpers).
- `/help dcc` shows DCC chat/file commands.
- `/help chathistory`, `/help markread`, and `/help upload` show focused usage for those flows.
- `/help edit`, `/help redact`, and `/help delete` show IRCv3 compose mutation command help.
- Quassel-specific workflows: `/quasselsetup` and `/quasselnet ...`.
- `/filter help` shows local filter command usage.
- `/monitor help` shows monitor command usage.

## Configuration

### Runtime configuration (preferred)

When running IRCafe (from source or as a packaged jar), edit the runtime config file (loaded on startup and overrides the defaults in `application.yml`):

- Default path: `${XDG_CONFIG_HOME}/ircafe/ircafe.yml` when `XDG_CONFIG_HOME` is set.
- Otherwise falls back to: `~/.config/ircafe/ircafe.yml` (i.e. `${user.home}/.config/ircafe/ircafe.yml`)

To use a different location, set `ircafe.runtime-config` (environment variable: `IRCAFE_RUNTIME_CONFIG`) or pass it on the command line:

```bash
IRCAFE_RUNTIME_CONFIG=/path/to/ircafe.yml java -jar build/libs/ircafe-*.jar
# or:
java -jar build/libs/ircafe-*.jar --ircafe.runtime-config=/path/to/ircafe.yml
```

Server definitions (IRC, Quassel Core, Matrix) use the `irc.servers` section.

IRC example:

```yaml
irc:
    servers:
    - id: "libera"
      host: "irc.libera.chat"
      port: 6697
      tls: true

      # Optional: IRC PASS / server password (commonly needed for bouncers like ZNC)
      # ZNC examples: "username:password" or "username/network:password"
      serverPassword: "${IRCAFE_SERVER_PASSWORD:}"

      nick: "${IRCAFE_NICK:IRCafeUser}"
      login: "${IRCAFE_IDENT:ircafe}"
      realName: "${IRCAFE_REALNAME:IRCafe User}"

      sasl:
        enabled: false
        username: "${IRCAFE_SASL_USERNAME:}"
        password: "${IRCAFE_SASL_PASSWORD:}"
        mechanism: "PLAIN"

      autoJoin:
        - "#irclient"
```

Matrix example:

```yaml
irc:
    servers:
    - id: "matrix-main"
      backend: "matrix"
      # Host can be a plain host or a full base URL (for example with a path prefix).
      host: "https://matrix.example.org"
      port: 443
      tls: true

      # Matrix access token (preferred here). IRCafe also falls back to sasl.password if this is blank.
      serverPassword: "${IRCAFE_MATRIX_ACCESS_TOKEN:}"

      # Optional local display identity used by IRCafe UI defaults.
      nick: "@ircafe:example.org"
      login: "ircafe"
      realName: "IRCafe Matrix"

      # Optional auto-join targets (room id or alias).
      autoJoin:
        - "!someroom:example.org"
        - "#ircafe:example.org"
```

You can also override UI behavior under the `ircafe.ui` prefix. For inline image previews (direct image links in chat),
this is **disabled by default**:

```yaml
ircafe:
  ui:
    imageEmbedsEnabled: false
```

You can toggle this at runtime from the GUI: **Settings -> Preferences… -> Inline images**.

Other runtime settings (theme and appearance tweaks, tray/notifications, CTCP replies, IRCv3 capability toggles,
STS cache entries, monitor nick lists, filters, command aliases, interceptors, notification rules, logging preferences, and more)
are persisted to the runtime config file as you change them in the UI.


### Environment variables

The default `application.yml` uses these environment variables:

- `IRCAFE_NICK` (defaults to `IRCafeUser`)
- `IRCAFE_IDENT` (defaults to `ircafe`)
- `IRCAFE_REALNAME` (defaults to `IRCafe User`)
- `IRCAFE_CTCP_VERSION` (defaults to `IRCafe`)
- `IRCAFE_SERVER_PASSWORD` (no default) — IRC `PASS` / server password (useful for ZNC: `username:password` or `username/network:password`)
- `IRCAFE_SASL_USERNAME` (no default)
- `IRCAFE_SASL_PASSWORD` (no default)

You can set them for a single run:

```bash
IRCAFE_NICK=myNick ./gradlew bootRun
```

Or export them in your shell:

```bash
export IRCAFE_NICK=myNick
export IRCAFE_IDENT=myIdent
export IRCAFE_REALNAME="My IRC Client"
```

To enable SASL, set `irc.servers[].sasl.enabled: true` in your config and set `IRCAFE_SASL_USERNAME` and `IRCAFE_SASL_PASSWORD`.

### Data locations

- Runtime config and UI state default to `${XDG_CONFIG_HOME}/ircafe/ircafe.yml` when `XDG_CONFIG_HOME` is set, otherwise `~/.config/ircafe/ircafe.yml`.
- With logging enabled (default `ircafe.logging.hsqldb.nextToRuntimeConfig: true`), HSQLDB files (for example `ircafe-chatlog.*`) are stored next to the runtime config file.
- Linux `uninstall.sh` removes the installed app image and desktop entry, but does not remove user config/history data.

### Security notes

- Keep `irc.client.tls.trustAllCertificates: false` unless you are intentionally testing with self-signed certs in a trusted environment.
- Interceptor and notification rule script actions execute local scripts. Only use scripts and paths you trust.

### Platform notes

- System tray and desktop notification behavior depends on OS/session support.
- Linux supports D-Bus notification actions when enabled (`ircafe.ui.tray.linuxDbusActionsEnabled: true`).
- Linux app-image builds include desktop/install helper files; other platforms keep standard app-image packaging.

## Contributing

- Commit messages follow Conventional Commits: `<type>(<scope>): <subject>`.
- Local hooks are configured by `./scripts/setup-git-hooks.sh`; CI validates the same rules.
- Allowed types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`, `revert`.

## Project support

Please feel free to file an issue, or get in touch on Libera.Chat channel `#ircafe`

## License

GPL-3.0. See `LICENSE`.

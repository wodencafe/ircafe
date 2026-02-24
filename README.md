# IRCafe

IRCafe is a Java 25 desktop IRC client with a Swing UI and a Spring Boot backend.
<figure>
  <img width="50%" height="731" alt="Screenshot_20260130_115917" src="https://github.com/user-attachments/assets/e1d2402a-dbc8-48a6-b3c3-39d496872fa9" />
  <figcaption style="font-size: 0.8rem; text-align: center; color: grey;">
      <BR>
      <sub>* Light Theme</sub>
  </figcaption>
</figure>
<P></P>
<figure>
  <img width="50%" height="731" alt="Screenshot_20260130_115903" src="https://github.com/user-attachments/assets/bc22d5d8-46bd-4d4d-9718-648c1df5647c" />
  <figcaption style="font-size: 0.8rem; text-align: center; color: grey;">
      <BR>
      <sub>* Dark Theme</sub>
  </figcaption>
</figure>

## Features

### Core IRC

- Multi-server connections with add/edit/manage server dialogs.
- TLS and SASL (`AUTO`, `PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-1`, `EXTERNAL`, `ECDSA-NIST256P-CHALLENGE`).
- Auto-reconnect with backoff + jitter, plus heartbeat timeout detection.
- Global SOCKS5 proxy plus per-server proxy override (auth, remote DNS, connect/read timeouts, proxy test).
- Auto-join channels or PM targets and per-server perform-on-connect commands.
- Rich slash command support for connection/session control, channel/user ops, invite workflows, monitor, ignore/soft-ignore, CTCP, DCC, CHATHISTORY, filters, and raw IRC.

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
- Runtime diagnostics surfaces under `Application` (including terminal mirror and diagnostics streams).

## Requirements

- Java 25 (Only if running as a jar)

## Run from source

From the project root:

```bash
./gradlew bootRun
```

This launches the Swing UI.

## Build a runnable jar

```bash
./gradlew bootJar
java -jar build/libs/*.jar
```

## Build an app image (jpackage)

```bash
./gradlew jpackage
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

## Developer workflow

Common local checks:

```bash
./gradlew test
./gradlew check
```

Additional verification/reporting tasks:

```bash
./gradlew themeValidate
./gradlew dependencyCheckAnalyze
./gradlew cyclonedxBom
```

## Command discovery

- `/help` shows the main command groups.
- `/help dcc` shows DCC chat/file commands.
- `/help edit` and `/help redact` show IRCv3 compose/edit command help.
- `/filter help` shows local filter command usage.
- `/monitor help` shows monitor command usage.

## Configuration

### Runtime configuration (preferred)

When running IRCafe as a packaged jar, edit the runtime config file (loaded on startup and overrides the defaults in `application.yml`):

- Default path: `~/.config/ircafe/ircafe.yml` (i.e. `${user.home}/.config/ircafe/ircafe.yml`)

To use a different location, set `ircafe.runtime-config` (environment variable: `IRCAFE_RUNTIME_CONFIG`) or pass it on the command line:

```bash
IRCAFE_RUNTIME_CONFIG=/path/to/ircafe.yml java -jar build/libs/ircafe-*.jar
# or:
java -jar build/libs/ircafe-*.jar --ircafe.runtime-config=/path/to/ircafe.yml
```

The IRC server list uses the `irc.servers` section.

Example:

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

- Runtime config and UI state default to `~/.config/ircafe/ircafe.yml`.
- With logging enabled (default `ircafe.logging.hsqldb.nextToRuntimeConfig: true`), HSQLDB files (for example `ircafe-chatlog.*`) are stored next to the runtime config file.
- Linux `uninstall.sh` removes the installed app image and desktop entry, but does not remove user config/history data.

### Security notes

- Keep `irc.client.tls.trustAllCertificates: false` unless you are intentionally testing with self-signed certs in a trusted environment.
- Interceptor and notification rule script actions execute local scripts. Only use scripts and paths you trust.

### Platform notes

- System tray and desktop notification behavior depends on OS/session support.
- Linux supports D-Bus notification actions when enabled (`ircafe.ui.tray.linuxDbusActionsEnabled: true`).
- Linux app-image builds include desktop/install helper files; other platforms keep standard app-image packaging.

## Project support

Please feel free to file an issue, or get in touch on Libera.Chat channel `#ircafe`

## License

GPL-3.0. See `LICENSE`.

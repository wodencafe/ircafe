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

- Multi-server connections with per-server configuration.
- TLS and SASL (including `PLAIN`, `EXTERNAL`, `AUTO` flows).
- Auto-reconnect with backoff + jitter, plus heartbeat timeout detection.
- Global or per-server SOCKS5 proxy support (including optional auth and remote DNS).
- Auto-join channels and perform-on-connect commands.
- Rich slash command support, including:
  - `/join`, `/part`, `/msg`, `/notice`, `/me`, `/query`, `/whois`, `/whowas`
  - `/mode`, `/op`, `/deop`, `/voice`, `/devoice`, `/ban`, `/unban`
  - `/invite`, `/invites`, `/invjoin`, `/invignore`, `/invwhois`, `/invblock`, `/inviteautojoin`
  - `/dcc ...`, `/chathistory ...`, `/filter ...`, `/quote`, `/say`, `/help`

### IRCv3 and Bouncer Support

- Capability negotiation with per-capability toggles.
- Support for modern IRCv3 capabilities such as:
  - `message-tags`, `server-time`, `standard-replies`, `labeled-response`
  - `draft/reply`, `draft/react`, `message-edit`, `message-redaction`
  - `typing`, `read-marker`, `batch`, `chathistory`, `znc.in/playback`
- Message reply/reaction/edit/redaction flows in chat UI and command layer.
- Typing indicators (send + receive) and read-marker updates.
- ZNC and soju network discovery with ephemeral server entries and optional auto-connect preferences.

### Chat UX

- Docked chat workspaces with per-buffer state.
- Unread + highlight counters in the server tree.
- Presence folding (join/part/quit/nick condensation).
- Transcript context menu actions (reply, react, redact, inspect).
- Find-in-transcript support and auto-load older history.
- Command history, nick completion, and alias expansion.

### Filtering and Ignore

- Hard ignore and soft ignore lists (with CTCP behavior toggles).
- WeeChat-style render-time filters with:
  - Scope matching
  - Direction/kind/source/tag/text matching
  - Global defaults and per-scope overrides
- Filter placeholders/hints with collapse behavior and history-batch controls.

### Logging and History

- Optional embedded HSQLDB logging with Flyway-managed schema.
- Logging controls for PM logging, soft-ignored line logging, and retention.
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

### Media and Previews

- Optional inline image embedding for direct image links (`png`, `jpg`, `gif`, `webp`).
- Optional animated GIF playback.
- Link previews (OpenGraph/oEmbed + dedicated resolvers for common sites).

### DCC and Utility Panels

- DCC chat and file transfer commands plus DCC Transfers panel.
- Channel List node for `/list` results with filtering and double-click join.
- User command alias editor with HexChat `commands.conf` import.
- Runtime diagnostics surfaces under `Application` (including terminal mirror and diagnostics streams).

## Current Limits

- DCC `RESUME`/`ACCEPT` incoming control messages are currently surfaced as unsupported.
- Filter rule actions are currently `HIDE`-only.
- Pushy notification integration is configuration-driven (`ircafe.pushy`) and does not currently have a dedicated GUI editor.
- Chat log writer queue/batch sizing is currently fixed in code (not yet configurable in UI/runtime config).

## Requirements

- Java 25

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

Other runtime settings (tray/notifications, filters, command aliases, interceptors, logging preferences, and more)
are persisted to the runtime config file as you change them in the UI.


### Environment variables

The default `application.yml` uses these environment variables:

- `IRCAFE_NICK` (defaults to `IRCafeUser`)
- `IRCAFE_IDENT` (defaults to `ircafe`)
- `IRCAFE_REALNAME` (defaults to `IRCafe User`)
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

## License

GPL-3.0. See `LICENSE`.

# IRCafe

IRCafe is a Java 21 desktop IRC client with a Swing UI and a Spring Boot backend.

<img width="60%" height="60%" alt="Screenshot_20260123_141509" src="https://github.com/user-attachments/assets/1c34e624-f6e9-4e30-a264-e8f067d4053a" />

## Requirements

- Java 21

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

The IRC settings use the prefix `irc.server`.

Example:

```yaml
irc:
  servers:
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
      - "#test"
```

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

To enable SASL, set `irc.server.sasl.enabled: true` in `application.yml` and set `IRCAFE_SASL_USERNAME` and `IRCAFE_SASL_PASSWORD`.

## License

GPL-3.0. See `LICENSE`.

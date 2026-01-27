# IRCafe

IRCafe is a Java 21 desktop IRC client with a Swing UI and a Spring Boot backend.
<figure>
  <img width="50%" height="620" alt="Light Theme" src="https://github.com/user-attachments/assets/af4534df-346b-482b-8a0d-e75b2faac7e8" />
  <figcaption style="font-size: 0.8rem; text-align: center; color: grey;">
      <BR>
      <sub>* Light Theme</sub>
  </figcaption>
</figure>
<P></P>
<figure>
  <img width="50%" height="620" alt="Dark Theme" src="https://github.com/user-attachments/assets/cfc9da43-57b4-45f3-b805-954b8591cd4d" />
  <figcaption style="font-size: 0.8rem; text-align: center; color: grey;">
      <BR>
      <sub>* Dark Theme</sub>
  </figcaption>
</figure>

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

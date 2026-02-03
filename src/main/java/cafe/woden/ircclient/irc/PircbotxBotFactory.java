package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.EnableCapHandler;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Factory for building a configured {@link PircBotX} instance for a given server.
 * <p>
 * This keeps {@link PircbotxIrcClientService} focused on orchestration (connect/disconnect),
 * and isolates the PircbotX configuration details.
 */
@Component
public class PircbotxBotFactory {

  public PircBotX build(IrcProperties.Server s, String version, ListenerAdapter listener) {
    SocketFactory socketFactory = s.tls() ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();

    Configuration.Builder builder = new Configuration.Builder()
        .setName(s.nick())
        .setLogin(s.login())
        .setRealName(s.realName())
        .setVersion(version)
        .addServer(s.host(), s.port())
        .setSocketFactory(socketFactory)
        // Enable CAP so we can request low-cost IRCv3 capabilities (e.g. userhost-in-names).
        .setCapEnabled(true)
        // Prefer hostmasks in the initial NAMES list (when supported). If unsupported, ignore.
        .addCapHandler(new EnableCapHandler("userhost-in-names", true))
        // IRCv3 away-notify: server will send user AWAY state changes as raw AWAY commands.
        .addCapHandler(new EnableCapHandler("away-notify", true))
        .setAutoNickChange(true)
        // We manage reconnects ourselves so we can surface status + use backoff.
        .setAutoReconnect(false)
        .addListener(listener);

    if (s.serverPassword() != null && !s.serverPassword().isBlank()) {
      builder.setServerPassword(s.serverPassword());
    }

    // Auto-join channels from config
    for (String chan : s.autoJoin()) {
      String ch = chan == null ? "" : chan.trim();
      if (!ch.isEmpty()) builder.addAutoJoinChannel(ch);
    }

    // SASL (PLAIN)
    if (s.sasl() != null && s.sasl().enabled()) {
      if (!"PLAIN".equalsIgnoreCase(s.sasl().mechanism())) {
        throw new IllegalStateException(
            "Only SASL mechanism PLAIN is supported for now (got: " + s.sasl().mechanism() + ")"
        );
      }
      if (s.sasl().username().isBlank() || s.sasl().password().isBlank()) {
        throw new IllegalStateException("SASL enabled but username/password not set");
      }
      builder.setCapEnabled(true);
      builder.addCapHandler(new SASLCapHandler(s.sasl().username(), s.sasl().password()));
    }

    return new PircBotX(builder.buildConfiguration());
  }
}

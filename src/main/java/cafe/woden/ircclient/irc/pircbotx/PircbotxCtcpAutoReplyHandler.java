package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles built-in CTCP auto-replies for the PircBotX transport. */
@InfrastructureLayer
final class PircbotxCtcpAutoReplyHandler {
  private static final Logger log = LoggerFactory.getLogger(PircbotxCtcpAutoReplyHandler.class);

  private final String version;
  private final CtcpReplyRuntimeConfigPort runtimeConfig;

  PircbotxCtcpAutoReplyHandler(String version, CtcpReplyRuntimeConfigPort runtimeConfig) {
    this.version = version;
    this.runtimeConfig = runtimeConfig;
  }

  boolean handleIfPresent(PircBotX bot, String fromNick, String message) {
    if (message == null || message.length() < 2) return false;
    if (message.charAt(0) != 0x01 || message.charAt(message.length() - 1) != 0x01) return false;

    SelfNickSnapshot selfNick = resolveSelfNickSnapshot(bot);
    if (isSelfEcho(fromNick, selfNick)) {
      log.debug(
          "[ircafe] CTCPDBG service-drop-self from={} n1={} n2={} n3={} message={}",
          Objects.toString(fromNick, "").trim(),
          selfNick.currentNick(),
          selfNick.userBotNick(),
          selfNick.configuredNick(),
          message.replace('\u0001', '|'));
      return true;
    }

    String inner = message.substring(1, message.length() - 1).trim();
    if (inner.isEmpty()) return false;

    String command = parseCommand(inner);
    log.debug(
        "[ircafe] CTCPDBG service-eval from={} cmd={} inner={} n1={} n2={} n3={}",
        Objects.toString(fromNick, ""),
        command,
        inner,
        selfNick.currentNick(),
        selfNick.userBotNick(),
        selfNick.configuredNick());

    if (!isKnownAutoReplyCommand(command)) {
      return false;
    }
    if (!isReplyEnabled(command)) {
      log.debug(
          "[ircafe] CTCPDBG service-drop-disabled from={} cmd={}",
          Objects.toString(fromNick, ""),
          command);
      return true;
    }

    return switch (command) {
      case "VERSION" -> sendVersionReply(bot, fromNick);
      case "PING" -> sendPingReply(bot, fromNick, inner);
      case "TIME" -> sendTimeReply(bot, fromNick);
      default -> false;
    };
  }

  private boolean sendVersionReply(PircBotX bot, String fromNick) {
    String resolvedVersion = (version == null) ? "IRCafe" : version;
    log.debug(
        "[ircafe] CTCPDBG service-send cmd=VERSION to={} payload={}",
        Objects.toString(fromNick, ""),
        "VERSION " + resolvedVersion);
    bot.sendIRC()
        .notice(PircbotxUtil.sanitizeNick(fromNick), "\u0001VERSION " + resolvedVersion + "\u0001");
    return true;
  }

  private boolean sendPingReply(PircBotX bot, String fromNick, String inner) {
    String payload = "";
    int separator = inner.indexOf(' ');
    if (separator >= 0 && separator + 1 < inner.length()) {
      payload = inner.substring(separator + 1).trim();
    }
    String body = payload.isEmpty() ? "\u0001PING\u0001" : "\u0001PING " + payload + "\u0001";
    log.debug(
        "[ircafe] CTCPDBG service-send cmd=PING to={} payload={}",
        Objects.toString(fromNick, ""),
        body.replace('\u0001', '|'));
    bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), body);
    return true;
  }

  private boolean sendTimeReply(PircBotX bot, String fromNick) {
    String now = ZonedDateTime.now().toString();
    log.debug(
        "[ircafe] CTCPDBG service-send cmd=TIME to={} payload=TIME {}",
        Objects.toString(fromNick, ""),
        now);
    bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), "\u0001TIME " + now + "\u0001");
    return true;
  }

  private boolean isReplyEnabled(String command) {
    if (runtimeConfig == null) return true;
    if (!isKnownAutoReplyCommand(command)) return true;
    if (!runtimeConfig.readCtcpAutoRepliesEnabled(true)) return false;
    return switch (command) {
      case "VERSION" -> runtimeConfig.readCtcpAutoReplyVersionEnabled(true);
      case "PING" -> runtimeConfig.readCtcpAutoReplyPingEnabled(true);
      case "TIME" -> runtimeConfig.readCtcpAutoReplyTimeEnabled(true);
      default -> true;
    };
  }

  private static boolean isKnownAutoReplyCommand(String command) {
    return "VERSION".equals(command) || "PING".equals(command) || "TIME".equals(command);
  }

  private static String parseCommand(String inner) {
    String command = inner;
    int separator = inner.indexOf(' ');
    if (separator >= 0) {
      command = inner.substring(0, separator);
    }
    return command.trim().toUpperCase(Locale.ROOT);
  }

  private static boolean isSelfEcho(String fromNick, SelfNickSnapshot selfNick) {
    String from = Objects.toString(fromNick, "").trim();
    if (from.isEmpty()) return false;
    return equalsIgnoreCaseTrimmed(from, selfNick.currentNick())
        || equalsIgnoreCaseTrimmed(from, selfNick.userBotNick())
        || equalsIgnoreCaseTrimmed(from, selfNick.configuredNick());
  }

  private static boolean equalsIgnoreCaseTrimmed(String left, String right) {
    String normalizedRight = Objects.toString(right, "").trim();
    return !normalizedRight.isEmpty() && left.equalsIgnoreCase(normalizedRight);
  }

  private static SelfNickSnapshot resolveSelfNickSnapshot(PircBotX bot) {
    String currentNick = null;
    String userBotNick = null;
    String configuredNick = null;

    try {
      currentNick = PircbotxUtil.safeStr(bot::getNick, null);
    } catch (Exception ignored) {
    }
    try {
      if (bot != null && bot.getUserBot() != null) {
        userBotNick = bot.getUserBot().getNick();
      }
    } catch (Exception ignored) {
    }
    try {
      Object cfg = bot == null ? null : bot.getConfiguration();
      if (cfg != null) {
        try {
          java.lang.reflect.Method method = cfg.getClass().getMethod("getNick");
          Object nick = method.invoke(cfg);
          if (nick != null) {
            configuredNick = String.valueOf(nick);
          }
        } catch (Exception ignored) {
        }
      }
    } catch (Exception ignored) {
    }

    return new SelfNickSnapshot(
        Objects.toString(currentNick, ""),
        Objects.toString(userBotNick, ""),
        Objects.toString(configuredNick, ""));
  }

  private record SelfNickSnapshot(String currentNick, String userBotNick, String configuredNick) {}
}

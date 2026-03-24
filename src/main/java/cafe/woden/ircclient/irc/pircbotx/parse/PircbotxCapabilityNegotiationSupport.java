package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles tracked capability state changes and fallback requests from CAP lines. */
public final class PircbotxCapabilityNegotiationSupport {

  private static final Logger log =
      LoggerFactory.getLogger(PircbotxCapabilityNegotiationSupport.class);

  private final PircBotX bot;
  private final String serverId;
  private final PircbotxConnectionState conn;
  private final Consumer<ServerIrcEvent> sink;
  private final PircbotxCapabilityStateSupport capabilityStateSupport;

  public PircbotxCapabilityNegotiationSupport(
      PircBotX bot,
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> sink,
      PircbotxCapabilityStateSupport capabilityStateSupport) {
    this.bot = Objects.requireNonNull(bot, "bot");
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.sink = Objects.requireNonNull(sink, "sink");
    this.capabilityStateSupport =
        Objects.requireNonNull(capabilityStateSupport, "capabilityStateSupport");
  }

  public void observe(ParsedCapLine capLine) {
    if (!capLine.hasTokens()) return;

    if (capLine.isAction("ACK", "DEL")) {
      applyCapStateFromCapLine(capLine);
    } else if (capLine.isAction("NEW", "LS")) {
      emitCapAvailabilityFromCapLine(capLine);
    } else if (capLine.isAction("NAK")) {
      emitCapNakFromCapLine(capLine);
    }

    maybeRequestMessageTagsFallback(capLine);
    maybeRequestHistoryCapabilityFallback(capLine);
  }

  private void applyCapStateFromCapLine(ParsedCapLine capLine) {
    String action = capLine.action();
    boolean fromAck = capLine.isAction("ACK");
    boolean fromDel = capLine.isAction("DEL");
    if (!fromAck && !fromDel) return;

    boolean emittedAny = false;
    for (String token : capLine.tokens()) {
      boolean tokenDisable = token.startsWith("-");
      String capName = canonicalCapName(token);
      if (capName == null) continue;

      boolean enabled = fromAck && !tokenDisable;
      if (fromDel || tokenDisable) enabled = false;

      if (enabled && PircbotxZncParsers.seemsZncCap(capName)) {
        if (conn.markZncDetected()) {
          log.debug("[{}] detected ZNC via CAP {}: {}", serverId, action, capName);
        }
      }

      capabilityStateSupport.apply(capName, enabled, action);
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, enabled)));
      emittedAny = true;
    }

    if (emittedAny) {
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ConnectionFeaturesUpdated(
                  Instant.now(), "cap-" + action.toLowerCase(Locale.ROOT))));
    }
  }

  private void emitCapAvailabilityFromCapLine(ParsedCapLine capLine) {
    String action = capLine.action();
    if (!capLine.isAction("NEW", "LS")) return;

    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if (capName == null || capName.isBlank()) continue;
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, false)));
    }
  }

  private void maybeRequestMessageTagsFallback(ParsedCapLine capLine) {
    if (!capLine.isAction("LS", "NEW")) return;
    if (conn.isMessageTagsCapAcked()) return;
    if (!conn.beginMessageTagsFallbackRequest()) return;

    boolean offered = false;
    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if ("message-tags".equalsIgnoreCase(capName)) {
        offered = true;
        break;
      }
    }
    if (!offered) {
      conn.clearMessageTagsFallbackRequest();
      return;
    }

    try {
      bot.sendCAP().request("message-tags");
      log.debug(
          "[{}] fallback CAP REQ sent for message-tags (downstream capability remained unenabled)",
          serverId);
    } catch (Exception ex) {
      conn.clearMessageTagsFallbackRequest();
      log.debug("[{}] fallback CAP REQ for message-tags failed", serverId, ex);
    }
  }

  private void maybeRequestHistoryCapabilityFallback(ParsedCapLine capLine) {
    if (!capLine.isAction("LS", "NEW")) return;

    boolean offeredBatch = false;
    boolean offeredChatHistory = false;
    boolean offeredDraftChatHistory = false;
    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if ("batch".equalsIgnoreCase(capName)) {
        offeredBatch = true;
      } else if ("chathistory".equalsIgnoreCase(capName)) {
        offeredChatHistory = true;
      } else if ("draft/chathistory".equalsIgnoreCase(capName)) {
        offeredDraftChatHistory = true;
      }
    }

    ArrayList<String> requestedCaps = new ArrayList<>(2);
    boolean requestedBatch = false;
    boolean requestedHistory = false;

    if (offeredBatch && !conn.isBatchCapAcked() && conn.beginBatchFallbackRequest()) {
      requestedCaps.add("batch");
      requestedBatch = true;
    }

    String historyCapToRequest = "";
    if (!conn.isChatHistoryCapAcked()) {
      if (offeredChatHistory) {
        historyCapToRequest = "chathistory";
      } else if (offeredDraftChatHistory) {
        historyCapToRequest = "draft/chathistory";
      }
    }
    if (!historyCapToRequest.isEmpty() && conn.beginChatHistoryFallbackRequest()) {
      requestedCaps.add(historyCapToRequest);
      requestedHistory = true;
    }

    if (requestedCaps.isEmpty()) return;

    try {
      bot.sendCAP().request(requestedCaps.toArray(new String[0]));
      log.debug("[{}] fallback CAP REQ sent for {}", serverId, String.join(", ", requestedCaps));
    } catch (Exception ex) {
      if (requestedBatch) {
        conn.clearBatchFallbackRequest();
      }
      if (requestedHistory) {
        conn.clearChatHistoryFallbackRequest();
      }
      log.debug("[{}] fallback CAP REQ for history capabilities failed", serverId, ex);
    }
  }

  private void emitCapNakFromCapLine(ParsedCapLine capLine) {
    String action = capLine.action();
    if (!capLine.isAction("NAK")) return;

    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if (capName == null || capName.isBlank()) continue;
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, false)));
    }
  }

  private static String canonicalCapName(String rawToken) {
    String value = Objects.toString(rawToken, "").trim();
    if (value.isEmpty()) return null;
    if (value.startsWith(":")) value = value.substring(1).trim();
    while (!value.isEmpty()) {
      char leading = value.charAt(0);
      if (leading == '-' || leading == '~' || leading == '=') {
        value = value.substring(1).trim();
        continue;
      }
      break;
    }
    int eq = value.indexOf('=');
    if (eq >= 0) value = value.substring(0, eq).trim();
    return value.isEmpty() ? null : value;
  }
}

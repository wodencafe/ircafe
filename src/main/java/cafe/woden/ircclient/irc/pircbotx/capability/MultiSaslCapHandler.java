package cafe.woden.ircclient.irc.pircbotx.capability;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.CapHandler;
import org.pircbotx.exception.CAPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SASL cap handler supporting multiple mechanisms.
 *
 * <p>Supported mechanisms:
 *
 * <ul>
 *   <li>PLAIN
 *   <li>EXTERNAL
 *   <li>SCRAM-SHA-1
 *   <li>SCRAM-SHA-256
 *   <li>ECDSA-NIST256P-CHALLENGE (secret interpreted as base64 PKCS#8 DER EC private key)
 *   <li>AUTO (choose strongest offered & satisfiable mechanism)
 * </ul>
 *
 * <p>Note: The "password" field in config is treated as a generic {@code secret}.
 */
public final class MultiSaslCapHandler implements CapHandler {

  private static final Logger log = LoggerFactory.getLogger(MultiSaslCapHandler.class);

  private final String username;
  private final String secret;
  private final String configuredMechanism;
  private final boolean disconnectOnFailure;
  private final PircbotxSaslAuthenticateFraming authenticateFraming =
      new PircbotxSaslAuthenticateFraming();
  private final PircbotxSaslMechanismSelector mechanismSelector =
      new PircbotxSaslMechanismSelector();
  private final PircbotxSaslResponseFactory responseFactory = new PircbotxSaslResponseFactory();

  private final Set<String> offeredMechanismsUpper = new LinkedHashSet<>();
  private boolean saslOffered;
  private boolean saslRequested;
  private boolean saslAcked;

  private State state = State.INIT;
  private String chosenMechanism;

  // SCRAM state
  private PircbotxScramSaslExchange scram;

  public MultiSaslCapHandler(
      String username, String secret, String mechanism, boolean disconnectOnFailure) {
    this.username = Objects.toString(username, "");
    this.secret = Objects.toString(secret, "");
    this.configuredMechanism = Objects.toString(mechanism, "PLAIN").trim();
    this.disconnectOnFailure = disconnectOnFailure;
  }

  @Override
  public boolean handleLS(PircBotX bot, ImmutableList<String> serverCaps) throws CAPException {
    if (isLsContinuationMarkerOnly(serverCaps)) return false;
    saslOffered = false;
    offeredMechanismsUpper.clear();
    if (serverCaps != null) {
      for (String cap : serverCaps) {
        if (cap == null) continue;
        String normalized = cap.trim();
        if (normalized.startsWith(":")) normalized = normalized.substring(1).trim();
        if (normalized.isEmpty()) continue;

        if (normalized.equalsIgnoreCase("sasl")
            || normalized.toLowerCase(Locale.ROOT).startsWith("sasl=")) {
          saslOffered = true;
          int idx = normalized.indexOf('=');
          if (idx >= 0 && idx + 1 < normalized.length()) {
            String mechList = normalized.substring(idx + 1);
            for (String m : mechList.split(",")) {
              String mm = m.trim();
              if (!mm.isEmpty()) offeredMechanismsUpper.add(mm.toUpperCase(Locale.ROOT));
            }
          }
        }
      }
    }

    if (!saslOffered) {
      return true;
    }
    if (!saslRequested) {
      saslRequested = true;
      state = State.REQUESTED;
      bot.sendCAP().request("sasl");
      log.debug(
          "[SASL] Requested capability sasl (offered mechanisms: {})", offeredMechanismsUpper);
    }
    return false;
  }

  private static boolean isLsContinuationMarkerOnly(ImmutableList<String> serverCaps) {
    if (serverCaps == null || serverCaps.size() != 1) return false;
    String token = Objects.toString(serverCaps.get(0), "").trim();
    if (token.startsWith(":")) token = token.substring(1).trim();
    return "*".equals(token);
  }

  @Override
  public boolean handleACK(PircBotX bot, ImmutableList<String> caps) throws CAPException {
    if (!containsCap(caps, "sasl")) return state.isTerminal();

    saslAcked = true;
    state = State.ACKED;

    chosenMechanism =
        mechanismSelector.choose(configuredMechanism, username, secret, offeredMechanismsUpper);
    if (chosenMechanism == null || chosenMechanism.isBlank()) {
      return fail(
          bot,
          "No usable SASL mechanism available (configured="
              + configuredMechanism
              + ", offered="
              + offeredMechanismsUpper
              + ")");
    }

    log.info("[SASL] Starting SASL mechanism {}", chosenMechanism);
    bot.sendRaw().rawLine("AUTHENTICATE " + chosenMechanism);
    state = State.AUTH_SENT;
    return false;
  }

  @Override
  public boolean handleNAK(PircBotX bot, ImmutableList<String> caps) throws CAPException {
    if (!containsCap(caps, "sasl")) return state.isTerminal();
    return fail(bot, "Server NAK'd sasl capability");
  }

  @Override
  public boolean handleUnknown(PircBotX bot, String line) throws CAPException {
    if (state.isTerminal()) return true;
    if (!saslOffered || !saslRequested || !saslAcked) return false;
    if (line == null || line.isBlank()) return false;

    PircbotxParsedIrcLine pl = PircbotxParsedIrcLine.parse(line);
    if (pl == null) return false;

    if ("AUTHENTICATE".equalsIgnoreCase(pl.command())) {
      String data = pl.trailing() == null ? "" : pl.trailing();
      onAuthenticate(bot, data);
      return state.isTerminal();
    }

    // SASL numerics
    if (pl.isNumeric()) {
      int num = pl.numeric();
      switch (num) {
        case 903: // SASL success
        case 907: // already authenticated
          log.info("[SASL] Authentication successful ({}).", num);
          state = State.DONE;
          return true;
        case 904:
        case 905:
        case 906:
          return fail(bot, "SASL failed (" + num + ")");
        default:
          return false;
      }
    }

    return false;
  }

  private void onAuthenticate(PircBotX bot, String data) throws CAPException {
    byte[] decoded = authenticateFraming.acceptServerPayload(data).orElse(null);
    if (decoded != null) {
      handleServerAuthMessage(bot, decoded);
    }
  }

  private void handleServerAuthMessage(PircBotX bot, byte[] decoded) throws CAPException {
    String mech = chosenMechanism == null ? "" : chosenMechanism.toUpperCase(Locale.ROOT);
    switch (mech) {
      case "PLAIN" -> handlePlain(bot);
      case "EXTERNAL" -> handleExternal(bot);
      case "SCRAM-SHA-1" -> handleScram(bot, "SHA-1", new String(decoded, StandardCharsets.UTF_8));
      case "SCRAM-SHA-256" ->
          handleScram(bot, "SHA-256", new String(decoded, StandardCharsets.UTF_8));
      case "ECDSA-NIST256P-CHALLENGE" -> handleEcdsa(bot, decoded);
      default ->
          throw new CAPException(
              CAPException.Reason.UNSUPPORTED_CAPABILITY,
              "Unsupported SASL mechanism: " + chosenMechanism);
    }
  }

  private void handlePlain(PircBotX bot) {
    if (state == State.EXCHANGING) return;
    state = State.EXCHANGING;
    sendAuthenticateResponse(bot, responseFactory.createPlain(username, secret));
  }

  private void handleExternal(PircBotX bot) {
    if (state == State.EXCHANGING) return;
    state = State.EXCHANGING;
    sendAuthenticateResponse(bot, responseFactory.createExternal(username));
  }

  private void handleEcdsa(PircBotX bot, byte[] challenge) throws CAPException {
    // Server sends base64 challenge (bytes). Client responds with base64 signature.
    if (state == State.EXCHANGING) return;
    state = State.EXCHANGING;
    sendAuthenticateResponse(bot, responseFactory.createEcdsa(secret, challenge));
  }

  private void handleScram(PircBotX bot, String digest, String serverMsg) throws CAPException {
    if (scram == null) {
      scram = new PircbotxScramSaslExchange(digest, username, secret);
      // Client first message (no server data required; serverMsg should be empty on '+').
      String clientFirst = scram.clientFirstMessage();
      sendAuthenticateResponse(
          bot, Base64.getEncoder().encodeToString(clientFirst.getBytes(StandardCharsets.UTF_8)));
      return;
    }

    if (!scram.hasSeenServerFirst()) {
      scram.onServerFirst(serverMsg);
      String clientFinal = scram.clientFinalMessage();
      sendAuthenticateResponse(
          bot, Base64.getEncoder().encodeToString(clientFinal.getBytes(StandardCharsets.UTF_8)));
      return;
    }

    if (!scram.hasSeenServerFinal()) {
      scram.onServerFinal(serverMsg);
      // Per SASL framing, send empty response to finish exchange.
      sendAuthenticateResponse(bot, "");
    }
  }

  private void sendAuthenticateResponse(PircBotX bot, String b64) {
    for (String payload : authenticateFraming.encodeClientResponse(b64)) {
      bot.sendRaw().rawLine("AUTHENTICATE " + payload);
    }
  }

  private boolean fail(PircBotX bot, String reason) throws CAPException {
    log.warn("[SASL] {} (disconnectOnFailure={})", reason, disconnectOnFailure);
    state = State.FAILED;
    if (disconnectOnFailure) {
      throw new CAPException(CAPException.Reason.SASL_FAILED, reason);
    }
    // Best-effort abort to let the server continue registration.
    try {
      bot.sendRaw().rawLine("AUTHENTICATE *");
    } catch (Exception ignored) {
    }
    return true;
  }

  private boolean containsCap(ImmutableList<String> caps, String want) {
    if (caps == null || caps.isEmpty()) return false;
    for (String c : caps) {
      if (c == null) continue;
      String n = c.trim();
      if (n.startsWith(":")) n = n.substring(1).trim();
      if (n.equalsIgnoreCase(want)
          || n.toLowerCase(Locale.ROOT).startsWith(want.toLowerCase(Locale.ROOT) + "=")) {
        return true;
      }
    }
    return false;
  }

  private enum State {
    INIT,
    REQUESTED,
    ACKED,
    AUTH_SENT,
    EXCHANGING,
    DONE,
    FAILED;

    boolean isTerminal() {
      return this == DONE || this == FAILED;
    }
  }
}

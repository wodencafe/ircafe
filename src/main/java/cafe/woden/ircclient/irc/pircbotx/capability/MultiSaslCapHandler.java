package cafe.woden.ircclient.irc.pircbotx.capability;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
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

  // IRCv3 SASL uses 400-byte base64 chunks.
  private static final int SASL_CHUNK_LEN = 400;

  private final String username;
  private final String secret;
  private final String configuredMechanism;
  private final boolean disconnectOnFailure;

  private final Set<String> offeredMechanismsUpper = new LinkedHashSet<>();
  private boolean saslOffered;
  private boolean saslRequested;
  private boolean saslAcked;

  private State state = State.INIT;
  private String chosenMechanism;

  // Server AUTHENTICATE chunks (base64 string)
  private final StringBuilder authInB64 = new StringBuilder();

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

    chosenMechanism = chooseMechanism();
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

    ParsedLine pl = ParsedLine.parse(line);
    if (pl == null) return false;

    if ("AUTHENTICATE".equalsIgnoreCase(pl.command)) {
      String data = pl.trailing == null ? "" : pl.trailing;
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
    // Server sends: AUTHENTICATE +  (empty/initial challenge)
    // or base64 chunks. Chunks are 400 bytes; if exactly 400, more will follow.
    if (data == null) data = "";
    data = data.trim();

    if ("+".equals(data)) {
      // '+' can be an empty initial challenge or a terminator after full 400-byte chunks.
      if (authInB64.length() > 0) {
        decodeAndHandleAuthBuffer(bot);
      } else {
        handleServerAuthMessage(bot, new byte[0]);
      }
      return;
    }

    authInB64.append(data);
    if (data.length() == SASL_CHUNK_LEN) {
      // More chunks will follow.
      return;
    }
    decodeAndHandleAuthBuffer(bot);
  }

  private void decodeAndHandleAuthBuffer(PircBotX bot) throws CAPException {
    String joined = authInB64.toString();
    authInB64.setLength(0);
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(joined);
    } catch (IllegalArgumentException e) {
      throw new CAPException(
          CAPException.Reason.OTHER, "Invalid base64 from server during SASL", e);
    }
    handleServerAuthMessage(bot, decoded);
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
    String payload = "\0" + username + "\0" + secret;
    sendAuthenticateResponse(
        bot, Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
  }

  private void handleExternal(PircBotX bot) {
    if (state == State.EXCHANGING) return;
    state = State.EXCHANGING;
    if (username == null || username.isBlank()) {
      // Empty response
      bot.sendRaw().rawLine("AUTHENTICATE +");
      return;
    }
    sendAuthenticateResponse(
        bot, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
  }

  private void handleEcdsa(PircBotX bot, byte[] challenge) throws CAPException {
    // Server sends base64 challenge (bytes). Client responds with base64 signature.
    if (state == State.EXCHANGING) return;
    state = State.EXCHANGING;
    // In practice, servers tend to send binary challenge.
    // NOTE: This is best-effort; if your network expects a different signing scheme, we can adjust.
    try {
      byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
      PrivateKey pk =
          KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
      Signature sig = Signature.getInstance("SHA256withECDSA");
      sig.initSign(pk);
      sig.update(challenge);
      byte[] signature = sig.sign();
      sendAuthenticateResponse(bot, Base64.getEncoder().encodeToString(signature));
    } catch (Exception e) {
      throw new CAPException(CAPException.Reason.OTHER, "Failed ECDSA SASL signing", e);
    }
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
      bot.sendRaw().rawLine("AUTHENTICATE +");
    }
  }

  private void sendAuthenticateResponse(PircBotX bot, String b64) {
    if (b64 == null) b64 = "";
    if (b64.isEmpty()) {
      bot.sendRaw().rawLine("AUTHENTICATE +");
      return;
    }
    int idx = 0;
    while (idx < b64.length()) {
      int end = Math.min(b64.length(), idx + SASL_CHUNK_LEN);
      bot.sendRaw().rawLine("AUTHENTICATE " + b64.substring(idx, end));
      idx = end;
    }
    // If the last chunk was exactly 400 bytes, we must send an empty terminator.
    if (b64.length() % SASL_CHUNK_LEN == 0) {
      bot.sendRaw().rawLine("AUTHENTICATE +");
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

  private String chooseMechanism() {
    String cfg = configuredMechanism.toUpperCase(Locale.ROOT);
    if (cfg.isBlank()) cfg = "PLAIN";

    // If user explicitly set a mechanism, honor it.
    if (!"AUTO".equals(cfg)) {
      return cfg;
    }

    boolean hasUser = username != null && !username.isBlank();
    boolean hasSecret = secret != null && !secret.isBlank();

    // Some servers advertise "sasl" without listing mechanisms (or PircBotX provides a bare
    // 'sasl').
    // In that case, make a conservative guess.
    if (offeredMechanismsUpper.isEmpty()) {
      if (!hasSecret) {
        // Only EXTERNAL can work without a shared secret.
        return "EXTERNAL";
      }
      return hasUser ? "PLAIN" : null;
    }

    // If we don't have a secret, only EXTERNAL is viable.
    if (!hasSecret) {
      return offeredMechanismsUpper.contains("EXTERNAL") ? "EXTERNAL" : null;
    }

    // Password-based mechs require a username.
    if (!hasUser) {
      return null;
    }

    // Prefer password-based mechanisms when a secret is present.
    // We intentionally do NOT auto-select EXTERNAL (TLS client cert) or ECDSA (private key) here,
    // because a user-provided "secret" is most commonly a password.
    for (String p : List.of("SCRAM-SHA-256", "SCRAM-SHA-1", "PLAIN")) {
      if (offeredMechanismsUpper.contains(p)) return p;
    }

    // Fallback: try PLAIN.
    return "PLAIN";
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

  private static final class ParsedLine {
    final String command;
    final String trailing;

    private ParsedLine(String command, String trailing) {
      this.command = command;
      this.trailing = trailing;
    }

    boolean isNumeric() {
      return command != null
          && command.length() == 3
          && Character.isDigit(command.charAt(0))
          && Character.isDigit(command.charAt(1))
          && Character.isDigit(command.charAt(2));
    }

    int numeric() {
      return Integer.parseInt(command);
    }

    static ParsedLine parse(String raw) {
      if (raw == null) return null;
      String line = raw;

      // Strip message-tags.
      if (line.startsWith("@")) {
        int sp = line.indexOf(' ');
        if (sp > 0 && sp + 1 < line.length()) {
          line = line.substring(sp + 1);
        }
      }

      if (line.startsWith(":")) {
        int sp = line.indexOf(' ');
        if (sp > 0) {

          line = line.substring(sp + 1);
        }
      }
      line = line.trim();
      if (line.isEmpty()) return null;

      String trailing = "";
      int trailIdx = line.indexOf(" :");
      if (trailIdx >= 0) {
        trailing = line.substring(trailIdx + 2);
        line = line.substring(0, trailIdx);
      }

      String[] parts = line.split("\\s+");
      if (parts.length == 0) return null;
      String cmd = parts[0];

      // AUTHENTICATE's argument is not a trailing field; it is a normal parameter.
      if ("AUTHENTICATE".equalsIgnoreCase(cmd)) {
        String arg = (parts.length >= 2) ? parts[1] : "";
        return new ParsedLine(cmd, arg);
      }

      return new ParsedLine(cmd, trailing);
    }
  }
}

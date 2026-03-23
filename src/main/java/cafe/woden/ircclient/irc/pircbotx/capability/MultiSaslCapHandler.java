package cafe.woden.ircclient.irc.pircbotx.capability;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
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

  private PircbotxSaslCapabilityOffer capabilityOffer =
      new PircbotxSaslCapabilityOffer(false, false, Set.of());
  private boolean saslRequested;
  private boolean saslAcked;
  private final PircbotxScramSaslConversation scramConversation;

  private State state = State.INIT;
  private String chosenMechanism;

  public MultiSaslCapHandler(
      String username, String secret, String mechanism, boolean disconnectOnFailure) {
    this.username = Objects.toString(username, "");
    this.secret = Objects.toString(secret, "");
    this.configuredMechanism = Objects.toString(mechanism, "PLAIN").trim();
    this.disconnectOnFailure = disconnectOnFailure;
    this.scramConversation = new PircbotxScramSaslConversation(this.username, this.secret);
  }

  @Override
  public boolean handleLS(PircBotX bot, ImmutableList<String> serverCaps) throws CAPException {
    PircbotxSaslCapabilityOffer parsedOffer = PircbotxSaslCapabilityOffer.parse(serverCaps);
    if (parsedOffer.continuationOnly()) return false;

    capabilityOffer = parsedOffer;
    if (!capabilityOffer.saslOffered()) {
      return true;
    }
    if (!saslRequested) {
      saslRequested = true;
      state = State.REQUESTED;
      bot.sendCAP().request("sasl");
      log.debug(
          "[SASL] Requested capability sasl (offered mechanisms: {})",
          capabilityOffer.offeredMechanismsUpper());
    }
    return false;
  }

  @Override
  public boolean handleACK(PircBotX bot, ImmutableList<String> caps) throws CAPException {
    if (!PircbotxSaslCapabilityOffer.parse(caps).saslOffered()) return state.isTerminal();

    saslAcked = true;
    state = State.ACKED;

    chosenMechanism =
        mechanismSelector.choose(
            configuredMechanism, username, secret, capabilityOffer.offeredMechanismsUpper());
    if (chosenMechanism == null || chosenMechanism.isBlank()) {
      return fail(
          bot,
          "No usable SASL mechanism available (configured="
              + configuredMechanism
              + ", offered="
              + capabilityOffer.offeredMechanismsUpper()
              + ")");
    }

    log.info("[SASL] Starting SASL mechanism {}", chosenMechanism);
    bot.sendRaw().rawLine("AUTHENTICATE " + chosenMechanism);
    state = State.AUTH_SENT;
    return false;
  }

  @Override
  public boolean handleNAK(PircBotX bot, ImmutableList<String> caps) throws CAPException {
    if (!PircbotxSaslCapabilityOffer.parse(caps).saslOffered()) return state.isTerminal();
    return fail(bot, "Server NAK'd sasl capability");
  }

  @Override
  public boolean handleUnknown(PircBotX bot, String line) throws CAPException {
    if (state.isTerminal()) return true;
    if (!capabilityOffer.saslOffered() || !saslRequested || !saslAcked) return false;
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
    String response = scramConversation.nextResponse(digest, serverMsg);
    if (response != null) {
      sendAuthenticateResponse(bot, response);
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

package cafe.woden.ircclient.irc.pircbotx.capability;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.pircbotx.exception.CAPException;

/** Orchestrates the SCRAM request/response sequence over a stateful exchange. */
final class PircbotxScramSaslConversation {

  private final String username;
  private final String secret;
  private PircbotxScramSaslExchange exchange;

  PircbotxScramSaslConversation(String username, String secret) {
    this.username = Objects.toString(username, "");
    this.secret = Objects.toString(secret, "");
  }

  String nextResponse(String digest, String serverMessage) throws CAPException {
    if (exchange == null) {
      exchange = new PircbotxScramSaslExchange(digest, username, secret);
      return encode(exchange.clientFirstMessage());
    }

    if (!exchange.hasSeenServerFirst()) {
      exchange.onServerFirst(Objects.toString(serverMessage, ""));
      return encode(exchange.clientFinalMessage());
    }

    if (!exchange.hasSeenServerFinal()) {
      exchange.onServerFinal(Objects.toString(serverMessage, ""));
      return "";
    }

    return null;
  }

  private static String encode(String message) {
    return Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
  }
}

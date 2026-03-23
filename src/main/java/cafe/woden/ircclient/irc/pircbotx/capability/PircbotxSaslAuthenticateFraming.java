package cafe.woden.ircclient.irc.pircbotx.capability;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.pircbotx.exception.CAPException;

/** Handles IRCv3 SASL AUTHENTICATE chunk framing for inbound and outbound payloads. */
final class PircbotxSaslAuthenticateFraming {

  private static final int SASL_CHUNK_LEN = 400;

  private final StringBuilder inboundBase64 = new StringBuilder();

  Optional<byte[]> acceptServerPayload(String data) throws CAPException {
    String payload = Objects.toString(data, "").trim();
    if ("+".equals(payload)) {
      return Optional.of(finishServerMessage());
    }

    inboundBase64.append(payload);
    if (payload.length() == SASL_CHUNK_LEN) {
      return Optional.empty();
    }
    return Optional.of(finishServerMessage());
  }

  List<String> encodeClientResponse(String base64Payload) {
    String payload = Objects.toString(base64Payload, "");
    if (payload.isEmpty()) {
      return List.of("+");
    }

    List<String> chunks = new ArrayList<>();
    int idx = 0;
    while (idx < payload.length()) {
      int end = Math.min(payload.length(), idx + SASL_CHUNK_LEN);
      chunks.add(payload.substring(idx, end));
      idx = end;
    }
    if (payload.length() % SASL_CHUNK_LEN == 0) {
      chunks.add("+");
    }
    return List.copyOf(chunks);
  }

  private byte[] finishServerMessage() throws CAPException {
    if (inboundBase64.length() == 0) {
      return new byte[0];
    }

    String joined = inboundBase64.toString();
    inboundBase64.setLength(0);
    try {
      return Base64.getDecoder().decode(joined);
    } catch (IllegalArgumentException e) {
      throw new CAPException(
          CAPException.Reason.OTHER, "Invalid base64 from server during SASL", e);
    }
  }
}

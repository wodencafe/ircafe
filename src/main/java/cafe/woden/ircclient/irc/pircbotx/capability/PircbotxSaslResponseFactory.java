package cafe.woden.ircclient.irc.pircbotx.capability;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import org.pircbotx.exception.CAPException;

/** Builds mechanism-specific SASL response payloads for non-SCRAM exchanges. */
final class PircbotxSaslResponseFactory {

  String createPlain(String username, String secret) {
    String payload = "\0" + Objects.toString(username, "") + "\0" + Objects.toString(secret, "");
    return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  String createExternal(String username) {
    String normalizedUsername = Objects.toString(username, "");
    if (normalizedUsername.isBlank()) {
      return "";
    }
    return Base64.getEncoder().encodeToString(normalizedUsername.getBytes(StandardCharsets.UTF_8));
  }

  String createEcdsa(String secret, byte[] challenge) throws CAPException {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(Objects.toString(secret, "").trim());
      PrivateKey pk =
          KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
      Signature sig = Signature.getInstance("SHA256withECDSA");
      sig.initSign(pk);
      sig.update(challenge);
      byte[] signature = sig.sign();
      return Base64.getEncoder().encodeToString(signature);
    } catch (Exception e) {
      throw new CAPException(CAPException.Reason.OTHER, "Failed ECDSA SASL signing", e);
    }
  }
}

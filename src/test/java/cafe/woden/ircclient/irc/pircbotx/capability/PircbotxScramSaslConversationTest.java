package cafe.woden.ircclient.irc.pircbotx.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.pircbotx.exception.CAPException;

class PircbotxScramSaslConversationTest {

  @Test
  void completesScramConversationAndEmitsFinalEmptyResponse() throws Exception {
    PircbotxScramSaslConversation conversation =
        new PircbotxScramSaslConversation("user", "secret");

    String clientFirst = decode(conversation.nextResponse("SHA-256", ""));
    assertTrue(clientFirst.startsWith("n,,"));
    String clientFirstBare = clientFirst.substring(3);
    String clientNonce = extractField(clientFirstBare, "r");

    String saltB64 = Base64.getEncoder().encodeToString("salt".getBytes(StandardCharsets.UTF_8));
    String serverFirst = "r=" + clientNonce + "server,s=" + saltB64 + ",i=4096";
    String clientFinal = decode(conversation.nextResponse("SHA-256", serverFirst));
    String clientFinalWithoutProof = clientFinal.substring(0, clientFinal.indexOf(",p="));

    String authMessage = clientFirstBare + "," + serverFirst + "," + clientFinalWithoutProof;
    String serverFinal =
        "v="
            + Base64.getEncoder()
                .encodeToString(
                    serverSignature(
                        "secret".getBytes(StandardCharsets.UTF_8),
                        Base64.getDecoder().decode(saltB64),
                        4096,
                        authMessage,
                        "SHA-256"));

    assertEquals("", conversation.nextResponse("SHA-256", serverFinal));
    assertNull(conversation.nextResponse("SHA-256", serverFinal));
  }

  @Test
  void rejectsInvalidServerSignature() throws Exception {
    PircbotxScramSaslConversation conversation =
        new PircbotxScramSaslConversation("user", "secret");

    String clientFirst = decode(conversation.nextResponse("SHA-256", ""));
    String clientNonce = extractField(clientFirst.substring(3), "r");
    String saltB64 = Base64.getEncoder().encodeToString("salt".getBytes(StandardCharsets.UTF_8));

    conversation.nextResponse("SHA-256", "r=" + clientNonce + "server,s=" + saltB64 + ",i=4096");

    assertThrows(CAPException.class, () -> conversation.nextResponse("SHA-256", "v=invalid"));
  }

  private static String decode(String payload) {
    return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
  }

  private static String extractField(String message, String key) {
    for (String part : message.split(",")) {
      if (part.startsWith(key + "=")) {
        return part.substring((key + "=").length());
      }
    }
    throw new IllegalStateException("Missing field " + key + " in " + message);
  }

  private static byte[] serverSignature(
      byte[] password, byte[] salt, int iterations, String authMessage, String digest)
      throws Exception {
    byte[] saltedPassword = hi(password, salt, iterations, digest);
    byte[] serverKey = hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.UTF_8), digest);
    return hmac(serverKey, authMessage.getBytes(StandardCharsets.UTF_8), digest);
  }

  private static byte[] hi(byte[] password, byte[] salt, int iterations, String digest)
      throws Exception {
    byte[] salt1 = new byte[salt.length + 4];
    System.arraycopy(salt, 0, salt1, 0, salt.length);
    salt1[salt.length] = 0;
    salt1[salt.length + 1] = 0;
    salt1[salt.length + 2] = 0;
    salt1[salt.length + 3] = 1;

    byte[] u = hmac(password, salt1, digest);
    byte[] out = u.clone();
    for (int n = 1; n < iterations; n++) {
      u = hmac(password, u, digest);
      for (int i = 0; i < out.length; i++) {
        out[i] ^= u[i];
      }
    }
    return out;
  }

  private static byte[] hmac(byte[] key, byte[] msg, String digest) throws Exception {
    String algorithm = "Hmac" + digest.replace("-", "");
    Mac mac = Mac.getInstance(algorithm);
    mac.init(new SecretKeySpec(key, algorithm));
    return mac.doFinal(msg);
  }
}

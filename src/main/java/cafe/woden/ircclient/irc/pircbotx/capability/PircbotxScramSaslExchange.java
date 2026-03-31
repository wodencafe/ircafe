package cafe.woden.ircclient.irc.pircbotx.capability;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.pircbotx.exception.CAPException;

/** Stateful client-side SCRAM exchange for SASL authentication. */
final class PircbotxScramSaslExchange {
  private final String digest;
  private final String user;
  private final String pass;
  private final SecureRandom rng = new SecureRandom();

  private final String clientNonce;
  private final String clientFirstBare;
  private final String clientFirstMessage;
  private String serverFirstMessage;
  private String clientFinalWithoutProof;
  private String authMessage;

  private boolean serverFirstSeen;
  private boolean serverFinalSeen;

  private byte[] saltedPassword;
  private byte[] serverSignature;
  private String clientProofB64;
  private String serverSignatureB64;

  PircbotxScramSaslExchange(String digest, String user, String pass) {
    this.digest = digest;
    this.user = Objects.toString(user, "");
    this.pass = Objects.toString(pass, "");
    this.clientNonce = randomNonce();
    String escapedUser = saslEscapeUsername(this.user);
    this.clientFirstBare = "n=" + escapedUser + ",r=" + clientNonce;
    this.clientFirstMessage = "n,," + clientFirstBare;
  }

  String clientFirstMessage() {
    return clientFirstMessage;
  }

  boolean hasSeenServerFirst() {
    return serverFirstSeen;
  }

  boolean hasSeenServerFinal() {
    return serverFinalSeen;
  }

  void onServerFirst(String serverFirst) throws CAPException {
    this.serverFirstMessage = Objects.toString(serverFirst, "");
    Map<String, String> kv = parseScramKvs(serverFirstMessage);
    String nonce = kv.get("r");
    String saltB64 = kv.get("s");
    String iterationsRaw = kv.get("i");
    if (nonce == null || saltB64 == null || iterationsRaw == null) {
      throw new CAPException(CAPException.Reason.SASL_FAILED, "Invalid SCRAM server-first-message");
    }
    if (!nonce.startsWith(clientNonce)) {
      throw new CAPException(
          CAPException.Reason.SASL_FAILED, "SCRAM server nonce does not start with client nonce");
    }

    byte[] salt;
    try {
      salt = Base64.getDecoder().decode(saltB64);
    } catch (IllegalArgumentException e) {
      throw new CAPException(CAPException.Reason.SASL_FAILED, "Invalid SCRAM salt (base64)");
    }

    int iterations;
    try {
      iterations = Integer.parseInt(iterationsRaw);
    } catch (NumberFormatException e) {
      throw new CAPException(CAPException.Reason.SASL_FAILED, "Invalid SCRAM iteration count");
    }
    if (iterations <= 0) {
      throw new CAPException(CAPException.Reason.SASL_FAILED, "Invalid SCRAM iteration count");
    }

    this.clientFinalWithoutProof = "c=biws,r=" + nonce;
    this.authMessage = clientFirstBare + "," + serverFirstMessage + "," + clientFinalWithoutProof;

    this.saltedPassword = hi(pass.getBytes(StandardCharsets.UTF_8), salt, iterations, digest);
    byte[] clientKey = hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8), digest);
    byte[] storedKey = hash(clientKey, digest);
    byte[] clientSignature = hmac(storedKey, authMessage.getBytes(StandardCharsets.UTF_8), digest);
    byte[] clientProof = xor(clientKey, clientSignature);
    byte[] serverKey = hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.UTF_8), digest);
    this.serverSignature = hmac(serverKey, authMessage.getBytes(StandardCharsets.UTF_8), digest);

    this.clientProofB64 = Base64.getEncoder().encodeToString(clientProof);
    this.serverSignatureB64 = Base64.getEncoder().encodeToString(serverSignature);
    serverFirstSeen = true;
  }

  String clientFinalMessage() {
    return clientFinalWithoutProof + ",p=" + clientProofB64;
  }

  void onServerFinal(String serverFinal) throws CAPException {
    Map<String, String> kv = parseScramKvs(Objects.toString(serverFinal, ""));
    String err = kv.get("e");
    if (err != null && !err.isEmpty()) {
      throw new CAPException(CAPException.Reason.SASL_FAILED, "SCRAM error from server: " + err);
    }
    String verifier = kv.get("v");
    if (verifier == null) {
      throw new CAPException(CAPException.Reason.SASL_FAILED, "Missing SCRAM server signature");
    }
    if (!Objects.equals(serverSignatureB64, verifier)) {
      throw new CAPException(
          CAPException.Reason.SASL_FAILED, "SCRAM server signature verification failed");
    }
    serverFinalSeen = true;
  }

  private String randomNonce() {
    byte[] bytes = new byte[18];
    rng.nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static String saslEscapeUsername(String user) {
    return user.replace("=", "=3D").replace(",", "=2C");
  }

  private static Map<String, String> parseScramKvs(String msg) {
    Map<String, String> out = new TreeMap<>();
    if (msg == null) return out;
    for (String part : msg.split(",")) {
      int idx = part.indexOf('=');
      if (idx <= 0) continue;
      out.put(part.substring(0, idx), part.substring(idx + 1));
    }
    return out;
  }

  private static byte[] hi(byte[] password, byte[] salt, int iterations, String digest)
      throws CAPException {
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
      for (int j = 0; j < out.length; j++) {
        out[j] ^= u[j];
      }
    }
    return out;
  }

  private static byte[] hmac(byte[] key, byte[] msg, String digest) throws CAPException {
    try {
      String alg = "Hmac" + digest.replace("-", "");
      Mac mac = Mac.getInstance(alg);
      mac.init(new SecretKeySpec(key, alg));
      return mac.doFinal(msg);
    } catch (Exception e) {
      throw new CAPException(CAPException.Reason.OTHER, "HMAC failure", e);
    }
  }

  private static byte[] hash(byte[] in, String digest) throws CAPException {
    try {
      return java.security.MessageDigest.getInstance(digest).digest(in);
    } catch (Exception e) {
      throw new CAPException(CAPException.Reason.OTHER, "Digest failure", e);
    }
  }

  private static byte[] xor(byte[] a, byte[] b) {
    byte[] out = new byte[Math.min(a.length, b.length)];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) (a[i] ^ b[i]);
    }
    return out;
  }
}

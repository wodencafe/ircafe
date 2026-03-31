package cafe.woden.ircclient.irc.pircbotx.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PircbotxSaslResponseFactoryTest {

  private final PircbotxSaslResponseFactory responseFactory = new PircbotxSaslResponseFactory();

  @Test
  void plainEncodesUsernameAndSecret() {
    String response = responseFactory.createPlain("user", "secret");

    assertEquals(
        "\0user\0secret", new String(Base64.getDecoder().decode(response), StandardCharsets.UTF_8));
  }

  @Test
  void externalUsesEmptyResponseWhenUsernameIsBlank() {
    assertEquals("", responseFactory.createExternal(" "));
  }

  @Test
  void externalEncodesUsernameWhenPresent() {
    String response = responseFactory.createExternal("user");

    assertEquals("user", new String(Base64.getDecoder().decode(response), StandardCharsets.UTF_8));
  }

  @Test
  void ecdsaSignsChallengeWithProvidedPrivateKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    KeyPair keyPair = generator.generateKeyPair();
    byte[] challenge = "challenge".getBytes(StandardCharsets.UTF_8);

    String response =
        responseFactory.createEcdsa(
            Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()), challenge);

    byte[] signatureBytes = Base64.getDecoder().decode(response);
    Signature verifier = Signature.getInstance("SHA256withECDSA");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(challenge);
    assertTrue(verifier.verify(signatureBytes));
  }
}

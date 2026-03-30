package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorConnectionPolicyTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void validationTracksRequiredCoreFields() {
    ServerEditorConnectionPolicy.ConnectionValidation ircValidation =
        ServerEditorConnectionPolicy.validation(
            BACKEND_PROFILES.profileForBackendId("irc"), "", "", "abc", "");
    ServerEditorConnectionPolicy.ConnectionValidation matrixValidation =
        ServerEditorConnectionPolicy.validation(
            BACKEND_PROFILES.profileForBackendId("matrix"), "matrix", "example.org", "443", "");

    assertTrue(ircValidation.idBad());
    assertTrue(ircValidation.hostBad());
    assertTrue(ircValidation.portBad());
    assertTrue(ircValidation.nickBad());
    assertFalse(matrixValidation.nickBad());
  }

  @Test
  void parseConnectionNormalizesWhitespaceAndValidatesPort() {
    ServerEditorConnectionPolicy.ServerConnection connection =
        ServerEditorConnectionPolicy.parseConnection(" libera ", " irc.libera.chat ", " 6697 ");

    assertEquals("libera", connection.id());
    assertEquals("irc.libera.chat", connection.host());
    assertEquals(6697, connection.port());

    IllegalArgumentException portRangeError =
        assertThrows(
            IllegalArgumentException.class,
            () -> ServerEditorConnectionPolicy.parseConnection("id", "host", "70000"));
    assertEquals("Port must be 1-65535", portRangeError.getMessage());

    IllegalArgumentException portNumberError =
        assertThrows(
            IllegalArgumentException.class,
            () -> ServerEditorConnectionPolicy.parseConnection("id", "host", "abc"));
    assertEquals("Port must be a number", portNumberError.getMessage());
  }

  @Test
  void validateAndNormalizeNickUsesBackendRequirement() {
    String optionalNick =
        ServerEditorConnectionPolicy.validateAndNormalizeNick(
            BACKEND_PROFILES.profileForBackendId("matrix"), "");

    assertEquals("", optionalNick);

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ServerEditorConnectionPolicy.validateAndNormalizeNick(
                    BACKEND_PROFILES.profileForBackendId("irc"), " "));
    assertEquals("Nick is required", error.getMessage());
  }
}

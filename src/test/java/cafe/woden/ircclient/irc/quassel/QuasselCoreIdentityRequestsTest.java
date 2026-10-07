package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreIdentityRequestsTest {
  @Test
  void configuredNickSuppliesIdentityAndRetainsOriginalRealName() {
    Map<String, Object> payload =
        QuasselCoreIdentityRequests.defaultPayload(" Initial\tNick ", request("network"));
    assertEquals(-1, payload.get("identityId"));
    assertEquals("Initial_Nick", payload.get("identityName"));
    assertEquals(List.of("Initial_Nick"), payload.get("nicks"));
    assertEquals("Initial\tNick", payload.get("realName"));
    assertEquals("initial_nick", payload.get("ident"));
    assertEquals("Initial_Nick_away", payload.get("awayNick"));
    assertThrows(UnsupportedOperationException.class, () -> payload.put("identityId", 7));
    @SuppressWarnings("unchecked")
    List<String> nicks = (List<String>) payload.get("nicks");
    assertThrows(UnsupportedOperationException.class, () -> nicks.add("other"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankConfiguredNickFallsBackToSanitizedNetworkName(String nick) {
    Map<String, Object> payload =
        QuasselCoreIdentityRequests.defaultPayload(nick, request(" Network Name "));
    assertEquals(List.of("Network_Name"), payload.get("nicks"));
    assertEquals("Network_Name", payload.get("realName"));
    assertEquals("network_name", payload.get("ident"));
  }

  @Test
  void missingNickAndRequestUseDefaultIdentity() {
    Map<String, Object> payload = QuasselCoreIdentityRequests.defaultPayload(null, null);
    assertEquals("ircafe", payload.get("identityName"));
    assertEquals(List.of("ircafe"), payload.get("nicks"));
    assertEquals("ircafe", payload.get("realName"));
  }

  @Test
  void defaultIdentityKeepsAwayAndReasonSettingsDisabled() {
    Map<String, Object> payload = QuasselCoreIdentityRequests.defaultPayload("nick", null);
    for (String key :
        List.of(
            "awayNickEnabled",
            "awayReasonEnabled",
            "autoAwayEnabled",
            "autoAwayReasonEnabled",
            "detachAwayEnabled",
            "detachAwayReasonEnabled")) {
      assertEquals(false, payload.get(key), key);
    }
    for (String key :
        List.of(
            "awayReason",
            "autoAwayReason",
            "detachAwayReason",
            "kickReason",
            "partReason",
            "quitReason")) {
      assertEquals("", payload.get(key), key);
    }
    assertEquals(10, payload.get("autoAwayTime"));
    assertEquals(19, payload.size());
  }

  @Test
  void nicknameSanitizingReplacesEmbeddedNewlines() {
    assertEquals(
        List.of("First__Last"),
        QuasselCoreIdentityRequests.defaultPayload("First\r\nLast", null).get("nicks"));
  }

  @ParameterizedTest
  @CsvSource({"' User Name ', User_Name", "'Nick@Host', Nick@Host", "'MiXeD', MiXeD"})
  void nicknameSanitizingPreservesNonWhitespaceCharacters(String input, String expected) {
    assertEquals(
        List.of(expected), QuasselCoreIdentityRequests.defaultPayload(input, null).get("nicks"));
  }

  private static QuasselCoreNetworkCreateRequest request(String name) {
    return new QuasselCoreNetworkCreateRequest(
        name, "irc.example.net", 6667, false, "", true, null, List.of());
  }
}

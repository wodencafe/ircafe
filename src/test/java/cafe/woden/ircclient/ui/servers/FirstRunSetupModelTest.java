package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FirstRunSetupModelTest {
  @Test
  void blankOptionalPagesCreateAnUnauthenticatedServerWithoutChannels() {
    var server =
        FirstRunSetupModel.build("network", "irc.example.org", "6697", true, "nick", "", "", "");
    assertEquals("irc.example.org", server.host());
    assertFalse(server.sasl().enabled());
    assertTrue(server.autoJoin().isEmpty());
  }

  @Test
  void savesAccountAndNormalizesChannels() {
    var server =
        FirstRunSetupModel.build(
            "network",
            "irc.example.org",
            "6697",
            true,
            "nick",
            " account ",
            "secret",
            "#one, #two\n#one &local");
    assertTrue(server.sasl().enabled());
    assertEquals("account", server.sasl().username());
    assertEquals("secret", server.sasl().password());
    assertEquals(List.of("#one", "#two", "&local"), server.autoJoin());
  }

  @Test
  void rejectsPartialCredentialsAndCredentialsOverPlaintext() {
    assertThrows(
        IllegalArgumentException.class, () -> FirstRunSetupModel.account(true, "account", ""));
    assertThrows(
        IllegalArgumentException.class, () -> FirstRunSetupModel.account(true, "", "secret"));
    assertThrows(
        IllegalArgumentException.class,
        () -> FirstRunSetupModel.account(false, "account", "secret"));
    assertNull(FirstRunSetupModel.account(false, "", ""));
  }

  @Test
  void rejectsInvalidConnectionAndChannelInput() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FirstRunSetupModel.build(
                "network", "irc.example.org", "70000", true, "nick", "", "", ""));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FirstRunSetupModel.build(
                "network", "irc.example.org", "6697", true, "bad\r\nnick", "", "", ""));
    assertThrows(
        IllegalArgumentException.class, () -> FirstRunSetupModel.channels("not-a-channel"));
    assertThrows(
        IllegalArgumentException.class, () -> FirstRunSetupModel.channels("#bad\0channel"));
    assertThrows(
        IllegalArgumentException.class,
        () -> FirstRunSetupModel.account(true, "account", "bad\r\nsecret"));
  }
}

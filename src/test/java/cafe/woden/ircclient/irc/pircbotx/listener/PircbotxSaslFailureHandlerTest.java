package cafe.woden.ircclient.irc.pircbotx.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxSaslFailureHandlerTest {

  @Test
  void parseFailureCodeRecognizesKnownSaslErrors() {
    PircbotxSaslFailureHandler handler =
        new PircbotxSaslFailureHandler(
            "libera", new PircbotxConnectionState("libera"), ignored -> {}, false);

    assertEquals(904, handler.parseFailureCode(":server 904 me :SASL authentication failed"));
    assertEquals(905, handler.parseFailureCode(":server 905 me :SASL message too long"));
    assertNull(handler.parseFailureCode(":server 900 me alice account :You are now logged in"));
  }

  @Test
  void handlePublishesErrorAndSuppressesReconnect() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxSaslFailureHandler handler =
        new PircbotxSaslFailureHandler("libera", conn, events::add, false);

    handler.handle(905, ":server 905 me :SASL message too long");

    assertEquals(
        "Login failed — SASL authentication failed (payload too long): SASL message too long",
        conn.disconnectReasonOverride());
    assertTrue(conn.autoReconnectSuppressed());
    assertEquals(1, events.size());
    IrcEvent.Error error = assertInstanceOf(IrcEvent.Error.class, events.getFirst().event());
    assertEquals(conn.disconnectReasonOverride(), error.message());
  }

  @Test
  void handleRespectsExistingDisconnectReason() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.overrideDisconnectReason("already failed");
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxSaslFailureHandler handler =
        new PircbotxSaslFailureHandler("libera", conn, events::add, false);

    handler.handle(904, ":server 904 me :SASL authentication failed");

    assertEquals("already failed", conn.disconnectReasonOverride());
    assertTrue(conn.autoReconnectSuppressed());
    assertTrue(events.isEmpty());
  }
}

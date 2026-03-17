package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PircbotxIsupportObserverTest {

  @Test
  void observeAppliesTokensAndEmitsDerivedFeatures() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    RecordingIsupportState state = new RecordingIsupportState();
    List<ServerIrcEvent> events = new ArrayList<>();
    AtomicReference<String> sojuNetId = new AtomicReference<>();
    PircbotxIsupportObserver observer =
        new PircbotxIsupportObserver("libera", conn, state, events::add, sojuNetId::set);

    observer.observe(
        ":server 005 me PREFIX=(qaohv)!&@%+ CHANTYPES=# MONITOR=250 WHOX "
            + "CLIENTTAGDENY=*,-typing BOUNCER_NETID=123 :are supported");

    assertEquals("(qaohv)!&@%+", state.tokens().get("PREFIX"));
    assertEquals("#", state.tokens().get("CHANTYPES"));
    assertEquals("250", state.tokens().get("MONITOR"));
    assertEquals("", state.tokens().get("WHOX"));
    assertEquals("*,-typing", state.tokens().get("CLIENTTAGDENY"));
    assertEquals("123", state.tokens().get("BOUNCER_NETID"));
    assertEquals("123", sojuNetId.get());
    assertTrue(conn.monitorSupported.get());
    assertEquals(250L, conn.monitorMaxTargets.get());
    assertTrue(conn.typingClientTagPolicyKnown.get());
    assertTrue(conn.typingClientTagAllowed.get());

    assertEquals(1, events.size());
    IrcEvent.WhoxSupportObserved whox =
        assertInstanceOf(IrcEvent.WhoxSupportObserved.class, events.getFirst().event());
    assertTrue(whox.supported());
  }

  @Test
  void observeAppliesMonitorRemovalAndTypingDenial() {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    conn.monitorSupported.set(true);
    conn.monitorMaxTargets.set(250L);

    RecordingIsupportState state = new RecordingIsupportState();
    state.tokens().put("MONITOR", "250");

    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxIsupportObserver observer =
        new PircbotxIsupportObserver("libera", conn, state, events::add, ignored -> {});

    observer.observe(":server 005 me -MONITOR CLIENTTAGDENY=typing :are supported");

    assertNull(state.tokens().get("MONITOR"));
    assertTrue(events.isEmpty());
    assertEquals(0L, conn.monitorMaxTargets.get());
    assertEquals(false, conn.monitorSupported.get());
    assertTrue(conn.typingClientTagPolicyKnown.get());
    assertEquals(false, conn.typingClientTagAllowed.get());
  }

  private static final class RecordingIsupportState implements ServerIsupportStatePort {
    private final Map<String, String> tokens = new LinkedHashMap<>();

    Map<String, String> tokens() {
      return tokens;
    }

    @Override
    public void applyIsupportToken(String serverId, String tokenName, String tokenValue) {
      String key = tokenName == null ? "" : tokenName.trim().toUpperCase(java.util.Locale.ROOT);
      if (key.isEmpty()) return;
      if (tokenValue == null) {
        tokens.remove(key);
      } else {
        tokens.put(key, tokenValue);
      }
    }

    @Override
    public ModeVocabulary vocabularyForServer(String serverId) {
      return ModeVocabulary.fallback();
    }

    @Override
    public void clearServer(String serverId) {
      tokens.clear();
    }
  }
}

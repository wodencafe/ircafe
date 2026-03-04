package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PresenceEventTest {

  @Test
  void nickDisplayTextUsesReadablePhrase() {
    PresenceEvent event = PresenceEvent.nick("oldNick", "newNick");

    assertEquals("oldNick is now known as newNick", event.displayText());
  }
}

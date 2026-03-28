package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PresenceEventTest {

  @Test
  void nickDisplayTextUsesReadablePhrase() {
    PresenceEvent event = PresenceEvent.nick("oldNick", "newNick");

    assertEquals("oldNick is now known as newNick", event.displayText());
  }

  @Test
  void joinDisplayTextIncludesArrowAndRenderedHostmask() {
    PresenceEvent event = PresenceEvent.join("quodlibet", "quodlibet!~openSUSE@user/quodlibet");

    assertEquals(
        "--> quodlibet (~openSUSE@user/quodlibet) has joined this channel.", event.displayText());
  }

  @Test
  void joinDisplayTextFallsBackWhenHostmaskIsUnknown() {
    PresenceEvent event = PresenceEvent.join("quodlibet");

    assertEquals("--> quodlibet has joined this channel.", event.displayText());
  }

  @Test
  void partDisplayTextIncludesArrowHostmaskAndReason() {
    PresenceEvent event =
        PresenceEvent.part("quodlibet", "bye", "quodlibet!~openSUSE@user/quodlibet");

    assertEquals(
        "<-- quodlibet (~openSUSE@user/quodlibet) has left this channel (bye).",
        event.displayText());
  }

  @Test
  void quitDisplayTextIncludesArrowHostmaskAndReason() {
    PresenceEvent event =
        PresenceEvent.quit("quodlibet", "Ping timeout", "quodlibet!~openSUSE@user/quodlibet");

    assertEquals(
        "<-- quodlibet (~openSUSE@user/quodlibet) has quit IRC (Ping timeout).",
        event.displayText());
  }
}

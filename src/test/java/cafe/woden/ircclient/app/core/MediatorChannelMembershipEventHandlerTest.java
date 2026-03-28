package cafe.woden.ircclient.app.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MediatorChannelMembershipEventHandlerTest {

  @Test
  void handleUserJoinedChannelUsesLearnedHostmaskInPresenceText() {
    UiPort ui = mock(UiPort.class);
    MediatorChannelMembershipEventHandler handler = newHandler(ui);
    MediatorChannelMembershipEventHandler.Callbacks callbacks =
        mock(MediatorChannelMembershipEventHandler.Callbacks.class);
    when(callbacks.learnedHostmaskForNick("libera", "quodlibet"))
        .thenReturn("quodlibet!~openSUSE@user/quodlibet");

    handler.handleUserJoinedChannel(
        callbacks, "libera", new IrcEvent.UserJoinedChannel(Instant.now(), "#ircafe", "quodlibet"));

    ArgumentCaptor<PresenceEvent> presence = ArgumentCaptor.forClass(PresenceEvent.class);
    verify(ui).appendPresence(eq(new TargetRef("libera", "#ircafe")), presence.capture());
    assertEquals(
        "--> quodlibet (~openSUSE@user/quodlibet) has joined this channel.",
        presence.getValue().displayText());
  }

  @Test
  void handleUserPartedChannelUsesLearnedHostmaskInPresenceText() {
    UiPort ui = mock(UiPort.class);
    MediatorChannelMembershipEventHandler handler = newHandler(ui);
    MediatorChannelMembershipEventHandler.Callbacks callbacks =
        mock(MediatorChannelMembershipEventHandler.Callbacks.class);
    when(callbacks.learnedHostmaskForNick("libera", "quodlibet"))
        .thenReturn("quodlibet!~openSUSE@user/quodlibet");

    handler.handleUserPartedChannel(
        callbacks,
        "libera",
        new IrcEvent.UserPartedChannel(Instant.now(), "#ircafe", "quodlibet", "bye"));

    ArgumentCaptor<PresenceEvent> presence = ArgumentCaptor.forClass(PresenceEvent.class);
    verify(ui).appendPresence(eq(new TargetRef("libera", "#ircafe")), presence.capture());
    assertEquals(
        "<-- quodlibet (~openSUSE@user/quodlibet) has left this channel (bye).",
        presence.getValue().displayText());
  }

  @Test
  void handleUserQuitChannelUsesLearnedHostmaskInPresenceText() {
    UiPort ui = mock(UiPort.class);
    MediatorChannelMembershipEventHandler handler = newHandler(ui);
    MediatorChannelMembershipEventHandler.Callbacks callbacks =
        mock(MediatorChannelMembershipEventHandler.Callbacks.class);
    when(callbacks.learnedHostmaskForNick("libera", "quodlibet"))
        .thenReturn("quodlibet!~openSUSE@user/quodlibet");

    handler.handleUserQuitChannel(
        callbacks,
        "libera",
        new IrcEvent.UserQuitChannel(Instant.now(), "#ircafe", "quodlibet", "Ping timeout"));

    ArgumentCaptor<PresenceEvent> presence = ArgumentCaptor.forClass(PresenceEvent.class);
    verify(ui).appendPresence(eq(new TargetRef("libera", "#ircafe")), presence.capture());
    assertEquals(
        "<-- quodlibet (~openSUSE@user/quodlibet) has quit IRC (Ping timeout).",
        presence.getValue().displayText());
  }

  private static MediatorChannelMembershipEventHandler newHandler(UiPort ui) {
    return new MediatorChannelMembershipEventHandler(
        ui,
        mock(ConnectionCoordinator.class),
        mock(TargetCoordinator.class),
        mock(InboundModeEventHandler.class),
        mock(UserInfoEnrichmentService.class),
        mock(JoinRoutingPort.class),
        mock(IrcSessionRuntimeConfigPort.class),
        mock(ServerRegistry.class));
  }
}

package cafe.woden.ircclient.ui.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatBanListCoordinatorTest {

  @Test
  void beginAppendEndBuildsSnapshotRowsAndSummary() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatBanListCoordinator coordinator = new ChatBanListCoordinator(channelListPanel);

    coordinator.beginBanList("libera", "#ircafe");
    coordinator.appendBanListEntry(
        "libera", "#ircafe", "*!*@example.org", "ChanServ", 1_700_000_000L);
    coordinator.endBanList("libera", "#ircafe", "End of channel ban list");

    ChannelListPanel.BanListSnapshot snapshot = coordinator.snapshot("libera", "#ircafe");

    assertEquals(1, snapshot.entries().size());
    ChannelListPanel.BanListEntryRow row = snapshot.entries().getFirst();
    assertEquals("*!*@example.org", row.mask());
    assertEquals("ChanServ", row.setBy());
    assertEquals(Instant.ofEpochSecond(1_700_000_000L).toString(), row.setAt());
    assertEquals("End of channel ban list", snapshot.summary());
    verify(channelListPanel, times(3)).refreshOpenChannelDetails("libera", "#ircafe");
  }

  @Test
  void beginClearsPreviousSummaryForTarget() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatBanListCoordinator coordinator = new ChatBanListCoordinator(channelListPanel);

    coordinator.beginBanList("libera", "#ircafe");
    coordinator.appendBanListEntry("libera", "#ircafe", "*!*@old.example", "Oper", null);
    coordinator.endBanList("libera", "#ircafe", "Old summary");

    coordinator.beginBanList("libera", "#ircafe");
    coordinator.appendBanListEntry("libera", "#ircafe", "*!*@new.example", "Oper", null);

    ChannelListPanel.BanListSnapshot snapshot = coordinator.snapshot("libera", "#ircafe");

    assertEquals(1, snapshot.entries().size());
    assertEquals("*!*@new.example", snapshot.entries().getFirst().mask());
    assertEquals("", snapshot.summary());
  }

  @Test
  void invalidInputsAreIgnored() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatBanListCoordinator coordinator = new ChatBanListCoordinator(channelListPanel);

    coordinator.beginBanList("", "#ircafe");
    coordinator.beginBanList("libera", "ircafe");
    coordinator.appendBanListEntry("libera", "#ircafe", "", "Oper", 1L);
    coordinator.endBanList("libera", "ircafe", "ignored");

    assertEquals(
        ChannelListPanel.BanListSnapshot.empty(), coordinator.snapshot("libera", "#ircafe"));
    verifyNoInteractions(channelListPanel);
  }
}

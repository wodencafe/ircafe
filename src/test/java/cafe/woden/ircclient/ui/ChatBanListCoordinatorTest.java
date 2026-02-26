package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import java.time.Instant;
import java.util.List;
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

    List<String> snapshot = coordinator.snapshot("libera", "#ircafe");

    assertEquals(2, snapshot.size());
    assertTrue(snapshot.getFirst().contains("*!*@example.org"));
    assertTrue(snapshot.getFirst().contains("set by ChanServ"));
    assertTrue(snapshot.getFirst().contains(Instant.ofEpochSecond(1_700_000_000L).toString()));
    assertEquals("End of channel ban list", snapshot.get(1));
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

    List<String> snapshot = coordinator.snapshot("libera", "#ircafe");

    assertEquals(1, snapshot.size());
    assertTrue(snapshot.getFirst().contains("*!*@new.example"));
    assertTrue(snapshot.stream().noneMatch("Old summary"::equals));
  }

  @Test
  void invalidInputsAreIgnored() {
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    ChatBanListCoordinator coordinator = new ChatBanListCoordinator(channelListPanel);

    coordinator.beginBanList("", "#ircafe");
    coordinator.beginBanList("libera", "ircafe");
    coordinator.appendBanListEntry("libera", "#ircafe", "", "Oper", 1L);
    coordinator.endBanList("libera", "ircafe", "ignored");

    assertEquals(List.of(), coordinator.snapshot("libera", "#ircafe"));
    verifyNoInteractions(channelListPanel);
  }
}

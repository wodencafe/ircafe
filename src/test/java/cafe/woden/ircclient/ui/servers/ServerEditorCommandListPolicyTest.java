package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ServerEditorCommandListPolicyTest {

  @Test
  void autoJoinEntriesKeepsChannelsAndEncodesPrivateMessages() {
    List<String> entries =
        ServerEditorCommandListPolicy.autoJoinEntries("  #irc \n\n #java  ", " alice \n\n bob ");

    assertEquals(List.of("#irc", "#java", "query:alice", "query:bob"), entries);
  }

  @Test
  void performCommandsDropsBlankLines() {
    List<String> commands =
        ServerEditorCommandListPolicy.performCommands(" /msg NickServ identify \n\n /mode +B ");

    assertEquals(List.of("/msg NickServ identify", "/mode +B"), commands);
  }
}

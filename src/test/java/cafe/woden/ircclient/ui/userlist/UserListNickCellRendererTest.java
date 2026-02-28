package cafe.woden.ircclient.ui.userlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.NickColorService;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;

class UserListNickCellRendererTest {

  @Test
  void rendererAppliesPrefixesIgnoreMarkersAndFontStyle() {
    NickColorService nickColors = mock(NickColorService.class);
    when(nickColors.enabled()).thenReturn(false);
    UserListNickCellRenderer renderer =
        new UserListNickCellRenderer(
            nickColors,
            __ -> new UserListNickCellRenderer.IgnoreMark(true, true),
            __ -> 1f,
            __ -> false);

    NickInfo nickInfo =
        new NickInfo(
            "alice", "@", "alice!u@h", AwayState.AWAY, null, AccountState.LOGGED_IN, "acct", null);
    JLabel label =
        (JLabel) renderer.getListCellRendererComponent(new JList<>(), nickInfo, 0, false, false);

    assertTrue(label.getText().contains("@alice"));
    assertTrue(label.getText().contains("[IGN]"));
    assertTrue(label.getText().contains("[SOFT]"));
    assertTrue((label.getFont().getStyle() & Font.BOLD) != 0);
    assertTrue((label.getFont().getStyle() & Font.ITALIC) != 0);
    assertNotNull(label.getIcon());
  }

  @Test
  void rendererUsesNickColorWhenEnabled() {
    NickColorService nickColors = mock(NickColorService.class);
    when(nickColors.enabled()).thenReturn(true);
    when(nickColors.colorForNick(anyString(), any(Color.class), any(Color.class)))
        .thenReturn(Color.ORANGE);
    UserListNickCellRenderer renderer =
        new UserListNickCellRenderer(
            nickColors,
            __ -> new UserListNickCellRenderer.IgnoreMark(false, false),
            __ -> 0f,
            __ -> false);

    NickInfo nickInfo = new NickInfo("alice", "", "alice!u@h");
    JLabel label =
        (JLabel) renderer.getListCellRendererComponent(new JList<>(), nickInfo, 0, false, false);

    assertEquals(Color.ORANGE, label.getForeground());
  }
}

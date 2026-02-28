package cafe.woden.ircclient.ui.userlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.swing.DefaultListModel;
import org.junit.jupiter.api.Test;

class UserListNickListViewTest {

  @Test
  void minimumSizeAllowsZeroWidth() {
    DefaultListModel<NickInfo> model = new DefaultListModel<>();
    model.addElement(new NickInfo("alice", "", "alice!u@h"));
    UserListNickListView list = new UserListNickListView(model, __ -> "tip");

    Dimension minimum = list.getMinimumSize();

    assertEquals(0, minimum.width);
    assertTrue(minimum.height >= 0);
  }

  @Test
  void tooltipUsesRowHitTesting() {
    DefaultListModel<NickInfo> model = new DefaultListModel<>();
    model.addElement(new NickInfo("alice", "", "alice!u@h"));
    UserListNickListView list = new UserListNickListView(model, ni -> "tip-" + ni.nick());
    list.setFixedCellHeight(20);
    list.setFixedCellWidth(160);
    list.setSize(160, 60);
    list.doLayout();

    Rectangle cell = list.getCellBounds(0, 0);
    MouseEvent inside =
        new MouseEvent(
            list,
            MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),
            0,
            cell.x + 2,
            cell.y + 2,
            0,
            false);
    MouseEvent below =
        new MouseEvent(
            list,
            MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),
            0,
            cell.x + 2,
            Math.max(cell.y + cell.height + 10, 40),
            0,
            false);

    assertEquals("tip-alice", list.getToolTipText(inside));
    assertNull(list.getToolTipText(below));
  }
}

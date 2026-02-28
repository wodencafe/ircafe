package cafe.woden.ircclient.ui.userlist;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.DefaultListModel;
import javax.swing.JList;

public final class UserListNickListView extends JList<NickInfo> {
  private final Function<NickInfo, String> tooltipBuilder;

  public UserListNickListView(
      DefaultListModel<NickInfo> model, Function<NickInfo, String> tooltipBuilder) {
    super(model);
    this.tooltipBuilder = Objects.requireNonNull(tooltipBuilder, "tooltipBuilder");
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension d = super.getMinimumSize();
    int h = (d == null) ? 0 : Math.max(0, d.height);
    // Allow the users dock to collapse narrower without the list enforcing a wide minimum.
    return new Dimension(0, h);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public String getToolTipText(MouseEvent e) {
    if (e == null) return null;

    int index = locationToIndex(e.getPoint());
    if (index < 0 || index >= getModel().getSize()) return null;

    Rectangle row = getCellBounds(index, index);
    if (row == null || !row.contains(e.getPoint())) return null;

    NickInfo nickInfo = getModel().getElementAt(index);
    if (nickInfo == null) return null;
    return tooltipBuilder.apply(nickInfo);
  }
}

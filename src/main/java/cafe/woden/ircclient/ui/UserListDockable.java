package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.NickColorService;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

@Component
@Lazy
public class UserListDockable extends JPanel implements Dockable {
  public static final String ID = "users";

  private final DefaultListModel<String> model = new DefaultListModel<>();
  private final JList<String> list = new JList<>(model);

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();

  private final NickColorService nickColors;

  private TargetRef active = new TargetRef("default", "status");

  public UserListDockable(NickColorService nickColors) {
    super(new BorderLayout());

    this.nickColors = nickColors;

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // Deterministic, global per-nick coloring in the user list.
    final ListCellRenderer<? super String> baseRenderer = list.getCellRenderer();
    list.setCellRenderer((JList<? extends String> l, String value, int index, boolean isSelected, boolean cellHasFocus) -> {
      java.awt.Component c = baseRenderer.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
      if (c instanceof JLabel lbl && value != null && !value.isBlank()
          && nickColors != null && nickColors.enabled()) {
        String nick = stripNickPrefix(value);
        Color fg = nickColors.colorForNick(nick, lbl.getBackground(), lbl.getForeground());
        lbl.setForeground(fg);
      }
      return c;
    });
    add(new JScrollPane(list), BorderLayout.CENTER);

    // Double-click a nick to open a PM
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) return;

        int index = list.locationToIndex(e.getPoint());
        if (index < 0) return;

        Rectangle r = list.getCellBounds(index, index);
        if (r == null || !r.contains(e.getPoint())) return;

        // Only meaningful when we're viewing a channel user list.
        if (active == null || !active.isChannel()) return;

        String raw = model.getElementAt(index);
        String nick = stripNickPrefix(raw);
        if (nick.isBlank()) return;

        openPrivate.onNext(new PrivateMessageRequest(active.serverId(), nick));
      }
    });
  }

  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return openPrivate.onBackpressureBuffer();
  }

  public void setChannel(TargetRef target) {
    this.active = target;
  }

  public void setNicks(List<NickInfo> nicks) {
    model.clear();
    for (String n : nicks.stream().map(x -> x.prefix() + x.nick()).toList()) model.addElement(n);
  }

  public void setPlaceholder(String... nicks) {
    model.clear();
    for (String n : nicks) model.addElement(n);
  }

  public TargetRef getChannel() {
    return active;
  }

  public List<String> getNicksSnapshot() {
    List<String> out = new ArrayList<>(model.size());
    for (int i = 0; i < model.size(); i++) out.add(model.getElementAt(i));
    return out;
  }

  private String stripNickPrefix(String s) {
    if (s == null) return "";
    String v = s.trim();
    if (v.isEmpty()) return v;

    // PircBotX gives us prefixes like "@", "+", "~", etc.
    char c = v.charAt(0);
    if (c == '@' || c == '+' || c == '~' || c == '&' || c == '%') {
      return v.substring(1).trim();
    }
    return v;
  }

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Users"; }
}

package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.Dockable;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Component
@Lazy
public class UserListDockable extends JPanel implements Dockable {
  public static final String ID = "users";

  private final DefaultListModel<String> model = new DefaultListModel<>();
  private final JList<String> list = new JList<>(model);

  private String channel = "status";

  public UserListDockable() {
    super(new BorderLayout());

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    add(new JScrollPane(list), BorderLayout.CENTER);

    //Docking.registerDockable(this);
  }

  public void setChannel(String channel) {
    this.channel = channel;
    // TODO: Show N/A when not on a channel
  }

  /** Replace the nick list contents. */
  public void setNicks(List<NickInfo> nicks) {
    model.clear();
    for (String n : nicks.stream().map(x -> x.prefix() + x.nick()).toList()) model.addElement(n);
  }

  /** Convenience: quick placeholder list (useful while wiring). */
  public void setPlaceholder(String... nicks) {
    model.clear();
    for (String n : nicks) model.addElement(n);
  }

  public String getChannel() {
    return channel;
  }

  public List<String> getNicksSnapshot() {
    List<String> out = new ArrayList<>(model.size());
    for (int i = 0; i < model.size(); i++) out.add(model.getElementAt(i));
    return out;
  }

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Users"; }
}

package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ui.SwingEdt;
import com.formdev.flatlaf.FlatClientProperties;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * List/manage servers: add, edit, remove.
 */
public class ServersDialog extends JDialog {

  private static final Logger log = LoggerFactory.getLogger(ServersDialog.class);

  private final ServerRegistry serverRegistry;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final DefaultListModel<IrcProperties.Server> model = new DefaultListModel<>();
  private final JList<IrcProperties.Server> list = new JList<>(model);

  private final JButton addBtn = new JButton("Add…");
  private final JButton editBtn = new JButton("Edit…");
  private final JButton removeBtn = new JButton("Remove");
  private final JButton closeBtn = new JButton("Close");

  public ServersDialog(Window parent, ServerRegistry serverRegistry) {
    super(parent, "Servers", ModalityType.APPLICATION_MODAL);
    this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout(10, 10));
    ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JLabel title = new JLabel("Configured servers");
    title.putClientProperty(FlatClientProperties.STYLE, "font:+2");
    add(title, BorderLayout.NORTH);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new ServerCellRenderer());
    list.putClientProperty(FlatClientProperties.STYLE, "arc:12;" );

    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(520, 320));
    scroll.putClientProperty(FlatClientProperties.STYLE, "arc:12;");
    add(scroll, BorderLayout.CENTER);

    JPanel buttons = new JPanel();
    buttons.setLayout(new javax.swing.BoxLayout(buttons, javax.swing.BoxLayout.X_AXIS));
    buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

    // FlatLaf button types (keep as strings for compatibility across FlatLaf versions).
    addBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "default");
    editBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "default");
    removeBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "danger");
    closeBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "default");

    buttons.add(addBtn);
    buttons.add(javax.swing.Box.createHorizontalStrut(8));
    buttons.add(editBtn);
    buttons.add(javax.swing.Box.createHorizontalStrut(8));
    buttons.add(removeBtn);
    buttons.add(javax.swing.Box.createHorizontalGlue());
    buttons.add(closeBtn);
    add(buttons, BorderLayout.SOUTH);

    addBtn.addActionListener(e -> onAdd());
    editBtn.addActionListener(e -> onEdit());
    removeBtn.addActionListener(e -> onRemove());
    closeBtn.addActionListener(e -> dispose());
    list.addListSelectionListener(e -> updateEnabled());

    // initial load
    reload(serverRegistry.servers(), null);

    // live updates
    disposables.add(
        serverRegistry.updates()
            .observeOn(SwingEdt.scheduler())
            .subscribe(latest -> {
              String selectedId = Optional.ofNullable(list.getSelectedValue()).map(IrcProperties.Server::id).orElse(null);
              reload(latest, selectedId);
            }, err -> {
              log.error("[ircafe] Servers dialog updates stream error", err);
            })
    );

    pack();
    setLocationRelativeTo(parent);
    updateEnabled();
  }

  public void open() {
    setVisible(true);
  }

  @Override
  public void dispose() {
    disposables.dispose();
    super.dispose();
  }

  private void updateEnabled() {
    boolean has = list.getSelectedIndex() >= 0;
    editBtn.setEnabled(has);
    removeBtn.setEnabled(has);
  }

  private void reload(List<IrcProperties.Server> servers, String keepSelectedId) {
    model.clear();
    if (servers != null) {
      for (IrcProperties.Server s : servers) {
        if (s == null) continue;
        model.addElement(s);
      }
    }

    if (keepSelectedId != null && !keepSelectedId.isBlank()) {
      for (int i = 0; i < model.size(); i++) {
        IrcProperties.Server s = model.get(i);
        if (keepSelectedId.equalsIgnoreCase(s.id())) {
          list.setSelectedIndex(i);
          list.ensureIndexIsVisible(i);
          break;
        }
      }
    } else if (!model.isEmpty() && list.getSelectedIndex() < 0) {
      list.setSelectedIndex(0);
    }

    updateEnabled();
  }

  private void onAdd() {
    ServerEditorDialog dlg = new ServerEditorDialog(this, "Add Server", null);
    Optional<IrcProperties.Server> out = dlg.open();
    out.ifPresent(serverRegistry::upsert);
  }

  private void onEdit() {
    IrcProperties.Server cur = list.getSelectedValue();
    if (cur == null) return;

    String originalId = cur.id();
    ServerEditorDialog dlg = new ServerEditorDialog(this, "Edit Server", cur);
    Optional<IrcProperties.Server> out = dlg.open();
    if (out.isEmpty()) return;

    IrcProperties.Server next = out.get();
    String nextId = next.id();
    if (!Objects.equals(originalId, nextId) && serverRegistry.containsId(nextId)) {
      JOptionPane.showMessageDialog(this,
          "A server with id '" + nextId + "' already exists.",
          "Duplicate server id",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (!Objects.equals(originalId, nextId)) {
      serverRegistry.remove(originalId);
    }
    serverRegistry.upsert(next);
  }

  private void onRemove() {
    IrcProperties.Server cur = list.getSelectedValue();
    if (cur == null) return;

    int ok = JOptionPane.showConfirmDialog(
        this,
        "Remove server '" + cur.id() + "'?\n\nThis updates your runtime config file immediately.",
        "Remove server",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE
    );

    if (ok != JOptionPane.OK_OPTION) return;
    serverRegistry.remove(cur.id());
  }

  private static final class ServerCellRenderer extends DefaultListCellRenderer {
    @Override
    public java.awt.Component getListCellRendererComponent(
        JList<?> list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof IrcProperties.Server s) {
        String host = Objects.toString(s.host(), "");
        int port = s.port();
        String tls = s.tls() ? "TLS" : "plain";
        String nick = Objects.toString(s.nick(), "");
        setText("<html><b>" + escape(s.id()) + "</b>  <span style='opacity:0.75'>" + escape(host) + ":" + port + " · " + tls + "</span><br/>" +
            "<span style='opacity:0.75'>Nick:</span> " + escape(nick) + "</html>");
      }
      setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
      return this;
    }

    private static String escape(String s) {
      if (s == null) return "";
      return s.replace("&", "&amp;")
          .replace("<", "&lt;")
          .replace(">", "&gt;")
          .replace("\"", "&quot;");
    }
  }
}

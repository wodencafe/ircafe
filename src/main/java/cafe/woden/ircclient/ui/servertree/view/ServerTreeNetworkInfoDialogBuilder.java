package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.PircbotxBotFactory;
import cafe.woden.ircclient.ui.servertree.state.ServerRuntimeMetadata;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import net.miginfocom.swing.MigLayout;

/** Builds and shows the server "Network Info" dialog for {@link ServerTreeDockable}. */
public final class ServerTreeNetworkInfoDialogBuilder {

  public interface Context {
    ConnectionState connectionStateForServer(String serverId);

    boolean desiredOnlineForServer(String serverId);

    String prettyServerLabel(String serverId);

    String connectionDiagnosticsTipForServer(String serverId);

    void requestCapabilityToggle(String serverId, String capability, boolean enable);
  }

  private static final DateTimeFormatter SERVER_META_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter CAP_TRANSITION_TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

  private final RuntimeConfigStore runtimeConfig;
  private final Context context;

  public ServerTreeNetworkInfoDialogBuilder(RuntimeConfigStore runtimeConfig, Context context) {
    this.runtimeConfig = runtimeConfig;
    this.context = Objects.requireNonNull(context, "context");
  }

  public void open(Component ownerComponent, String serverId, ServerRuntimeMetadata metadata) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || metadata == null) return;

    Window owner = ownerComponent == null ? null : SwingUtilities.getWindowAncestor(ownerComponent);
    String title = "Network Info - " + context.prettyServerLabel(sid);

    JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

    JPanel body =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[][grow,fill]"));
    body.add(buildNetworkSummaryPanel(sid, metadata), "growx");

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Overview", buildOverviewTab(sid, metadata));
    tabs.addTab(
        "Capabilities (" + metadata.ircv3Caps.size() + ")",
        buildCapabilitiesInfoPanel(sid, metadata));
    tabs.addTab("ISUPPORT (" + metadata.isupport.size() + ")", buildIsupportInfoPanel(metadata));
    body.add(tabs, "grow, push");

    JScrollPane bodyScroll =
        new JScrollPane(
            body,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    bodyScroll.setBorder(BorderFactory.createEmptyBorder());
    bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
    bodyScroll.setPreferredSize(new Dimension(820, 470));

    JButton close = new JButton("Close");
    close.addActionListener(ev -> dialog.dispose());
    JPanel actions = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill][]", "[]"));
    actions.add(new JLabel(""), "growx");
    actions.add(close, "tag ok");

    JPanel content =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[grow,fill][]"));
    content.add(bodyScroll, "grow, push");
    content.add(actions, "growx");

    dialog.setContentPane(content);
    dialog.getRootPane().setDefaultButton(close);
    dialog.pack();
    int width = Math.max(820, dialog.getWidth());
    int height = Math.max(500, Math.min(560, dialog.getHeight()));
    dialog.setSize(width, height);
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private JPanel buildNetworkSummaryPanel(String serverId, ServerRuntimeMetadata metadata) {
    JPanel panel =
        new JPanel(new MigLayout("insets 8, fillx, wrap 2", "[grow,fill][right]", "[]4[]"));
    panel.setBorder(BorderFactory.createTitledBorder("Summary"));

    ConnectionState state = context.connectionStateForServer(serverId);
    boolean desired = context.desiredOnlineForServer(serverId);

    JLabel title = new JLabel(context.prettyServerLabel(serverId));
    Font base = title.getFont();
    if (base != null) {
      title.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1.5f));
    }
    panel.add(title, "growx");
    panel.add(new JLabel("State: " + ServerTreeConnectionStateViewModel.stateLabel(state)));

    String endpoint = formatConnectedEndpoint(metadata.connectedHost, metadata.connectedPort);
    String nick = fallbackInfoValue(metadata.nick);
    panel.add(new JLabel("Network ID: " + serverId), "span 2, growx");
    panel.add(
        new JLabel(
            "Endpoint: "
                + endpoint
                + "    Nick: "
                + nick
                + "    Intent: "
                + ServerTreeConnectionStateViewModel.desiredIntentLabel(desired)),
        "span 2, growx");
    return panel;
  }

  private JComponent buildOverviewTab(String serverId, ServerRuntimeMetadata metadata) {
    JPanel overview =
        new JPanel(new MigLayout("insets 8, fill, wrap 2", "[grow,fill]12[grow,fill]", "[top]"));
    overview.add(buildConnectionInfoPanel(serverId, metadata), "grow");
    overview.add(buildServerInfoPanel(metadata), "grow");
    return overview;
  }

  private JPanel buildConnectionInfoPanel(String serverId, ServerRuntimeMetadata metadata) {
    JPanel panel = new JPanel(new MigLayout("insets 8, fillx, wrap 2", "[right][grow,fill]"));
    panel.setBorder(BorderFactory.createTitledBorder("Connection"));

    ConnectionState state = context.connectionStateForServer(serverId);
    boolean desired = context.desiredOnlineForServer(serverId);

    addInfoRow(panel, "Network ID", serverId);
    addInfoRow(panel, "Display", context.prettyServerLabel(serverId));
    addInfoRow(panel, "State", ServerTreeConnectionStateViewModel.stateLabel(state));
    addInfoRow(panel, "Intent", ServerTreeConnectionStateViewModel.desiredIntentLabel(desired));
    addInfoRow(
        panel,
        "Connected endpoint",
        formatConnectedEndpoint(metadata.connectedHost, metadata.connectedPort));
    addInfoRow(panel, "Current nick", fallbackInfoValue(metadata.nick));
    addInfoRow(
        panel,
        "Connected at",
        metadata.connectedAt == null
            ? "(unknown)"
            : SERVER_META_TIME_FMT.format(metadata.connectedAt));

    String diagnostics = context.connectionDiagnosticsTipForServer(serverId).trim();
    if (!diagnostics.isEmpty()) {
      addInfoRow(panel, "Diagnostics", diagnostics);
    }
    return panel;
  }

  private JPanel buildServerInfoPanel(ServerRuntimeMetadata metadata) {
    JPanel panel = new JPanel(new MigLayout("insets 8, fillx, wrap 2", "[right][grow,fill]"));
    panel.setBorder(BorderFactory.createTitledBorder("Server"));
    addInfoRow(panel, "Server name", fallbackInfoValue(metadata.serverName));
    addInfoRow(panel, "Version", fallbackInfoValue(metadata.serverVersion));
    addInfoRow(panel, "User modes", fallbackInfoValue(metadata.userModes));
    addInfoRow(panel, "Channel modes", fallbackInfoValue(metadata.channelModes));
    return panel;
  }

  private JPanel buildCapabilitiesInfoPanel(String serverId, ServerRuntimeMetadata metadata) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 8, fill, wrap 1", "[grow,fill]", "[]6[]6[]6[grow,fill]6[grow,fill]"));
    panel.add(buildCapabilityCountsRow(metadata), "growx");
    panel.add(new JLabel(capabilityStatusSummary(metadata)), "growx");
    panel.add(
        new JLabel("Toggle Requested to send CAP REQ now and persist the startup preference."),
        "growx");

    TreeMap<String, ServerRuntimeMetadata.CapabilityState> sortedObserved =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    sortedObserved.putAll(metadata.ircv3Caps);
    java.util.TreeSet<String> allCapabilities =
        new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    allCapabilities.addAll(sortedObserved.keySet());
    for (String capability : PircbotxBotFactory.requestableCapabilities()) {
      String normalized =
          Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
      if (!normalized.isEmpty()) {
        allCapabilities.add(normalized);
      }
    }

    if (allCapabilities.isEmpty()) {
      panel.add(new JLabel("No IRCv3 capabilities observed yet."), "grow");
      panel.add(buildCapabilityTransitionsPanel(metadata), "grow");
      return panel;
    }

    Object[][] rows = new Object[allCapabilities.size()][4];
    int idx = 0;
    for (String capName : allCapabilities) {
      ServerRuntimeMetadata.CapabilityState state = sortedObserved.get(capName);
      rows[idx][0] = capName;
      rows[idx][1] = state == null ? "(not seen)" : state.label;
      rows[idx][2] = isCapabilityRequested(capName);
      rows[idx][3] = fallbackInfoValue(metadata.ircv3CapLastSubcommand.get(capName));
      idx++;
    }

    DefaultTableModel model =
        new DefaultTableModel(rows, new String[] {"Capability", "State", "Requested", "Last CAP"}) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return column == 2;
          }

          @Override
          public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 2) {
              return Boolean.class;
            }
            return String.class;
          }
        };
    model.addTableModelListener(
        event -> {
          if (event == null || event.getType() != TableModelEvent.UPDATE) {
            return;
          }
          if (event.getColumn() != 2 && event.getColumn() != TableModelEvent.ALL_COLUMNS) {
            return;
          }
          int from = Math.max(0, event.getFirstRow());
          int to = Math.max(from, event.getLastRow());
          for (int row = from; row <= to; row++) {
            String cap =
                Objects.toString(model.getValueAt(row, 0), "")
                    .trim()
                    .toLowerCase(java.util.Locale.ROOT);
            if (cap.isEmpty()) {
              continue;
            }
            boolean enable = Boolean.TRUE.equals(model.getValueAt(row, 2));
            context.requestCapabilityToggle(serverId, cap, enable);
          }
        });

    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(true);
    table.setColumnSelectionAllowed(false);
    table.getTableHeader().setReorderingAllowed(false);
    JScrollPane scroll = new JScrollPane(table);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    panel.add(scroll, "grow");
    panel.add(buildCapabilityTransitionsPanel(metadata), "grow");
    return panel;
  }

  private JComponent buildCapabilityTransitionsPanel(ServerRuntimeMetadata metadata) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[][grow,fill]"));
    panel.setBorder(BorderFactory.createTitledBorder("Recent CAP transitions"));
    if (metadata.ircv3CapTransitions.isEmpty()) {
      panel.add(new JLabel("No CAP transitions observed yet."), "growx");
      return panel;
    }

    int size = metadata.ircv3CapTransitions.size();
    int start = Math.max(0, size - 80);
    Object[][] rows = new Object[size - start][4];
    int out = 0;
    for (int i = size - 1; i >= start; i--) {
      ServerRuntimeMetadata.CapabilityTransition transition = metadata.ircv3CapTransitions.get(i);
      rows[out][0] = CAP_TRANSITION_TIME_FMT.format(transition.at());
      rows[out][1] = transition.subcommand();
      rows[out][2] = transition.capability();
      rows[out][3] = transition.state().label;
      out++;
    }

    JTable table = buildReadOnlyTable(new String[] {"Time", "CAP", "Capability", "State"}, rows);
    JScrollPane scroll = new JScrollPane(table);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    panel.add(scroll, "grow");
    return panel;
  }

  private JPanel buildIsupportInfoPanel(ServerRuntimeMetadata metadata) {
    JPanel panel =
        new JPanel(new MigLayout("insets 8, fill, wrap 1", "[grow,fill]", "[grow,fill]"));
    if (metadata.isupport.isEmpty()) {
      panel.add(new JLabel("No ISUPPORT tokens observed yet."), "grow");
      return panel;
    }

    TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    sorted.putAll(metadata.isupport);
    Object[][] rows = new Object[sorted.size()][2];
    int idx = 0;
    for (Map.Entry<String, String> entry : sorted.entrySet()) {
      String val = Objects.toString(entry.getValue(), "");
      rows[idx][0] = entry.getKey();
      rows[idx][1] = val.isBlank() ? "(present)" : val;
      idx++;
    }

    JTable table = buildReadOnlyTable(new String[] {"Token", "Value"}, rows);
    JScrollPane scroll = new JScrollPane(table);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    panel.add(scroll, "grow");
    return panel;
  }

  private JPanel buildCapabilityCountsRow(ServerRuntimeMetadata metadata) {
    Map<ServerRuntimeMetadata.CapabilityState, Integer> counts =
        new java.util.EnumMap<>(ServerRuntimeMetadata.CapabilityState.class);
    for (ServerRuntimeMetadata.CapabilityState state :
        ServerRuntimeMetadata.CapabilityState.values()) {
      counts.put(state, 0);
    }
    for (ServerRuntimeMetadata.CapabilityState state : metadata.ircv3Caps.values()) {
      if (state == null) {
        continue;
      }
      counts.put(state, counts.getOrDefault(state, 0) + 1);
    }

    JPanel row =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 4",
                "[grow,fill]8[grow,fill]8[grow,fill]8[grow,fill]",
                "[]"));
    row.add(
        buildCountChip(
            "Enabled", counts.getOrDefault(ServerRuntimeMetadata.CapabilityState.ENABLED, 0)),
        "growx");
    row.add(
        buildCountChip(
            "Available", counts.getOrDefault(ServerRuntimeMetadata.CapabilityState.AVAILABLE, 0)),
        "growx");
    row.add(
        buildCountChip(
            "Disabled", counts.getOrDefault(ServerRuntimeMetadata.CapabilityState.DISABLED, 0)),
        "growx");
    row.add(
        buildCountChip(
            "Removed", counts.getOrDefault(ServerRuntimeMetadata.CapabilityState.REMOVED, 0)),
        "growx");
    return row;
  }

  private boolean isCapabilityRequested(String capability) {
    String cap = Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (cap.isEmpty()) {
      return false;
    }

    boolean requestable = false;
    for (String candidate : PircbotxBotFactory.requestableCapabilities()) {
      if (cap.equalsIgnoreCase(Objects.toString(candidate, "").trim())) {
        requestable = true;
        break;
      }
    }
    if (!requestable) {
      return false;
    }

    if (runtimeConfig == null) {
      return true;
    }
    try {
      return runtimeConfig.isIrcv3CapabilityEnabled(cap, true);
    } catch (Exception ignored) {
      return true;
    }
  }

  private String capabilityStatusSummary(ServerRuntimeMetadata metadata) {
    if (metadata == null || metadata.ircv3Caps.isEmpty()) {
      return "Requested but not enabled: (none)";
    }

    List<String> pending = new ArrayList<>();
    TreeMap<String, ServerRuntimeMetadata.CapabilityState> sorted =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    sorted.putAll(metadata.ircv3Caps);
    for (Map.Entry<String, ServerRuntimeMetadata.CapabilityState> entry : sorted.entrySet()) {
      String cap = entry.getKey();
      ServerRuntimeMetadata.CapabilityState state = entry.getValue();
      if (!isCapabilityRequested(cap)) {
        continue;
      }
      if (ServerRuntimeMetadata.CapabilityState.ENABLED.equals(state)) {
        continue;
      }
      String label = state == null ? "unknown" : state.label;
      pending.add(cap + " [" + label + "]");
    }

    if (pending.isEmpty()) {
      return "Requested but not enabled: (none)";
    }
    int limit = Math.min(8, pending.size());
    String joined = String.join(", ", pending.subList(0, limit));
    if (pending.size() > limit) {
      joined = joined + ", +" + (pending.size() - limit) + " more";
    }
    return "Requested but not enabled: " + joined;
  }

  private static JPanel buildCountChip(String label, int count) {
    JPanel chip = new JPanel(new MigLayout("insets 6, wrap 1", "[grow,fill]", "[]0[]"));
    Color border = UIManager.getColor("Separator.foreground");
    if (border == null) {
      border = UIManager.getColor("Component.borderColor");
    }
    if (border == null) {
      border = Color.GRAY;
    }
    chip.setBorder(BorderFactory.createLineBorder(withAlpha(border, 180)));

    JLabel countLabel = new JLabel(Integer.toString(Math.max(0, count)));
    Font font = countLabel.getFont();
    if (font != null) {
      countLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 1f));
    }
    JLabel textLabel = new JLabel(label);
    Color muted = UIManager.getColor("Label.disabledForeground");
    if (muted != null) {
      textLabel.setForeground(muted);
    }

    chip.add(countLabel, "alignx center");
    chip.add(textLabel, "alignx center");
    return chip;
  }

  private static JTable buildReadOnlyTable(String[] columns, Object[][] rows) {
    DefaultTableModel model =
        new DefaultTableModel(rows, columns) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };

    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(true);
    table.setRowSelectionAllowed(false);
    table.setColumnSelectionAllowed(false);
    table.getTableHeader().setReorderingAllowed(false);
    return table;
  }

  private static void addInfoRow(JPanel panel, String key, String value) {
    panel.add(new JLabel(key + ":"), "aligny top");
    JLabel valueLabel = new JLabel(fallbackInfoValue(value));
    valueLabel.setToolTipText(fallbackInfoValue(value));
    panel.add(valueLabel, "growx, wrap");
  }

  private static String fallbackInfoValue(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? "(unknown)" : v;
  }

  private static String formatConnectedEndpoint(String host, int port) {
    String h = Objects.toString(host, "").trim();
    if (h.isEmpty() && port <= 0) {
      return "(unknown)";
    }
    if (h.isEmpty()) {
      return ":" + port;
    }
    if (port <= 0) {
      return h;
    }
    return h + ":" + port;
  }

  private static Color withAlpha(Color color, int alpha) {
    Color base = color == null ? Color.GRAY : color;
    int a = Math.max(0, Math.min(255, alpha));
    return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
  }
}

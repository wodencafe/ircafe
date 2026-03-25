package cafe.woden.ircclient.ui.settings;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import net.miginfocom.swing.MigLayout;

final class HistoryStoragePanelSupport {
  private HistoryStoragePanelSupport() {}

  static JPanel buildPanel(LoggingControls logging, HistoryControls history) {
    JPanel panel =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[]8[grow,fill]"));
    panel.add(PreferencesDialog.tabTitle("History & Storage"), "growx, wmin 0, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Use the sub-tabs below to configure local chat logging, transcript scrolling/loading behavior, and remote history limits."),
        "growx, wmin 0, wrap");

    JTabbedPane subTabs = new DynamicTabbedPane();
    subTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    subTabs.addTab("Logging", PreferencesDialog.padSubTab(buildLoggingSubTab(logging)));
    subTabs.addTab(
        "Scrolling & Loading", PreferencesDialog.padSubTab(buildScrollingSubTab(history)));
    subTabs.addTab(
        "Remote & Limits", PreferencesDialog.padSubTab(buildRemoteLimitsSubTab(history)));

    panel.add(subTabs, "grow, push, wmin 0");
    return panel;
  }

  private static JPanel buildLoggingSubTab(LoggingControls logging) {
    JPanel tab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]8[]8[]"));
    tab.setOpaque(false);
    tab.add(logging.info, "growx, wmin 0, wrap");

    JPanel behavior =
        PreferencesDialog.captionPanel(
            "Logging behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    behavior.add(logging.enabled, "growx");
    behavior.add(logging.logSoftIgnored, "growx");
    behavior.add(logging.logPrivateMessages, "growx");
    behavior.add(logging.savePrivateMessageList, "growx, wrap");

    JPanel pmRow = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]8[grow,fill]", ""));
    pmRow.setOpaque(false);
    pmRow.add(new JLabel("PM list settings"));
    pmRow.add(logging.managePrivateMessageList, "alignx left");
    behavior.add(pmRow, "growx, wmin 0");
    tab.add(behavior, "growx, wmin 0, wrap");

    JPanel retention =
        PreferencesDialog.captionPanel(
            "Retention", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "");
    retention.add(logging.keepForever, "span 2, growx, wrap");
    retention.add(new JLabel("Retention (days)"));
    retention.add(logging.retentionDays, "w 110!");
    tab.add(retention, "growx, wmin 0, wrap");

    JPanel storage =
        PreferencesDialog.captionPanel(
            "Storage & writer", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "");
    storage.add(new JLabel("Writer queue max"));
    storage.add(logging.writerQueueMax, "w 130!, wrap");
    storage.add(new JLabel("Writer batch size"));
    storage.add(logging.writerBatchSize, "w 130!, wrap");
    storage.add(new JLabel("DB file base name"));
    storage.add(logging.dbBaseName, "w 260!, wrap");
    storage.add(new JLabel("DB location"));
    storage.add(logging.dbNextToConfig, "growx");
    tab.add(storage, "growx, wmin 0");

    return tab;
  }

  private static JPanel buildScrollingSubTab(HistoryControls history) {
    JPanel tab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]8[]8[]"));
    tab.setOpaque(false);
    tab.add(
        PreferencesDialog.helpText(
            "These controls tune transcript feel when opening targets and loading older lines."),
        "growx, wmin 0, wrap");

    JPanel opening =
        PreferencesDialog.captionPanel(
            "Open + page behavior", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "");
    opening.add(new JLabel("Initial load (lines)"));
    opening.add(history.initialLoadLines, "w 110!, wrap");
    opening.add(new JLabel("Page size (Load older)"));
    opening.add(history.pageSize, "w 110!, wrap");
    opening.add(new JLabel("Auto-load wheel debounce (ms)"));
    opening.add(history.autoLoadWheelDebounceMs, "w 110!, wrap");
    opening.add(new JLabel("Chat wheel smoothing"));
    opening.add(history.smoothWheelScrollingEnabled, "growx");
    tab.add(opening, "growx, wmin 0, wrap");

    JPanel loadOlder =
        PreferencesDialog.captionPanel(
            "Load older smoothing", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "");
    loadOlder.add(new JLabel("Chunk size (lines)"));
    loadOlder.add(history.loadOlderChunkSize, "w 110!, wrap");
    loadOlder.add(new JLabel("Chunk delay (ms)"));
    loadOlder.add(history.loadOlderChunkDelayMs, "w 110!, wrap");
    loadOlder.add(new JLabel("EDT budget (ms)"));
    loadOlder.add(history.loadOlderChunkEdtBudgetMs, "w 110!, wrap");
    loadOlder.add(new JLabel("Batch rendering"));
    loadOlder.add(history.deferRichTextDuringBatch, "growx, wrap");
    loadOlder.add(new JLabel("Scrolling behavior"));
    loadOlder.add(history.lockViewportDuringLoadOlder, "growx");
    tab.add(loadOlder, "growx, wmin 0, wrap");

    tab.add(
        PreferencesDialog.helpText(
            "Tip: if loading feels choppy, reduce chunk size and/or EDT budget, then increase chunk delay slightly."),
        "growx, wmin 0");
    return tab;
  }

  private static JPanel buildRemoteLimitsSubTab(HistoryControls history) {
    JPanel tab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]8[]8[]"));
    tab.setOpaque(false);
    tab.add(
        PreferencesDialog.helpText(
            "Configure remote history waits plus local in-memory caps for commands/transcripts."),
        "growx, wmin 0, wrap");

    JPanel remote =
        PreferencesDialog.captionPanel(
            "Remote history", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "");
    remote.add(new JLabel("Request timeout (sec)"));
    remote.add(history.remoteRequestTimeoutSeconds, "w 110!, wrap");
    remote.add(new JLabel("ZNC playback timeout (sec)"));
    remote.add(history.remoteZncPlaybackTimeoutSeconds, "w 110!, wrap");
    remote.add(new JLabel("ZNC playback window (min)"));
    remote.add(history.remoteZncPlaybackWindowMinutes, "w 110!");
    tab.add(remote, "growx, wmin 0, wrap");

    JPanel limits =
        PreferencesDialog.captionPanel(
            "Local limits", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "");
    limits.add(new JLabel("Input command history (max)"));
    limits.add(history.commandHistoryMaxSize, "w 110!, wrap");
    limits.add(new JLabel("Live transcript max lines/target"));
    limits.add(history.chatTranscriptMaxLinesPerTarget, "w 110!");
    tab.add(limits, "growx, wmin 0");
    return tab;
  }
}

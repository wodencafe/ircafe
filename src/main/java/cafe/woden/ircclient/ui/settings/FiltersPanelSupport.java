package cafe.woden.ircclient.ui.settings;

import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import net.miginfocom.swing.MigLayout;

final class FiltersPanelSupport {
  private FiltersPanelSupport() {}

  static JPanel buildPanel(FilterControls c) {
    JPanel panel =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[]6[grow,fill]"));

    panel.add(PreferencesDialog.tabTitle("Filters"), "growx, wrap");
    panel.add(PreferencesDialog.sectionTitle("Configuration"), "growx, wmin 0, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Filters only affect transcript rendering; messages are still logged."),
        "growx, wmin 0, wrap");

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("General", buildGeneralTab(c));
    tabs.addTab("Placeholders", buildPlaceholdersTab(c));
    tabs.addTab("History", buildHistoryTab(c));
    tabs.addTab("Overrides", buildOverridesTab(c));
    tabs.addTab("Rules", buildRulesTab(c));

    panel.add(tabs, "grow, push, wmin 0");
    return panel;
  }

  private static JComponent buildGeneralTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JPanel defaults =
        PreferencesDialog.captionPanel("Defaults", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    defaults.add(c.filtersEnabledByDefault, "growx, wrap");
    defaults.add(
        PreferencesDialog.helpText(
            "When disabled, rules and placeholders are ignored unless a scope override enables them."),
        "growx, wmin 0, wrap");
    panel.add(defaults, "growx, wmin 0");

    return panel;
  }

  private static JComponent buildPlaceholdersTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]8[]8[]"));
    panel.setOpaque(false);

    JPanel behavior =
        PreferencesDialog.captionPanel(
            "Placeholder behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    behavior.add(c.placeholdersEnabledByDefault, "growx, wrap");
    behavior.add(c.placeholdersCollapsedByDefault, "growx, wrap");

    JPanel previewRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    previewRow.add(new JLabel("Placeholder preview lines:"), "split 2");
    previewRow.add(c.placeholderPreviewLines, "w 80!");
    behavior.add(previewRow, "growx, wrap");
    panel.add(behavior, "growx, wmin 0, wrap");

    JPanel limits =
        PreferencesDialog.captionPanel(
            "Preview and run limits", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    JPanel runCapRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    runCapRow.add(new JLabel("Max hidden lines per run:"), "split 2");
    runCapRow.add(c.placeholderMaxLinesPerRun, "w 80!");
    limits.add(runCapRow, "growx, wrap");
    limits.add(
        PreferencesDialog.helpText(
            "0 = unlimited. Prevents a single placeholder from representing an enormous filtered run."),
        "growx, wmin 0, wrap");
    panel.add(limits, "growx, wmin 0, wrap");

    JPanel tooltip =
        PreferencesDialog.captionPanel(
            "Tooltip details", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    JPanel tooltipTagsRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    tooltipTagsRow.add(new JLabel("Tooltip tag limit:"), "split 2");
    tooltipTagsRow.add(c.placeholderTooltipMaxTags, "w 80!");
    tooltip.add(tooltipTagsRow, "growx, wrap");
    tooltip.add(
        PreferencesDialog.helpText("0 = hide tags in the tooltip (rule + count still shown)."),
        "growx, wmin 0, wrap");
    panel.add(tooltip, "growx, wmin 0, wrap");

    return panel;
  }

  private static JComponent buildHistoryTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JPanel history =
        PreferencesDialog.captionPanel(
            "History loading", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    history.add(c.historyPlaceholdersEnabledByDefault, "growx, wrap");

    JPanel historyCapRow = new JPanel(new MigLayout("insets 0", "[][grow]", ""));
    historyCapRow.add(new JLabel("History placeholder run cap per batch:"), "split 2");
    historyCapRow.add(c.historyPlaceholderMaxRunsPerBatch, "w 80!");
    history.add(historyCapRow, "growx, wrap");
    history.add(
        PreferencesDialog.helpText(
            "0 = unlimited. Limits how many filtered placeholder/hint runs appear per history load."),
        "growx, wmin 0, wrap");
    panel.add(history, "growx, wmin 0");

    return panel;
  }

  private static JComponent buildOverridesTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JScrollPane tableScroll = new JScrollPane(c.overridesTable);
    tableScroll.setPreferredSize(new Dimension(520, 220));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    buttons.add(c.addOverride);
    buttons.add(c.removeOverride);

    JPanel overrides =
        PreferencesDialog.captionPanel(
            "Scope overrides", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    overrides.add(
        PreferencesDialog.helpText("Overrides apply by scope pattern. Most specific match wins."),
        "growx, wmin 0, wrap");
    overrides.add(tableScroll, "growx, wrap 8");
    overrides.add(buttons, "growx, wrap 8");
    overrides.add(
        PreferencesDialog.helpText(
            "Tip: You can also manage overrides via /filter override ... and export with /filter export."),
        "growx, wmin 0, wrap");
    panel.add(overrides, "growx, wmin 0");

    return panel;
  }

  private static JComponent buildRulesTab(FilterControls c) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", ""));
    panel.setOpaque(false);

    JScrollPane rulesScroll = new JScrollPane(c.rulesTable);
    rulesScroll.setPreferredSize(new Dimension(760, 260));

    JPanel ruleButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    ruleButtons.add(c.addRule);
    ruleButtons.add(c.editRule);
    ruleButtons.add(c.deleteRule);
    ruleButtons.add(c.moveRuleUp);
    ruleButtons.add(c.moveRuleDown);

    JPanel rules =
        PreferencesDialog.captionPanel(
            "Filter rules", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    rules.add(
        PreferencesDialog.helpText(
            "Rules affect transcript rendering only (they do not prevent logging)."),
        "growx, wmin 0, wrap");
    rules.add(rulesScroll, "growx, wrap 8");
    rules.add(ruleButtons, "growx, wrap 8");
    rules.add(
        PreferencesDialog.helpText(
            "Tip: You can also manage rules via /filter add|del|set and export with /filter export."),
        "growx, wmin 0, wrap");
    panel.add(rules, "growx, wmin 0");

    return panel;
  }
}

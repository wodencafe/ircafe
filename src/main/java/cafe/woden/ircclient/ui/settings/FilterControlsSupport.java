package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

final class FilterControlsSupport {
  private FilterControlsSupport() {}

  static FilterControls buildControls(
      FilterSettings current,
      Window owner,
      List<AutoCloseable> closeables,
      FilterSettingsBus filterSettingsBus,
      RuntimeConfigStore runtimeConfig,
      ActiveTargetPort targetCoordinator,
      TranscriptRebuildService transcriptRebuildService) {
    Objects.requireNonNull(current);

    JCheckBox enabledByDefault = new JCheckBox("Enable filters by default");
    enabledByDefault.setSelected(current.filtersEnabledByDefault());

    JCheckBox placeholdersEnabledByDefault =
        new JCheckBox("Enable \"Filtered (N)\" placeholders by default");
    placeholdersEnabledByDefault.setSelected(current.placeholdersEnabledByDefault());

    JCheckBox placeholdersCollapsedByDefault = new JCheckBox("Collapse placeholders by default");
    placeholdersCollapsedByDefault.setSelected(current.placeholdersCollapsedByDefault());

    JSpinner previewLines =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(25, current.placeholderMaxPreviewLines())), 0, 25, 1));
    decorateSpinner(previewLines, closeables);

    JSpinner maxLinesPerRun =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(50_000, current.placeholderMaxLinesPerRun())), 0, 50_000, 50));
    maxLinesPerRun.setToolTipText(
        "Max hidden lines represented in a single placeholder run. 0 = unlimited.");
    decorateSpinner(maxLinesPerRun, closeables);

    JSpinner tooltipMaxTags =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(500, current.placeholderTooltipMaxTags())), 0, 500, 1));
    tooltipMaxTags.setToolTipText("Max tags shown in placeholder/hint tooltips. 0 = hide tags.");
    decorateSpinner(tooltipMaxTags, closeables);

    JCheckBox historyPlaceholdersEnabledByDefault =
        new JCheckBox("Show placeholders for filtered history loads");
    historyPlaceholdersEnabledByDefault.setSelected(current.historyPlaceholdersEnabledByDefault());
    historyPlaceholdersEnabledByDefault.setToolTipText(
        "If off, filtered lines loaded from history are silently hidden (no placeholder/hint rows).");

    JSpinner historyMaxRuns =
        new JSpinner(
            new SpinnerNumberModel(
                Math.max(0, Math.min(5_000, current.historyPlaceholderMaxRunsPerBatch())),
                0,
                5_000,
                1));
    historyMaxRuns.setToolTipText("Max placeholder runs per history load batch. 0 = unlimited.");
    decorateSpinner(historyMaxRuns, closeables);

    try {
      historyMaxRuns.setEnabled(historyPlaceholdersEnabledByDefault.isSelected());
      historyPlaceholdersEnabledByDefault.addActionListener(
          e -> historyMaxRuns.setEnabled(historyPlaceholdersEnabledByDefault.isSelected()));
    } catch (Exception ignored) {
    }

    FilterOverrideControls overrideControls =
        FilterOverrideControlsSupport.buildControls(current, owner);
    FilterRuleControls ruleControls =
        FilterRuleControlsSupport.buildControls(
            current,
            owner,
            filterSettingsBus,
            runtimeConfig,
            targetCoordinator,
            transcriptRebuildService,
            closeables);

    return new FilterControls(
        enabledByDefault,
        placeholdersEnabledByDefault,
        placeholdersCollapsedByDefault,
        previewLines,
        maxLinesPerRun,
        tooltipMaxTags,
        historyPlaceholdersEnabledByDefault,
        historyMaxRuns,
        overrideControls.model,
        overrideControls.table,
        overrideControls.add,
        overrideControls.remove,
        ruleControls.table,
        ruleControls.addRule,
        ruleControls.editRule,
        ruleControls.deleteRule,
        ruleControls.moveRuleUp,
        ruleControls.moveRuleDown);
  }

  private static void decorateSpinner(JSpinner spinner, List<AutoCloseable> closeables) {
    if (closeables != null) {
      try {
        closeables.add(MouseWheelDecorator.decorateNumberSpinner(spinner));
      } catch (Exception ignored) {
      }
    } else {
      try {
        MouseWheelDecorator.decorateNumberSpinner(spinner);
      } catch (Exception ignored) {
      }
    }
  }
}

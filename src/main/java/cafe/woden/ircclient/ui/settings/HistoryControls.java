package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;

final class HistoryControls {
  final JSpinner initialLoadLines;
  final JSpinner pageSize;
  final JSpinner autoLoadWheelDebounceMs;
  final JCheckBox smoothWheelScrollingEnabled;
  final JSpinner loadOlderChunkSize;
  final JSpinner loadOlderChunkDelayMs;
  final JSpinner loadOlderChunkEdtBudgetMs;
  final JCheckBox deferRichTextDuringBatch;
  final JCheckBox lockViewportDuringLoadOlder;
  final JSpinner remoteRequestTimeoutSeconds;
  final JSpinner remoteZncPlaybackTimeoutSeconds;
  final JSpinner remoteZncPlaybackWindowMinutes;
  final JSpinner commandHistoryMaxSize;
  final JSpinner chatTranscriptMaxLinesPerTarget;

  HistoryControls(
      JSpinner initialLoadLines,
      JSpinner pageSize,
      JSpinner autoLoadWheelDebounceMs,
      JCheckBox smoothWheelScrollingEnabled,
      JSpinner loadOlderChunkSize,
      JSpinner loadOlderChunkDelayMs,
      JSpinner loadOlderChunkEdtBudgetMs,
      JCheckBox deferRichTextDuringBatch,
      JCheckBox lockViewportDuringLoadOlder,
      JSpinner remoteRequestTimeoutSeconds,
      JSpinner remoteZncPlaybackTimeoutSeconds,
      JSpinner remoteZncPlaybackWindowMinutes,
      JSpinner commandHistoryMaxSize,
      JSpinner chatTranscriptMaxLinesPerTarget) {
    this.initialLoadLines = initialLoadLines;
    this.pageSize = pageSize;
    this.autoLoadWheelDebounceMs = autoLoadWheelDebounceMs;
    this.smoothWheelScrollingEnabled = smoothWheelScrollingEnabled;
    this.loadOlderChunkSize = loadOlderChunkSize;
    this.loadOlderChunkDelayMs = loadOlderChunkDelayMs;
    this.loadOlderChunkEdtBudgetMs = loadOlderChunkEdtBudgetMs;
    this.deferRichTextDuringBatch = deferRichTextDuringBatch;
    this.lockViewportDuringLoadOlder = lockViewportDuringLoadOlder;
    this.remoteRequestTimeoutSeconds = remoteRequestTimeoutSeconds;
    this.remoteZncPlaybackTimeoutSeconds = remoteZncPlaybackTimeoutSeconds;
    this.remoteZncPlaybackWindowMinutes = remoteZncPlaybackWindowMinutes;
    this.commandHistoryMaxSize = commandHistoryMaxSize;
    this.chatTranscriptMaxLinesPerTarget = chatTranscriptMaxLinesPerTarget;
  }
}

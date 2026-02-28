package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.api.TargetRef;
import java.awt.Component;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import javax.swing.text.StyledDocument;

/**
 * Logging-facing transcript/settings port so history services do not depend on UI implementation
 * types.
 */
public interface ChatHistoryTranscriptPort {

  StyledDocument document(TargetRef ref);

  OptionalLong earliestTimestampEpochMs(TargetRef ref);

  Component ensureLoadOlderMessagesControl(TargetRef ref);

  void setLoadOlderMessagesControlState(TargetRef ref, LoadOlderControlState state);

  void setLoadOlderMessagesControlHandler(TargetRef ref, BooleanSupplier onLoad);

  void beginHistoryInsertBatch(TargetRef ref);

  void endHistoryInsertBatch(TargetRef ref);

  int loadOlderInsertOffset(TargetRef ref);

  boolean hasContentAfterOffset(TargetRef ref, int offset);

  void ensureHistoryDivider(TargetRef ref, int insertAt, String labelText);

  void markHistoryDividerPending(TargetRef ref, String labelText);

  int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs);

  default int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return insertChatFromHistoryAt(ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs);
  }

  int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs);

  default int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return insertActionFromHistoryAt(ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs);
  }

  int insertNoticeFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  default int insertNoticeFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return insertNoticeFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  int insertStatusFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  int insertErrorFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  int insertPresenceFromHistoryAt(TargetRef ref, int insertAt, String text, long tsEpochMs);

  int insertSpoilerChatFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  void appendChatFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs);

  default void appendChatFromHistory(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendChatFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs);
  }

  void appendActionFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs);

  default void appendActionFromHistory(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendActionFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs);
  }

  void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  default void appendNoticeFromHistory(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendNoticeFromHistory(ref, from, text, tsEpochMs);
  }

  void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  void appendPresenceFromHistory(TargetRef ref, String text, long tsEpochMs);

  void appendSpoilerChatFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  int chatHistoryInitialLoadLines();

  int chatHistoryPageSize();

  int chatHistoryAutoLoadWheelDebounceMs();

  int chatHistoryLoadOlderChunkSize();

  int chatHistoryLoadOlderChunkDelayMs();

  int chatHistoryLoadOlderChunkEdtBudgetMs();

  boolean chatHistoryLockViewportDuringLoadOlder();

  int chatHistoryRemoteRequestTimeoutSeconds();

  int chatHistoryRemoteZncPlaybackTimeoutSeconds();

  int chatHistoryRemoteZncPlaybackWindowMinutes();
}

package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.api.TargetRef;
import java.awt.Component;
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

  int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs);

  int insertNoticeFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  int insertStatusFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  int insertErrorFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  int insertPresenceFromHistoryAt(TargetRef ref, int insertAt, String text, long tsEpochMs);

  int insertSpoilerChatFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs);

  void appendChatFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs);

  void appendActionFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs);

  void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  void appendPresenceFromHistory(TargetRef ref, String text, long tsEpochMs);

  void appendSpoilerChatFromHistory(TargetRef ref, String from, String text, long tsEpochMs);

  int chatHistoryInitialLoadLines();

  int chatHistoryPageSize();

  int chatHistoryAutoLoadWheelDebounceMs();

  int chatHistoryLoadOlderChunkSize();

  int chatHistoryLoadOlderChunkDelayMs();

  int chatHistoryRemoteRequestTimeoutSeconds();

  int chatHistoryRemoteZncPlaybackTimeoutSeconds();

  int chatHistoryRemoteZncPlaybackWindowMinutes();
}

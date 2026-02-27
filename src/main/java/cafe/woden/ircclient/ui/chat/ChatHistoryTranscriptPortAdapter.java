package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.logging.history.ChatHistoryTranscriptPort;
import cafe.woden.ircclient.logging.history.LoadOlderControlState;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import javax.swing.text.StyledDocument;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ChatHistoryTranscriptPortAdapter implements ChatHistoryTranscriptPort {

  private final ChatTranscriptStore transcripts;
  private final UiSettingsBus settingsBus;
  private final RuntimeConfigStore runtimeConfig;

  public ChatHistoryTranscriptPortAdapter(
      ChatTranscriptStore transcripts,
      UiSettingsBus settingsBus,
      RuntimeConfigStore runtimeConfig) {
    this.transcripts = transcripts;
    this.settingsBus = settingsBus;
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public StyledDocument document(TargetRef ref) {
    return transcripts.document(ref);
  }

  @Override
  public OptionalLong earliestTimestampEpochMs(TargetRef ref) {
    return transcripts.earliestTimestampEpochMs(ref);
  }

  @Override
  public java.awt.Component ensureLoadOlderMessagesControl(TargetRef ref) {
    return transcripts.ensureLoadOlderMessagesControl(ref);
  }

  @Override
  public void setLoadOlderMessagesControlState(TargetRef ref, LoadOlderControlState state) {
    transcripts.setLoadOlderMessagesControlState(ref, toUiState(state));
  }

  @Override
  public void setLoadOlderMessagesControlHandler(TargetRef ref, BooleanSupplier onLoad) {
    transcripts.setLoadOlderMessagesControlHandler(ref, onLoad);
  }

  @Override
  public void beginHistoryInsertBatch(TargetRef ref) {
    transcripts.beginHistoryInsertBatch(ref);
  }

  @Override
  public void endHistoryInsertBatch(TargetRef ref) {
    transcripts.endHistoryInsertBatch(ref);
  }

  @Override
  public int loadOlderInsertOffset(TargetRef ref) {
    return transcripts.loadOlderInsertOffset(ref);
  }

  @Override
  public boolean hasContentAfterOffset(TargetRef ref, int offset) {
    return transcripts.hasContentAfterOffset(ref, offset);
  }

  @Override
  public void ensureHistoryDivider(TargetRef ref, int insertAt, String labelText) {
    transcripts.ensureHistoryDivider(ref, insertAt, labelText);
  }

  @Override
  public void markHistoryDividerPending(TargetRef ref, String labelText) {
    transcripts.markHistoryDividerPending(ref, labelText);
  }

  @Override
  public int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs) {
    return transcripts.insertChatFromHistoryAt(
        ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs);
  }

  @Override
  public int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs) {
    return transcripts.insertActionFromHistoryAt(
        ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs);
  }

  @Override
  public int insertNoticeFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return transcripts.insertNoticeFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  @Override
  public int insertStatusFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return transcripts.insertStatusFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  @Override
  public int insertErrorFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return transcripts.insertErrorFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  @Override
  public int insertPresenceFromHistoryAt(TargetRef ref, int insertAt, String text, long tsEpochMs) {
    return transcripts.insertPresenceFromHistoryAt(ref, insertAt, text, tsEpochMs);
  }

  @Override
  public int insertSpoilerChatFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return transcripts.insertSpoilerChatFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  @Override
  public void appendChatFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    transcripts.appendChatFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs);
  }

  @Override
  public void appendActionFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    transcripts.appendActionFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs);
  }

  @Override
  public void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    transcripts.appendNoticeFromHistory(ref, from, text, tsEpochMs);
  }

  @Override
  public void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    transcripts.appendStatusFromHistory(ref, from, text, tsEpochMs);
  }

  @Override
  public void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    transcripts.appendErrorFromHistory(ref, from, text, tsEpochMs);
  }

  @Override
  public void appendPresenceFromHistory(TargetRef ref, String text, long tsEpochMs) {
    transcripts.appendPresenceFromHistory(ref, text, tsEpochMs);
  }

  @Override
  public void appendSpoilerChatFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    transcripts.appendSpoilerChatFromHistory(ref, from, text, tsEpochMs);
  }

  @Override
  public int chatHistoryInitialLoadLines() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryInitialLoadLines() : 0;
  }

  @Override
  public int chatHistoryPageSize() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryPageSize() : 0;
  }

  @Override
  public int chatHistoryAutoLoadWheelDebounceMs() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryAutoLoadWheelDebounceMs() : 0;
  }

  @Override
  public int chatHistoryLoadOlderChunkSize() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryLoadOlderChunkSize() : 0;
  }

  @Override
  public int chatHistoryLoadOlderChunkDelayMs() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryLoadOlderChunkDelayMs() : 0;
  }

  @Override
  public int chatHistoryLoadOlderChunkEdtBudgetMs() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryLoadOlderChunkEdtBudgetMs() : 0;
  }

  @Override
  public boolean chatHistoryLockViewportDuringLoadOlder() {
    if (runtimeConfig == null) return true;
    return runtimeConfig.readChatHistoryLockViewportDuringLoadOlder(true);
  }

  @Override
  public int chatHistoryRemoteRequestTimeoutSeconds() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryRemoteRequestTimeoutSeconds() : 0;
  }

  @Override
  public int chatHistoryRemoteZncPlaybackTimeoutSeconds() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryRemoteZncPlaybackTimeoutSeconds() : 0;
  }

  @Override
  public int chatHistoryRemoteZncPlaybackWindowMinutes() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    return s != null ? s.chatHistoryRemoteZncPlaybackWindowMinutes() : 0;
  }

  private static LoadOlderMessagesComponent.State toUiState(LoadOlderControlState state) {
    if (state == null) return LoadOlderMessagesComponent.State.READY;
    return switch (state) {
      case READY -> LoadOlderMessagesComponent.State.READY;
      case LOADING -> LoadOlderMessagesComponent.State.LOADING;
      case EXHAUSTED -> LoadOlderMessagesComponent.State.EXHAUSTED;
      case UNAVAILABLE -> LoadOlderMessagesComponent.State.UNAVAILABLE;
    };
  }
}

package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.ChatHistoryRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for chat history runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigChatHistoryAdapter implements ChatHistoryRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigChatHistoryAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public boolean readChatSmoothWheelScrollingEnabled(boolean defaultValue) {
    return runtimeConfig.readChatSmoothWheelScrollingEnabled(defaultValue);
  }

  @Override
  public boolean readChatHistoryLockViewportDuringLoadOlder(boolean defaultValue) {
    return runtimeConfig.readChatHistoryLockViewportDuringLoadOlder(defaultValue);
  }

  @Override
  public void rememberChatHistoryInitialLoadLines(int lines) {
    runtimeConfig.rememberChatHistoryInitialLoadLines(lines);
  }

  @Override
  public void rememberChatHistoryPageSize(int pageSize) {
    runtimeConfig.rememberChatHistoryPageSize(pageSize);
  }

  @Override
  public void rememberChatHistoryAutoLoadWheelDebounceMs(int debounceMs) {
    runtimeConfig.rememberChatHistoryAutoLoadWheelDebounceMs(debounceMs);
  }

  @Override
  public void rememberChatSmoothWheelScrollingEnabled(boolean enabled) {
    runtimeConfig.rememberChatSmoothWheelScrollingEnabled(enabled);
  }

  @Override
  public void rememberChatHistoryLoadOlderChunkSize(int chunkSize) {
    runtimeConfig.rememberChatHistoryLoadOlderChunkSize(chunkSize);
  }

  @Override
  public void rememberChatHistoryLoadOlderChunkDelayMs(int chunkDelayMs) {
    runtimeConfig.rememberChatHistoryLoadOlderChunkDelayMs(chunkDelayMs);
  }

  @Override
  public void rememberChatHistoryLoadOlderChunkEdtBudgetMs(int chunkEdtBudgetMs) {
    runtimeConfig.rememberChatHistoryLoadOlderChunkEdtBudgetMs(chunkEdtBudgetMs);
  }

  @Override
  public void rememberChatHistoryDeferRichTextDuringBatch(boolean enabled) {
    runtimeConfig.rememberChatHistoryDeferRichTextDuringBatch(enabled);
  }

  @Override
  public void rememberChatHistoryLockViewportDuringLoadOlder(boolean enabled) {
    runtimeConfig.rememberChatHistoryLockViewportDuringLoadOlder(enabled);
  }

  @Override
  public void rememberChatHistoryRemoteRequestTimeoutSeconds(int seconds) {
    runtimeConfig.rememberChatHistoryRemoteRequestTimeoutSeconds(seconds);
  }

  @Override
  public void rememberChatHistoryRemoteZncPlaybackTimeoutSeconds(int seconds) {
    runtimeConfig.rememberChatHistoryRemoteZncPlaybackTimeoutSeconds(seconds);
  }

  @Override
  public void rememberChatHistoryRemoteZncPlaybackWindowMinutes(int minutes) {
    runtimeConfig.rememberChatHistoryRemoteZncPlaybackWindowMinutes(minutes);
  }

  @Override
  public void rememberCommandHistoryMaxSize(int maxSize) {
    runtimeConfig.rememberCommandHistoryMaxSize(maxSize);
  }

  @Override
  public void rememberChatTranscriptMaxLinesPerTarget(int maxLines) {
    runtimeConfig.rememberChatTranscriptMaxLinesPerTarget(maxLines);
  }
}

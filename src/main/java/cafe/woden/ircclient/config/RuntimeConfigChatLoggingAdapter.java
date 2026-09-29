package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.ChatLoggingRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for chat logging runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigChatLoggingAdapter implements ChatLoggingRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigChatLoggingAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public boolean readChatLoggingEnabled(boolean defaultValue) {
    return runtimeConfig.readChatLoggingEnabled(defaultValue);
  }

  @Override
  public void rememberChatLoggingEnabled(boolean enabled) {
    runtimeConfig.rememberChatLoggingEnabled(enabled);
  }

  @Override
  public void rememberChatLoggingLogSoftIgnoredLines(boolean enabled) {
    runtimeConfig.rememberChatLoggingLogSoftIgnoredLines(enabled);
  }

  @Override
  public void rememberChatLoggingRedactionAuditEnabled(boolean enabled) {
    runtimeConfig.rememberChatLoggingRedactionAuditEnabled(enabled);
  }

  @Override
  public void rememberChatLoggingLogPrivateMessages(boolean enabled) {
    runtimeConfig.rememberChatLoggingLogPrivateMessages(enabled);
  }

  @Override
  public void rememberChatLoggingSavePrivateMessageList(boolean enabled) {
    runtimeConfig.rememberChatLoggingSavePrivateMessageList(enabled);
  }

  @Override
  public void rememberChatLoggingDbFileBaseName(String fileBaseName) {
    runtimeConfig.rememberChatLoggingDbFileBaseName(fileBaseName);
  }

  @Override
  public void rememberChatLoggingDbNextToRuntimeConfig(boolean nextToRuntimeConfig) {
    runtimeConfig.rememberChatLoggingDbNextToRuntimeConfig(nextToRuntimeConfig);
  }

  @Override
  public void rememberChatLoggingKeepForever(boolean keepForever) {
    runtimeConfig.rememberChatLoggingKeepForever(keepForever);
  }

  @Override
  public void rememberChatLoggingRetentionDays(int retentionDays) {
    runtimeConfig.rememberChatLoggingRetentionDays(retentionDays);
  }

  @Override
  public void rememberChatLoggingWriterQueueMax(int writerQueueMax) {
    runtimeConfig.rememberChatLoggingWriterQueueMax(writerQueueMax);
  }

  @Override
  public void rememberChatLoggingWriterBatchSize(int writerBatchSize) {
    runtimeConfig.rememberChatLoggingWriterBatchSize(writerBatchSize);
  }
}

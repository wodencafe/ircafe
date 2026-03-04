package cafe.woden.ircclient.logging.channelmeta;

import cafe.woden.ircclient.app.api.ChannelMetadataPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.ExecutorConfig;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ChannelMetadataStore implements ChannelMetadataPort {

  private static final Logger log = LoggerFactory.getLogger(ChannelMetadataStore.class);

  private final ChannelMetadataRepository repository;
  private final Executor persistExecutor;
  private final Map<TargetRef, String> topicByTarget = new ConcurrentHashMap<>();

  public ChannelMetadataStore(
      ChannelMetadataRepository repository,
      @Qualifier(ExecutorConfig.CHANNEL_METADATA_PERSIST_EXECUTOR) Executor persistExecutor) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.persistExecutor = Objects.requireNonNull(persistExecutor, "persistExecutor");
  }

  @PostConstruct
  void initializeCache() {
    List<ChannelMetadataRepository.ChannelMetadataRow> rows = repository.findAll();
    int loaded = 0;
    for (ChannelMetadataRepository.ChannelMetadataRow row : rows) {
      if (row == null) continue;
      TargetRef ref = toTargetRef(row.serverId(), row.channelDisplay(), row.channelKey());
      if (ref == null || !ref.isChannel()) continue;
      String topic = normalizeTopic(row.topic());
      if (topic.isEmpty()) continue;
      topicByTarget.put(ref, topic);
      loaded++;
    }
    log.info("[ircafe] loaded {} persisted channel topic snapshots", loaded);
  }

  @Override
  public String topicFor(TargetRef target) {
    if (target == null || !target.isChannel()) return "";
    return Objects.toString(topicByTarget.getOrDefault(target, ""), "");
  }

  @Override
  public void rememberTopic(
      TargetRef target, String topic, String topicSetBy, Long topicSetAtEpochMs) {
    if (target == null || !target.isChannel()) return;
    String normalized = normalizeTopic(topic);
    if (normalized.isEmpty()) {
      topicByTarget.remove(target);
      persistExecutor.execute(() -> safeDelete(target.serverId(), target.key()));
      return;
    }

    topicByTarget.put(target, normalized);
    long now = System.currentTimeMillis();
    ChannelMetadataRepository.ChannelMetadataRow row =
        new ChannelMetadataRepository.ChannelMetadataRow(
            target.serverId(),
            target.key(),
            target.target(),
            normalized,
            normalizeOptional(topicSetBy),
            normalizeEpoch(topicSetAtEpochMs),
            now);
    persistExecutor.execute(() -> safeUpsert(row));
  }

  private void safeUpsert(ChannelMetadataRepository.ChannelMetadataRow row) {
    try {
      repository.upsert(row);
    } catch (Exception ex) {
      log.warn(
          "[ircafe] failed to persist channel metadata for {}/{}",
          row.serverId(),
          row.channelKey(),
          ex);
    }
  }

  private void safeDelete(String serverId, String channelKey) {
    try {
      repository.delete(serverId, channelKey);
    } catch (Exception ex) {
      log.warn(
          "[ircafe] failed to delete channel metadata for {}/{}",
          Objects.toString(serverId, "").trim(),
          Objects.toString(channelKey, "").trim(),
          ex);
    }
  }

  private static TargetRef toTargetRef(String serverId, String channelDisplay, String channelKey) {
    String sid = Objects.toString(serverId, "").trim();
    String display = Objects.toString(channelDisplay, "").trim();
    String key = Objects.toString(channelKey, "").trim();
    if (sid.isEmpty()) return null;
    String target = display.isEmpty() ? key : display;
    if (target.isEmpty()) return null;
    try {
      return new TargetRef(sid, target);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String normalizeTopic(String topic) {
    if (topic == null) return "";
    return topic.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
  }

  private static String normalizeOptional(String value) {
    String normalized = Objects.toString(value, "").trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static Long normalizeEpoch(Long epochMs) {
    if (epochMs == null || epochMs <= 0L) return null;
    return epochMs;
  }
}

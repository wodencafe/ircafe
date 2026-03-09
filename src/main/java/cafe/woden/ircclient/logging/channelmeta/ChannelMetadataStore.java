package cafe.woden.ircclient.logging.channelmeta;

import cafe.woden.ircclient.app.api.ChannelMetadataPort;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.model.TargetRef;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@InfrastructureLayer
public class ChannelMetadataStore implements ChannelMetadataPort {

  private static final Logger log = LoggerFactory.getLogger(ChannelMetadataStore.class);
  private static final int DEFAULT_TOPIC_PANEL_HEIGHT_PX = 58;
  private static final int MIN_TOPIC_PANEL_HEIGHT_PX = 40;
  private static final int MAX_TOPIC_PANEL_HEIGHT_PX = 200;

  private final ChannelMetadataRepository repository;
  private final Executor persistExecutor;
  private final Map<TargetRef, String> topicByTarget = new ConcurrentHashMap<>();
  private final Map<TargetRef, Integer> topicHeightByTarget = new ConcurrentHashMap<>();

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
      if (!topic.isEmpty()) {
        topicByTarget.put(ref, topic);
        loaded++;
      }
      Integer topicHeight = normalizeTopicPanelHeight(row.topicPanelHeightPx());
      if (topicHeight != null) {
        topicHeightByTarget.put(ref, topicHeight);
      }
    }
    log.info("[ircafe] loaded {} persisted channel topic snapshots", loaded);
  }

  @Override
  public String topicFor(TargetRef target) {
    if (target == null || !target.isChannel()) return "";
    return Objects.toString(topicByTarget.getOrDefault(target, ""), "");
  }

  @Override
  public int topicPanelHeightPxFor(TargetRef target) {
    if (target == null || !target.isChannel()) return DEFAULT_TOPIC_PANEL_HEIGHT_PX;
    Integer saved = topicHeightByTarget.get(target);
    if (saved == null) return DEFAULT_TOPIC_PANEL_HEIGHT_PX;
    return normalizeTopicPanelHeight(saved);
  }

  @Override
  public void rememberTopic(
      TargetRef target, String topic, String topicSetBy, Long topicSetAtEpochMs) {
    if (target == null || !target.isChannel()) return;
    String normalized = normalizeTopic(topic);
    Integer savedTopicHeight = topicHeightByTarget.get(target);
    if (normalized.isEmpty()) {
      topicByTarget.remove(target);
      // Keep per-channel rows when they carry topic panel height metadata.
      if (savedTopicHeight == null) {
        persistExecutor.execute(() -> safeDelete(target.serverId(), target.key()));
        return;
      }
    } else {
      topicByTarget.put(target, normalized);
    }
    long now = System.currentTimeMillis();
    ChannelMetadataRepository.ChannelMetadataRow row =
        new ChannelMetadataRepository.ChannelMetadataRow(
            target.serverId(),
            target.key(),
            target.target(),
            normalized,
            savedTopicHeight,
            normalizeOptional(topicSetBy),
            normalizeEpoch(topicSetAtEpochMs),
            now);
    persistExecutor.execute(() -> safeUpsert(row));
  }

  @Override
  public void rememberTopicPanelHeight(TargetRef target, int heightPx) {
    if (target == null || !target.isChannel()) return;
    int normalizedHeight = normalizeTopicPanelHeight(heightPx);
    topicHeightByTarget.put(target, normalizedHeight);
    String topic = topicFor(target);
    long now = System.currentTimeMillis();
    ChannelMetadataRepository.ChannelMetadataRow row =
        new ChannelMetadataRepository.ChannelMetadataRow(
            target.serverId(),
            target.key(),
            target.target(),
            topic,
            normalizedHeight,
            null,
            null,
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

  private static Integer normalizeTopicPanelHeight(Integer heightPx) {
    if (heightPx == null) return null;
    return Math.max(MIN_TOPIC_PANEL_HEIGHT_PX, Math.min(MAX_TOPIC_PANEL_HEIGHT_PX, heightPx));
  }

  private static int normalizeTopicPanelHeight(int heightPx) {
    return Math.max(MIN_TOPIC_PANEL_HEIGHT_PX, Math.min(MAX_TOPIC_PANEL_HEIGHT_PX, heightPx));
  }
}

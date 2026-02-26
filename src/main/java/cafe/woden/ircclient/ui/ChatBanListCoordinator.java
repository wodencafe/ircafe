package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns ban-list state and channel-list detail projection for {@link ChatDockable}. */
final class ChatBanListCoordinator {

  private final ChannelListPanel channelListPanel;
  private final Map<String, Map<String, ArrayList<BanListEntry>>> entriesByServer = new HashMap<>();
  private final Map<String, Map<String, String>> summaryByServer = new HashMap<>();

  private record BanListEntry(String mask, String setBy, Long setAtEpochSeconds) {}

  ChatBanListCoordinator(ChannelListPanel channelListPanel) {
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
  }

  void beginBanList(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;

    entriesByServer.computeIfAbsent(sid, __ -> new HashMap<>()).put(ch, new ArrayList<>());
    summaryByServer.computeIfAbsent(sid, __ -> new HashMap<>()).remove(ch);
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  void appendBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    String normalizedMask = Objects.toString(mask, "").trim();
    if (sid.isEmpty() || ch.isEmpty() || normalizedMask.isEmpty()) return;

    Map<String, ArrayList<BanListEntry>> byChannel =
        entriesByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    ArrayList<BanListEntry> entries = byChannel.computeIfAbsent(ch, __ -> new ArrayList<>());
    entries.add(
        new BanListEntry(
            normalizedMask,
            Objects.toString(setBy, "").trim(),
            setAtEpochSeconds != null && setAtEpochSeconds.longValue() > 0L
                ? setAtEpochSeconds
                : null));
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  void endBanList(String serverId, String channel, String summary) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    String text = Objects.toString(summary, "").trim();
    if (!text.isEmpty()) {
      summaryByServer.computeIfAbsent(sid, __ -> new HashMap<>()).put(ch, text);
    }
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  List<String> snapshot(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return List.of();

    Map<String, ArrayList<BanListEntry>> byChannel = entriesByServer.get(sid);
    ArrayList<BanListEntry> entries = byChannel == null ? null : byChannel.get(ch);
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }

    ArrayList<String> out = new ArrayList<>(entries.size() + 1);
    for (BanListEntry entry : entries) {
      if (entry == null) continue;
      StringBuilder line = new StringBuilder(entry.mask());
      String by = Objects.toString(entry.setBy(), "").trim();
      if (!by.isEmpty()) line.append("  |  set by ").append(by);
      if (entry.setAtEpochSeconds() != null && entry.setAtEpochSeconds().longValue() > 0L) {
        try {
          line.append("  |  ").append(Instant.ofEpochSecond(entry.setAtEpochSeconds().longValue()));
        } catch (Exception ignored) {
        }
      }
      out.add(line.toString());
    }

    if (out.isEmpty()) {
      return List.of();
    }
    String summary = summaryByServer.getOrDefault(sid, Map.of()).getOrDefault(ch, "").trim();
    if (!summary.isEmpty()) out.add(summary);
    return List.copyOf(out);
  }

  private static String normalizeChannelName(String channel) {
    String normalized = Objects.toString(channel, "").trim();
    if (normalized.isEmpty()) return "";
    return (normalized.startsWith("#") || normalized.startsWith("&")) ? normalized : "";
  }
}

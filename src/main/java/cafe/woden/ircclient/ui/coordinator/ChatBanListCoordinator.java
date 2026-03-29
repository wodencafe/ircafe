package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns ban-list state and channel-list detail projection for {@link ChatDockable}. */
public final class ChatBanListCoordinator {

  private final ChannelListPanel channelListPanel;
  private final Map<String, Map<String, ArrayList<BanListEntry>>> entriesByServer = new HashMap<>();
  private final Map<String, Map<String, String>> summaryByServer = new HashMap<>();

  private record BanListEntry(String mask, String setBy, Long setAtEpochSeconds) {}

  public ChatBanListCoordinator(ChannelListPanel channelListPanel) {
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
  }

  public void beginBanList(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;

    entriesByServer.computeIfAbsent(sid, __ -> new HashMap<>()).put(ch, new ArrayList<>());
    summaryByServer.computeIfAbsent(sid, __ -> new HashMap<>()).remove(ch);
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  public void appendBanListEntry(
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

  public void endBanList(String serverId, String channel, String summary) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;
    String text = Objects.toString(summary, "").trim();
    if (!text.isEmpty()) {
      summaryByServer.computeIfAbsent(sid, __ -> new HashMap<>()).put(ch, text);
    }
    channelListPanel.refreshOpenChannelDetails(sid, ch);
  }

  public ChannelListPanel.BanListSnapshot snapshot(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = normalizeChannelName(channel);
    if (sid.isEmpty() || ch.isEmpty()) return ChannelListPanel.BanListSnapshot.empty();

    Map<String, ArrayList<BanListEntry>> byChannel = entriesByServer.get(sid);
    ArrayList<BanListEntry> entries = byChannel == null ? null : byChannel.get(ch);
    String summary = summaryByServer.getOrDefault(sid, Map.of()).getOrDefault(ch, "").trim();
    if (entries == null || entries.isEmpty()) {
      return summary.isEmpty()
          ? ChannelListPanel.BanListSnapshot.empty()
          : new ChannelListPanel.BanListSnapshot(List.of(), summary);
    }

    ArrayList<ChannelListPanel.BanListEntryRow> out = new ArrayList<>(entries.size());
    for (BanListEntry entry : entries) {
      if (entry == null) continue;
      out.add(
          new ChannelListPanel.BanListEntryRow(
              entry.mask(), entry.setBy(), formatSetAt(entry.setAtEpochSeconds())));
    }

    return out.isEmpty()
        ? (summary.isEmpty()
            ? ChannelListPanel.BanListSnapshot.empty()
            : new ChannelListPanel.BanListSnapshot(List.of(), summary))
        : new ChannelListPanel.BanListSnapshot(out, summary);
  }

  private static String formatSetAt(Long setAtEpochSeconds) {
    if (setAtEpochSeconds == null || setAtEpochSeconds.longValue() <= 0L) return "";
    try {
      return Instant.ofEpochSecond(setAtEpochSeconds.longValue()).toString();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String normalizeChannelName(String channel) {
    String normalized = Objects.toString(channel, "").trim();
    if (normalized.isEmpty()) return "";
    return (normalized.startsWith("#") || normalized.startsWith("&")) ? normalized : "";
  }
}

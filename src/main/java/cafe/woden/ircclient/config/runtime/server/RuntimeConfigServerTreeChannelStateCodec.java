package cafe.woden.ircclient.config.runtime.server;

import static cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSupport.asBoolean;

import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelPreference;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelSortMode;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pure codec/policy helpers for persisted server-tree channel state. */
final class RuntimeConfigServerTreeChannelStateCodec {

  private static final Logger log =
      LoggerFactory.getLogger(RuntimeConfigServerTreeChannelStateCodec.class);

  private RuntimeConfigServerTreeChannelStateCodec() {}

  static ServerTreeChannelState parseServerTreeChannelState(
      Map<String, Object> raw, List<String> joinedChannels) {
    Map<String, Object> safeRaw = raw == null ? Map.of() : raw;
    List<String> safeJoinedChannels = joinedChannels == null ? List.of() : joinedChannels;
    try {
      ServerTreeChannelSortMode sortMode =
          ServerTreeChannelSortMode.fromToken(Objects.toString(safeRaw.get("sortMode"), ""));

      LinkedHashMap<String, ServerTreeChannelPreference> byKey = new LinkedHashMap<>();
      Object channelsObj = safeRaw.get("channels");
      if (channelsObj instanceof List<?> list) {
        for (Object entry : list) {
          if (!(entry instanceof Map<?, ?> item)) continue;
          String channel = normalizeChannelName(item.get("name"));
          if (channel.isEmpty()) continue;
          String key = foldChannelKey(channel);
          if (byKey.containsKey(key)) continue;
          boolean auto = asBoolean(item.get("autoReattach")).orElse(Boolean.TRUE);
          boolean pinned = asBoolean(item.get("pinned")).orElse(Boolean.FALSE);
          boolean muted = asBoolean(item.get("muted")).orElse(Boolean.FALSE);
          byKey.put(key, new ServerTreeChannelPreference(channel, auto, pinned, muted));
        }
      }

      for (String joined : safeJoinedChannels) {
        String channel = normalizeChannelName(joined);
        if (channel.isEmpty()) continue;
        String key = foldChannelKey(channel);
        byKey.putIfAbsent(key, new ServerTreeChannelPreference(channel, true));
      }

      ArrayList<String> customOrder = sanitizeCustomOrder(safeRaw.get("customOrder"), byKey);

      if (customOrder.isEmpty()) {
        for (ServerTreeChannelPreference pref : byKey.values()) {
          customOrder.add(pref.channel());
        }
      }

      if (byKey.isEmpty() && safeJoinedChannels.isEmpty()) {
        return ServerTreeChannelState.defaults();
      }

      return new ServerTreeChannelState(
          sortMode, List.copyOf(customOrder), List.copyOf(byKey.values()));
    } catch (Exception e) {
      log.warn("[ircafe] Could not parse server-tree channel state", e);
      return stateFromLegacyAutoJoin(joinedChannels);
    }
  }

  static List<Map<String, Object>> serializeChannelPreferences(
      Iterable<ServerTreeChannelPreference> preferences) {
    ArrayList<Map<String, Object>> channelsOut = new ArrayList<>();
    for (ServerTreeChannelPreference pref : preferences) {
      if (pref == null) continue;
      String channel = normalizeChannelName(pref.channel());
      if (channel.isEmpty()) continue;
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", channel);
      item.put("autoReattach", pref.autoReattach());
      if (pref.pinned()) {
        item.put("pinned", true);
      }
      if (pref.muted()) {
        item.put("muted", true);
      }
      channelsOut.add(item);
    }
    return channelsOut.isEmpty() ? List.of() : List.copyOf(channelsOut);
  }

  static LinkedHashMap<String, ServerTreeChannelPreference> channelPreferencesByKey(
      ServerTreeChannelState state) {
    LinkedHashMap<String, ServerTreeChannelPreference> byKey = new LinkedHashMap<>();
    if (state == null || state.channels() == null) return byKey;
    for (ServerTreeChannelPreference pref : state.channels()) {
      if (pref == null) continue;
      String channel = normalizeChannelName(pref.channel());
      if (channel.isEmpty()) continue;
      String key = foldChannelKey(channel);
      byKey.put(
          key,
          new ServerTreeChannelPreference(
              channel, pref.autoReattach(), pref.pinned(), pref.muted()));
    }
    return byKey;
  }

  static ArrayList<String> sanitizeCustomOrder(
      ServerTreeChannelState state, Map<String, ServerTreeChannelPreference> channelsByKey) {
    if (state == null) return sanitizeCustomOrder((Object) null, channelsByKey);
    return sanitizeCustomOrder(state.customOrder(), channelsByKey);
  }

  static ArrayList<String> sanitizeCustomOrder(
      Object rawOrder, Map<String, ServerTreeChannelPreference> channelsByKey) {
    Map<String, ServerTreeChannelPreference> safeChannels =
        channelsByKey == null ? Map.of() : channelsByKey;
    ArrayList<String> out = new ArrayList<>();
    Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    if (rawOrder instanceof List<?> rawList) {
      for (Object entry : rawList) {
        String channel = normalizeChannelName(entry);
        if (channel.isEmpty()) continue;
        String key = foldChannelKey(channel);
        if (!safeChannels.containsKey(key)) continue;
        if (seen.contains(channel)) continue;
        String canonicalChannel = safeChannels.get(key).channel();
        out.add(canonicalChannel);
        seen.add(canonicalChannel);
      }
    } else if (rawOrder instanceof ServerTreeChannelState state) {
      return sanitizeCustomOrder(state.customOrder(), safeChannels);
    }

    for (ServerTreeChannelPreference pref : safeChannels.values()) {
      if (pref == null) continue;
      String channel = normalizeChannelName(pref.channel());
      if (channel.isEmpty()) continue;
      if (!seen.add(channel)) continue;
      out.add(channel);
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> readMap(Object raw) {
    if (raw instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return Map.of();
  }

  static String normalizeChannelName(Object channel) {
    String ch = Objects.toString(channel, "").trim();
    if (ch.isEmpty()) return "";
    return (ch.startsWith("#") || ch.startsWith("&")) ? ch : "";
  }

  static String foldChannelKey(String channel) {
    return Objects.toString(channel, "").trim().toLowerCase(Locale.ROOT);
  }

  private static ServerTreeChannelState stateFromLegacyAutoJoin(List<String> joinedChannels) {
    if (joinedChannels == null || joinedChannels.isEmpty()) {
      return ServerTreeChannelState.defaults();
    }

    ArrayList<ServerTreeChannelPreference> channels = new ArrayList<>();
    for (String entry : joinedChannels) {
      String channel = normalizeChannelName(entry);
      if (channel.isEmpty()) continue;
      if (channels.stream().anyMatch(pref -> channel.equalsIgnoreCase(pref.channel()))) continue;
      channels.add(new ServerTreeChannelPreference(channel, true));
    }

    if (channels.isEmpty()) {
      return ServerTreeChannelState.defaults();
    }

    ArrayList<String> customOrder = new ArrayList<>();
    for (ServerTreeChannelPreference pref : channels) {
      customOrder.add(pref.channel());
    }
    return new ServerTreeChannelState(
        ServerTreeChannelSortMode.CUSTOM, List.copyOf(customOrder), List.copyOf(channels));
  }
}

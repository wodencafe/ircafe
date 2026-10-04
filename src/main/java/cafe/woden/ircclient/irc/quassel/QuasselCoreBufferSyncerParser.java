package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstIntFromMapKeys;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstLongFromMapKeys;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstMapValueByKeyIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseInt;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseLong;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Decodes native read-marker updates without owning buffers, history, or pending replay. */
final class QuasselCoreBufferSyncerParser {
  private static final List<String> MARKER_PROPERTIES =
      List.of("markerLines", "lastSeenMsg", "lastSeenMsgs");
  private static final Set<String> MARKER_PROPERTY_NAMES_LOWER =
      Set.of("markerlines", "lastseenmsg", "lastseenmsgs");

  private QuasselCoreBufferSyncerParser() {}

  record ReadMarkerUpdate(int bufferId, long msgId) {}

  static List<ReadMarkerUpdate> parseReadMarkers(String slotName, List<?> values) {
    if (values == null || values.isEmpty()) return List.of();
    String slot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    if (slot.contains("setmarkerline") || slot.contains("setlastseenmsg")) {
      int bufferId = tryParseInt(values.get(0));
      long msgId = values.size() < 2 ? -1L : tryParseLong(values.get(1));
      return bufferId >= 0 && msgId > 0L
          ? List.of(new ReadMarkerUpdate(bufferId, msgId))
          : List.of();
    }

    LinkedHashSet<ReadMarkerUpdate> updates = new LinkedHashSet<>();
    // Decode flat init-data properties before nested snapshot observations.
    for (int i = 0; i + 1 < values.size(); i += 2) {
      Object rawKey = values.get(i);
      String key =
          rawKey instanceof byte[] bytes
              ? new String(bytes, StandardCharsets.UTF_8).trim()
              : Objects.toString(rawKey, "").trim();
      if (MARKER_PROPERTY_NAMES_LOWER.contains(key.toLowerCase(Locale.ROOT))) {
        collectMarkerPairs(values.get(i + 1), updates);
      }
    }
    for (Object value : values) {
      collectReadMarkers(value, updates);
    }
    return List.copyOf(updates);
  }

  private static void collectReadMarkers(Object raw, Set<ReadMarkerUpdate> out) {
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectReadMarkers(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;

    int bufferId = firstIntFromMapKeys(map, "bufferId", "bufferid", "buffer", "buffer_id", "id");
    long msgId =
        firstLongFromMapKeys(
            map,
            "markerLine",
            "markerline",
            "lastSeenMsg",
            "lastseenmsg",
            "lastSeen",
            "lastseen",
            "msgId",
            "msgid",
            "messageId",
            "messageid");
    if (bufferId >= 0 && msgId > 0L) {
      out.add(new ReadMarkerUpdate(bufferId, msgId));
    }

    for (String key : MARKER_PROPERTIES) {
      collectMarkerPairs(firstMapValueByKeyIgnoreCase(map, key), out);
    }
    for (Object value : map.values()) {
      collectReadMarkers(value, out);
    }
  }

  private static void collectMarkerPairs(Object raw, Set<ReadMarkerUpdate> out) {
    if (raw instanceof Map<?, ?> markerMap) {
      for (Map.Entry<?, ?> entry : markerMap.entrySet()) {
        int bufferId = tryParseInt(entry.getKey());
        long msgId = tryParseLong(entry.getValue());
        if (bufferId >= 0 && msgId > 0L) {
          out.add(new ReadMarkerUpdate(bufferId, msgId));
        }
      }
    } else if (raw instanceof List<?> pairs) {
      for (int i = 0; i + 1 < pairs.size(); i += 2) {
        int bufferId = tryParseInt(pairs.get(i));
        long msgId = tryParseLong(pairs.get(i + 1));
        if (bufferId >= 0 && msgId > 0L) {
          out.add(new ReadMarkerUpdate(bufferId, msgId));
        }
      }
    }
  }
}

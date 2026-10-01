package cafe.woden.ircclient.config.runtime.server;

import static cafe.woden.ircclient.config.runtime.server.RuntimeConfigServerTreeChannelStateCodec.channelPreferencesByKey;
import static cafe.woden.ircclient.config.runtime.server.RuntimeConfigServerTreeChannelStateCodec.parseServerTreeChannelState;
import static cafe.woden.ircclient.config.runtime.server.RuntimeConfigServerTreeChannelStateCodec.sanitizeCustomOrder;
import static cafe.woden.ircclient.config.runtime.server.RuntimeConfigServerTreeChannelStateCodec.serializeChannelPreferences;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelPreference;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelSortMode;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeConfigServerTreeChannelStateCodecTest {

  @Test
  void customOrderDedupeUsesTheSpellingAlreadyAddedToTheOutput() {
    var channels = new LinkedHashMap<String, ServerTreeChannelPreference>();
    channels.put("#i\u0307", new ServerTreeChannelPreference("#\u0130", true));
    channels.put("#i", new ServerTreeChannelPreference("#i", true));

    assertEquals(List.of("#\u0130"), sanitizeCustomOrder(List.of("#i\u0307", "#i"), channels));
  }

  @Test
  void customOrderKeepsUnicodeCaseInsensitiveDedupeAndCanonicalSpelling() {
    var channels = new LinkedHashMap<String, ServerTreeChannelPreference>();
    channels.put("#alpha", new ServerTreeChannelPreference("#Alpha", true));
    channels.put("#i", new ServerTreeChannelPreference("#I", true));
    channels.put("#\u0131", new ServerTreeChannelPreference("#\u0131", true));
    channels.put("#beta", new ServerTreeChannelPreference("#beta", true));

    assertEquals(
        List.of("#I", "#Alpha", "#beta"),
        sanitizeCustomOrder(List.of("#i", "#\u0131", "#ALPHA", "#alpha", "#missing"), channels));
  }

  @Test
  void parseServerTreeChannelStateSanitizesChannelsCustomOrderAndJoinedChannels() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("sortMode", "alphabetical");
    raw.put("customOrder", List.of("#beta", "#missing", "#ALPHA", "#beta"));
    raw.put(
        "channels",
        List.of(
            Map.of("name", "#alpha", "autoReattach", false, "pinned", true, "muted", true),
            Map.of("name", "#ALPHA", "autoReattach", true),
            Map.of("name", "not-a-channel", "autoReattach", true),
            Map.of("name", "#beta")));

    ServerTreeChannelState state = parseServerTreeChannelState(raw, List.of("#gamma", "#alpha"));

    assertEquals(ServerTreeChannelSortMode.ALPHABETICAL, state.sortMode());
    assertEquals(List.of("#beta", "#alpha", "#gamma"), state.customOrder());
    assertEquals(
        List.of(
            new ServerTreeChannelPreference("#alpha", false, true, true),
            new ServerTreeChannelPreference("#beta", true),
            new ServerTreeChannelPreference("#gamma", true)),
        state.channels());
  }

  @Test
  void serializeChannelPreferencesOmitsInvalidChannelsAndDefaultFalseFlags() {
    Map<String, Object> alpha = new LinkedHashMap<>();
    alpha.put("name", "#alpha");
    alpha.put("autoReattach", true);
    alpha.put("pinned", true);
    alpha.put("muted", true);

    Map<String, Object> beta = new LinkedHashMap<>();
    beta.put("name", "#beta");
    beta.put("autoReattach", false);

    assertEquals(
        List.of(alpha, beta),
        serializeChannelPreferences(
            List.of(
                new ServerTreeChannelPreference("#alpha", true, true, true),
                new ServerTreeChannelPreference("#beta", false),
                new ServerTreeChannelPreference("not-a-channel", true))));
  }

  @Test
  void channelPreferencesByKeyCopiesPreferencesAndKeepsLastCaseInsensitiveDuplicate() {
    ServerTreeChannelState state =
        new ServerTreeChannelState(
            ServerTreeChannelSortMode.CUSTOM,
            List.of(),
            List.of(
                new ServerTreeChannelPreference("#alpha", true),
                new ServerTreeChannelPreference("#ALPHA", false, true, true)));

    assertEquals(
        Map.of("#alpha", new ServerTreeChannelPreference("#ALPHA", false, true, true)),
        channelPreferencesByKey(state));
  }
}

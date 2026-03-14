package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Emits structured {@link IrcEvent}s from IRC MONITOR numerics. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxMonitorEventEmitter {
  @NonNull private final String serverId;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  public boolean maybeEmitNumeric(String rawLine, String originalLine) {
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) return false;
    Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(originalLine);
    if (at == null) at = Instant.now();

    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> onlineEntries =
        PircbotxMonitorParsers.parseRpl730MonitorOnlineEntries(raw);
    List<String> online = monitorNickList(onlineEntries);
    if (!online.isEmpty()) {
      emitMonitorHostmaskObservations(at, onlineEntries);
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.MonitorOnlineObserved(at, online)));
      return true;
    }

    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> offlineEntries =
        PircbotxMonitorParsers.parseRpl731MonitorOfflineEntries(raw);
    List<String> offline = monitorNickList(offlineEntries);
    if (!offline.isEmpty()) {
      emitMonitorHostmaskObservations(at, offlineEntries);
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.MonitorOfflineObserved(at, offline)));
      return true;
    }

    List<String> list = PircbotxMonitorParsers.parseRpl732MonitorListNicks(raw);
    if (!list.isEmpty()) {
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.MonitorListObserved(at, list)));
      return true;
    }

    if (PircbotxMonitorParsers.isRpl733MonitorListEnd(raw)) {
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.MonitorListEnded(at)));
      return true;
    }

    PircbotxMonitorParsers.ParsedMonitorListFull full =
        PircbotxMonitorParsers.parseErr734MonitorListFull(raw);
    if (full != null) {
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.MonitorListFull(at, full.limit(), full.nicks(), full.message())));
      return true;
    }
    return false;
  }

  private List<String> monitorNickList(
      List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(entries.size());
    for (PircbotxMonitorParsers.ParsedMonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      if (!nick.isEmpty()) out.add(nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private void emitMonitorHostmaskObservations(
      Instant at, List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return;
    for (PircbotxMonitorParsers.ParsedMonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      String hostmask = Objects.toString(entry.hostmask(), "").trim();
      if (nick.isEmpty() || !PircbotxUtil.isUsefulHostmask(hostmask)) continue;
      emit.accept(
          new ServerIrcEvent(serverId, new IrcEvent.UserHostmaskObserved(at, "", nick, hostmask)));
    }
  }
}

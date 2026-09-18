package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionContribution;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionKind;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3SpecStatus;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3UiGroup;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3UiMetadata;
import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Built-in metadata and runtime provider for IRC MONITOR capability interpretation. */
@AutoService({
  Ircv3ExtensionProvider.class,
  Ircv3InboundCommandSignalProvider.class,
  Ircv3OutboundCommandProvider.class
})
public final class Ircv3MonitorExtensionProvider
    implements Ircv3ExtensionProvider,
        Ircv3InboundCommandSignalProvider,
        Ircv3OutboundCommandProvider {

  @Override
  public String providerId() {
    return "monitor";
  }

  @Override
  public int sortOrder() {
    return 130;
  }

  @Override
  public List<Ircv3ExtensionContribution> extensions() {
    return List.of(
        capability(
            "monitor",
            "MONITOR",
            155,
            "Lets IRCafe track online/offline state for monitored nicknames."));
  }

  @Override
  public Set<Ircv3OutboundCommandOperation> operations() {
    return Set.of(
        Ircv3OutboundCommandOperation.MONITOR_LIST,
        Ircv3OutboundCommandOperation.MONITOR_STATUS,
        Ircv3OutboundCommandOperation.MONITOR_CLEAR,
        Ircv3OutboundCommandOperation.MONITOR_ADD,
        Ircv3OutboundCommandOperation.MONITOR_REMOVE);
  }

  @Override
  public List<String> build(
      Ircv3OutboundCommandOperation operation, Ircv3OutboundCommandRequest request) {
    if (operation == null || request == null) {
      return List.of();
    }
    return switch (operation) {
      case MONITOR_LIST -> single(new Ircv3MonitorCommandPlanner.ListRequested());
      case MONITOR_STATUS -> single(new Ircv3MonitorCommandPlanner.StatusRequested());
      case MONITOR_CLEAR -> single(new Ircv3MonitorCommandPlanner.ClearRequested());
      case MONITOR_ADD ->
          Ircv3MonitorCommandPlanner.modificationRawLines('+', request.values(), request.limit());
      case MONITOR_REMOVE ->
          Ircv3MonitorCommandPlanner.modificationRawLines('-', request.values(), request.limit());
      default -> List.of();
    };
  }

  @Override
  public Set<Ircv3InboundCommandOperation> inboundCommandOperations() {
    return Set.of(
        Ircv3InboundCommandOperation.MONITOR, Ircv3InboundCommandOperation.ISUPPORT_MONITOR);
  }

  @Override
  public List<Ircv3InboundCommandSignal> parse(
      Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
    if (operation == null || request == null) {
      return List.of();
    }
    if (operation == Ircv3InboundCommandOperation.ISUPPORT_MONITOR) {
      Ircv3MonitorParser.ParsedMonitorSupport support =
          Ircv3MonitorParser.parseRpl005MonitorSupport(request.rawLine());
      return support == null
          ? List.of()
          : List.of(
              new Ircv3InboundCommandSignal.MonitorSupportObserved(
                  support.supported(), support.limit()));
    }
    if (operation != Ircv3InboundCommandOperation.MONITOR) {
      return List.of();
    }

    String rawLine = request.rawLine();
    List<Ircv3MonitorParser.ParsedMonitorStatusEntry> online =
        Ircv3MonitorParser.parseRpl730MonitorOnlineEntries(rawLine);
    if (!online.isEmpty()) {
      return List.of(new Ircv3InboundCommandSignal.MonitorStatusObserved(true, entries(online)));
    }

    List<Ircv3MonitorParser.ParsedMonitorStatusEntry> offline =
        Ircv3MonitorParser.parseRpl731MonitorOfflineEntries(rawLine);
    if (!offline.isEmpty()) {
      return List.of(new Ircv3InboundCommandSignal.MonitorStatusObserved(false, entries(offline)));
    }

    List<String> list = Ircv3MonitorParser.parseRpl732MonitorListNicks(rawLine);
    if (!list.isEmpty()) {
      return List.of(new Ircv3InboundCommandSignal.MonitorListObserved(list));
    }

    if (Ircv3MonitorParser.isRpl733MonitorListEnd(rawLine)) {
      return List.of(new Ircv3InboundCommandSignal.MonitorListEnded());
    }

    Ircv3MonitorParser.ParsedMonitorListFull full =
        Ircv3MonitorParser.parseErr734MonitorListFull(rawLine);
    if (full == null) {
      return List.of();
    }
    return List.of(
        new Ircv3InboundCommandSignal.MonitorListFull(full.limit(), full.nicks(), full.message()));
  }

  private static List<String> single(Ircv3MonitorCommandPlanner.Action action) {
    String rawLine = Ircv3MonitorCommandPlanner.simpleRawLine(action);
    return rawLine.isEmpty() ? List.of() : List.of(rawLine);
  }

  private static List<Ircv3InboundCommandSignal.MonitorStatusEntry> entries(
      List<Ircv3MonitorParser.ParsedMonitorStatusEntry> parsedEntries) {
    ArrayList<Ircv3InboundCommandSignal.MonitorStatusEntry> entries =
        new ArrayList<>(parsedEntries.size());
    for (Ircv3MonitorParser.ParsedMonitorStatusEntry entry : parsedEntries) {
      if (entry != null) {
        entries.add(
            new Ircv3InboundCommandSignal.MonitorStatusEntry(entry.nick(), entry.hostmask()));
      }
    }
    return List.copyOf(entries);
  }

  private static Ircv3ExtensionContribution capability(
      String id, String label, int sortOrder, String impactSummary) {
    return new Ircv3ExtensionContribution(
        id,
        Ircv3ExtensionKind.CAPABILITY,
        Ircv3SpecStatus.STABLE,
        List.of(),
        // MONITOR is advertised through RPL_ISUPPORT (005), not negotiated with CAP REQ.
        "",
        id,
        new Ircv3UiMetadata(label, Ircv3UiGroup.CORE, sortOrder, impactSummary));
  }
}

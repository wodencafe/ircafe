package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates runtime-provider decisions for ZNC detection and history bootstrap suppression. */
public final class Ircv3ZncPlaybackRuntimeSupport {

  private static final int MAX_EVIDENCE_LENGTH = 1024;

  private final Ircv3InboundCommandSignalRuntimeCatalog commandCatalog;
  private final Ircv3InboundTagSignalRuntimeCatalog tagCatalog;

  public Ircv3ZncPlaybackRuntimeSupport(
      Ircv3InboundCommandSignalRuntimeCatalog commandCatalog,
      Ircv3InboundTagSignalRuntimeCatalog tagCatalog) {
    this.commandCatalog = Objects.requireNonNull(commandCatalog, "commandCatalog");
    this.tagCatalog = Objects.requireNonNull(tagCatalog, "tagCatalog");
  }

  public Detection detectZncCapability(String capabilityName) {
    return detect(
        Ircv3InboundCommandOperation.HISTORY_ZNC_CAPABILITY,
        new Ircv3InboundCommandRequest(
            "server", "CAP", "", List.of(Objects.toString(capabilityName, "")), Map.of()));
  }

  public Detection detectZncRpl004(String rawLine) {
    return detect(
        Ircv3InboundCommandOperation.HISTORY_ZNC_RPL004,
        new Ircv3InboundCommandRequest(
            "server", "004", Objects.toString(rawLine, ""), List.of(), Map.of()));
  }

  public boolean shouldSuppressBootstrap(boolean fromSelf, String target, String message) {
    List<Ircv3InboundTagSignal> signals =
        tagCatalog.parse(
            Ircv3InboundTagOperation.HISTORY_BOOTSTRAP_SUPPRESSION,
            Ircv3InboundTagRequest.historyBootstrap(target, message, fromSelf));
    if (signals.size() != 1) {
      return false;
    }
    Ircv3InboundTagSignal signal = signals.getFirst();
    return signal.type() == Ircv3InboundTagSignalType.HISTORY_BOOTSTRAP_SUPPRESSED;
  }

  private Detection detect(
      Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
    List<Ircv3InboundCommandSignal> signals = commandCatalog.parse(operation, request);
    if (signals.size() != 1
        || !(signals.getFirst()
            instanceof Ircv3InboundCommandSignal.ZncDetectedObserved detected)) {
      return Detection.notDetected();
    }
    String source = bounded(detected.source());
    String evidence = bounded(detected.evidence());
    if (source.isEmpty()) {
      return Detection.notDetected();
    }
    return new Detection(true, source, evidence);
  }

  private static String bounded(String value) {
    String normalized = Objects.toString(value, "").trim();
    if (normalized.length() > MAX_EVIDENCE_LENGTH) {
      return "";
    }
    for (int i = 0; i < normalized.length(); i++) {
      char ch = normalized.charAt(i);
      if (ch == '\r' || ch == '\n' || ch == '\0') {
        return "";
      }
    }
    return normalized;
  }

  public record Detection(boolean detected, String source, String evidence) {
    public Detection {
      source = Objects.toString(source, "").trim();
      evidence = Objects.toString(evidence, "").trim();
      if (!detected) {
        source = "";
        evidence = "";
      }
    }

    public static Detection notDetected() {
      return new Detection(false, "", "");
    }
  }
}

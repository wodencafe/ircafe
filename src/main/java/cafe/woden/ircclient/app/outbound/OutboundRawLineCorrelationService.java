package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Correlates outbound raw lines with labeled-response routing metadata when available. */
@Component
final class OutboundRawLineCorrelationService {

  private final IrcClientService irc;
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  private final LabeledResponseRoutingPort labeledResponseRoutingState;

  @Autowired
  OutboundRawLineCorrelationService(
      OutboundBackendCapabilityPolicy backendCapabilityPolicy,
      LabeledResponseRoutingPort labeledResponseRoutingState) {
    this.irc = null;
    this.backendCapabilityPolicy =
        Objects.requireNonNull(backendCapabilityPolicy, "backendCapabilityPolicy");
    this.labeledResponseRoutingState =
        Objects.requireNonNull(labeledResponseRoutingState, "labeledResponseRoutingState");
  }

  OutboundRawLineCorrelationService(
      IrcClientService irc, LabeledResponseRoutingPort labeledResponseRoutingState) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.backendCapabilityPolicy = null;
    this.labeledResponseRoutingState =
        Objects.requireNonNull(labeledResponseRoutingState, "labeledResponseRoutingState");
  }

  PreparedRawLine prepare(TargetRef origin, String rawLine) {
    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty() || origin == null) return new PreparedRawLine(line, "");
    if (!supportsLabeledResponse(origin.serverId())) return new PreparedRawLine(line, "");

    LabeledResponseRoutingPort.PreparedRawLine prepared =
        labeledResponseRoutingState.prepareOutgoingRaw(origin.serverId(), line);
    String sendLine =
        (prepared == null || prepared.line() == null || prepared.line().isBlank())
            ? line
            : prepared.line();
    String label = (prepared == null) ? "" : Objects.toString(prepared.label(), "").trim();
    if (!label.isEmpty()) {
      labeledResponseRoutingState.remember(
          origin.serverId(), label, origin, redactIfSensitive(line), Instant.now());
    }
    return new PreparedRawLine(sendLine, label);
  }

  private boolean supportsLabeledResponse(String serverId) {
    if (backendCapabilityPolicy != null) {
      return backendCapabilityPolicy.supportsLabeledResponse(serverId);
    }
    return irc != null && irc.isLabeledResponseAvailable(serverId);
  }

  static String redactIfSensitive(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return s;

    int sp = s.indexOf(' ');
    String head = (sp < 0 ? s : s.substring(0, sp)).trim();
    String upper = head.toUpperCase(Locale.ROOT);
    if (upper.equals("PASS") || upper.equals("OPER") || upper.equals("AUTHENTICATE")) {
      return upper + (sp < 0 ? "" : " <redacted>");
    }
    return s;
  }

  record PreparedRawLine(String line, String label) {}
}

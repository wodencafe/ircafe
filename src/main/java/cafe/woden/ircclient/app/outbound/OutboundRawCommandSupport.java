package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared support for correlated raw-line preparation, preview rendering, and validation. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundRawCommandSupport {

  @NonNull private final OutboundRawLineCorrelationService rawLineCorrelationService;

  PreparedRawLine prepare(TargetRef origin, String rawLine) {
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(origin, rawLine);
    return new PreparedRawLine(prepared.line(), prepared.label());
  }

  String preview(String rawLine, PreparedRawLine prepared) {
    return withLabelHint(rawLine, prepared == null ? "" : prepared.label());
  }

  String safePreview(String rawLine, PreparedRawLine prepared) {
    return withLabelHint(
        OutboundRawLineCorrelationService.redactIfSensitive(rawLine),
        prepared == null ? "" : prepared.label());
  }

  static boolean containsLineBreaks(String input) {
    return input != null && (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0);
  }

  private static String withLabelHint(String preview, String label) {
    String p = Objects.toString(preview, "").trim();
    String l = Objects.toString(label, "").trim();
    if (l.isEmpty()) return p;
    return p + " {label=" + l + "}";
  }

  record PreparedRawLine(String line, String label) {}
}

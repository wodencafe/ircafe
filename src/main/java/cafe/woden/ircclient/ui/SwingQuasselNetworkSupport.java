package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeQuasselNetworkParentResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared Quassel network rendering and token helpers for Swing UI adapters. */
final class SwingQuasselNetworkSupport {

  private SwingQuasselNetworkSupport() {}

  static String renderChoiceLabel(QuasselCoreControlPort.QuasselCoreNetworkSummary summary) {
    if (summary == null) return "(unknown network)";
    String name = Objects.toString(summary.networkName(), "").trim();
    if (name.isEmpty()) name = "network-" + summary.networkId();
    String host = Objects.toString(summary.serverHost(), "").trim();
    int port = summary.serverPort();
    StringBuilder line = new StringBuilder();
    line.append("[").append(summary.networkId()).append("] ").append(name);
    line.append(" - ").append(summary.connected() ? "connected" : "disconnected");
    if (!summary.enabled()) line.append(", disabled");
    if (!host.isEmpty() && port > 0) {
      line.append(" @ ").append(host).append(":").append(port);
      line.append(summary.useTls() ? " tls" : " plain");
    }
    return line.toString();
  }

  static String networkChoiceToken(QuasselCoreControlPort.QuasselCoreNetworkSummary summary) {
    if (summary == null) return "";
    String token =
        summary.networkId() >= 0
            ? Integer.toString(summary.networkId())
            : Objects.toString(summary.networkName(), "").trim();
    return token.isEmpty() ? ("network-" + summary.networkId()) : token;
  }

  static List<ServerTreeQuasselNetworkParentResolver.NetworkPresentation> toNetworkPresentations(
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    if (networks == null || networks.isEmpty()) return List.of();
    LinkedHashMap<String, ServerTreeQuasselNetworkParentResolver.NetworkPresentation> byToken =
        new LinkedHashMap<>();
    for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
      if (summary == null) continue;
      String token = normalizeNetworkToken(networkChoiceToken(summary));
      if (token.isEmpty() || byToken.containsKey(token)) continue;
      String label = Objects.toString(summary.networkName(), "").trim();
      byToken.put(
          token,
          new ServerTreeQuasselNetworkParentResolver.NetworkPresentation(
              token, label, summary.connected(), summary.enabled()));
    }
    return byToken.isEmpty() ? List.of() : List.copyOf(byToken.values());
  }

  static List<String> networkTokenCandidates(
      QuasselCoreControlPort.QuasselCoreNetworkSummary summary) {
    if (summary == null) return List.of();
    ArrayList<String> out = new ArrayList<>(6);
    String idToken = summary.networkId() >= 0 ? Integer.toString(summary.networkId()) : "";
    String name = Objects.toString(summary.networkName(), "").trim();
    String choiceToken = networkChoiceToken(summary);
    addTokenCandidate(out, idToken);
    addTokenCandidate(out, name);
    addTokenCandidate(out, name.toLowerCase(Locale.ROOT));
    addTokenCandidate(out, sanitizeNetworkToken(name));
    addTokenCandidate(out, sanitizeNetworkToken(choiceToken));
    addTokenCandidate(out, choiceToken);
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  static String normalizeNetworkToken(String value) {
    return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
  }

  static String sanitizeNetworkToken(String value) {
    String raw = normalizeNetworkToken(value);
    if (raw.isEmpty()) return "";
    String token = raw.replaceAll("[^a-z0-9._-]+", "-");
    token = token.replaceAll("^-+", "").replaceAll("-+$", "");
    return token;
  }

  private static void addTokenCandidate(List<String> out, String value) {
    String token = normalizeNetworkToken(value);
    if (token.isEmpty() || out.contains(token)) return;
    out.add(token);
  }
}

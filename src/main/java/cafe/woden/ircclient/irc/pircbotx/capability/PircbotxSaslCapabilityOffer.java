package cafe.woden.ircclient.irc.pircbotx.capability;

import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Parsed view of a CAP LS/ACK/NAK token list as it relates to SASL. */
record PircbotxSaslCapabilityOffer(
    boolean continuationOnly, boolean saslOffered, Set<String> offeredMechanismsUpper) {

  static PircbotxSaslCapabilityOffer parse(ImmutableList<String> caps) {
    if (caps == null) {
      return new PircbotxSaslCapabilityOffer(false, false, Set.of());
    }

    if (caps.size() == 1 && "*".equals(normalize(caps.get(0)))) {
      return new PircbotxSaslCapabilityOffer(true, false, Set.of());
    }

    boolean saslOffered = false;
    Set<String> offeredMechanismsUpper = new LinkedHashSet<>();
    for (String cap : caps) {
      String normalized = normalize(cap);
      if (normalized.isEmpty()) {
        continue;
      }

      if (normalized.equalsIgnoreCase("sasl")
          || normalized.toLowerCase(Locale.ROOT).startsWith("sasl=")) {
        saslOffered = true;
        int idx = normalized.indexOf('=');
        if (idx >= 0 && idx + 1 < normalized.length()) {
          String mechList = normalized.substring(idx + 1);
          for (String mechanism : mechList.split(",")) {
            String trimmed = mechanism.trim();
            if (!trimmed.isEmpty()) {
              offeredMechanismsUpper.add(trimmed.toUpperCase(Locale.ROOT));
            }
          }
        }
      }
    }

    return new PircbotxSaslCapabilityOffer(false, saslOffered, Set.copyOf(offeredMechanismsUpper));
  }

  private static String normalize(String cap) {
    String normalized = Objects.toString(cap, "").trim();
    if (normalized.startsWith(":")) {
      normalized = normalized.substring(1).trim();
    }
    return normalized;
  }
}

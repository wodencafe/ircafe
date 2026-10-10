package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Core-compatible default identity payloads for network creation bootstrap. */
final class QuasselCoreIdentityRequests {
  private QuasselCoreIdentityRequests() {}

  static Map<String, Object> defaultPayload(
      String initialNick, QuasselCoreNetworkCreateRequest request) {
    String baseNick =
        sanitizeIdentityNick(
            firstNonBlank(initialNick, request == null ? "" : request.networkName(), "ircafe"));
    String identityName = firstNonBlank(baseNick, "IRCafe");
    String realName = firstNonBlank(initialNick, baseNick);
    String ident = sanitizeIdentityNick(baseNick.toLowerCase(Locale.ROOT));

    LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
    identity.put("identityId", -1);
    identity.put("identityName", identityName);
    identity.put("nicks", List.of(baseNick));
    identity.put("realName", realName);
    identity.put("awayNick", baseNick + "_away");
    identity.put("awayNickEnabled", false);
    identity.put("awayReason", "");
    identity.put("awayReasonEnabled", false);
    identity.put("autoAwayEnabled", false);
    identity.put("autoAwayTime", 10);
    identity.put("autoAwayReason", "");
    identity.put("autoAwayReasonEnabled", false);
    identity.put("detachAwayEnabled", false);
    identity.put("detachAwayReason", "");
    identity.put("detachAwayReasonEnabled", false);
    identity.put("ident", ident);
    identity.put("kickReason", "");
    identity.put("partReason", "");
    identity.put("quitReason", "");
    return Collections.unmodifiableMap(identity);
  }

  private static String sanitizeIdentityNick(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return "ircafe";
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isWhitespace(ch) || ch == '\r' || ch == '\n') {
        out.append('_');
      } else {
        out.append(ch);
      }
    }
    String sanitized = out.toString().trim();
    if (sanitized.isEmpty()) return "ircafe";
    return sanitized;
  }
}

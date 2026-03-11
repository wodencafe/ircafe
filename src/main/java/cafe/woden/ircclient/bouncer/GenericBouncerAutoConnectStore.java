package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Persisted auto-connect preferences for generic bouncer-discovered networks. */
@Component
@ApplicationLayer
public class GenericBouncerAutoConnectStore extends AbstractBouncerAutoConnectStore {

  public GenericBouncerAutoConnectStore(RuntimeConfigStore runtimeConfig) {
    super(runtimeConfig);
    initialize(runtimeConfig.readGenericBouncerAutoConnectRules());
  }

  @Override
  protected String normalizeNetworkKey(String networkName) {
    String raw = Objects.toString(networkName, "").trim();
    if (raw.isEmpty()) return null;

    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '.'
              || c == '_'
              || c == '-';
      out.append(ok ? c : '_');
    }
    String value = out.toString().trim();
    value = value.replaceAll("_+", "_");
    while (value.startsWith("_")) value = value.substring(1);
    while (value.endsWith("_")) value = value.substring(0, value.length() - 1);
    value = value.trim();
    if (value.isEmpty()) return null;
    return value.toLowerCase(Locale.ROOT);
  }

  @Override
  protected void persistAutoConnectRule(String bouncerServerId, String networkKey, boolean enable) {
    runtimeConfig().rememberGenericBouncerAutoConnectNetwork(bouncerServerId, networkKey, enable);
  }
}

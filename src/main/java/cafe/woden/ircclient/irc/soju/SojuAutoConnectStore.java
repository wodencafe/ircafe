package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.irc.bouncer.AbstractBouncerAutoConnectStore;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Persisted auto-connect preferences for Soju-discovered networks.
 *
 * <p>This store keeps a mapping of:
 *
 * <pre>
 *   bouncerServerId -> networkName -> enabled
 * </pre>
 *
 * <p>Network names are canonicalized using Soju's name sanitizer and compared case-insensitively.
 */
@Component
public class SojuAutoConnectStore extends AbstractBouncerAutoConnectStore {

  public SojuAutoConnectStore(SojuProperties props, RuntimeConfigStore runtimeConfig) {
    super(runtimeConfig);
    initialize(props == null ? Map.of() : props.autoConnectCopy());
  }

  /** Convenience alias. */
  public synchronized Map<String, Boolean> rulesForBouncer(String bouncerServerId) {
    return networksForBouncer(bouncerServerId);
  }

  @Override
  protected String normalizeNetworkKey(String networkName) {
    String v = PircbotxSojuParsers.sanitizeNetworkName(networkName);
    v = Objects.toString(v, "").trim();
    if (v.isEmpty()) return null;

    // Make keys stable + user-friendly: collapse runs of '_' and trim leading/trailing '_'.
    v = v.replaceAll("_+", "_");
    while (v.startsWith("_")) v = v.substring(1);
    while (v.endsWith("_")) v = v.substring(0, v.length() - 1);

    v = v.trim();
    if (v.isEmpty()) return null;
    return v.toLowerCase(Locale.ROOT);
  }

  @Override
  protected void persistAutoConnectRule(String bouncerServerId, String networkKey, boolean enable) {
    runtimeConfig().rememberSojuAutoConnectNetwork(bouncerServerId, networkKey, enable);
  }
}

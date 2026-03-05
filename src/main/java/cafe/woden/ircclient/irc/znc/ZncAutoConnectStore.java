package cafe.woden.ircclient.irc.znc;

import cafe.woden.ircclient.bouncer.AbstractBouncerAutoConnectStore;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ZncProperties;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Persisted auto-connect preferences for ZNC-discovered networks.
 *
 * <p>This store keeps a mapping of:
 *
 * <pre>
 *   bouncerServerId -> networkName -> enabled
 * </pre>
 *
 * <p>Network names are canonicalized using ZNC's network segment sanitizer and compared
 * case-insensitively.
 */
@Component
public class ZncAutoConnectStore extends AbstractBouncerAutoConnectStore {

  public ZncAutoConnectStore(ZncProperties props, RuntimeConfigStore runtimeConfig) {
    super(runtimeConfig);
    initialize(props == null ? Map.of() : props.autoConnectCopy());
  }

  @Override
  protected String normalizeNetworkKey(String networkName) {
    String v = ZncEphemeralNaming.sanitizeNetworkSegment(networkName);
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
    runtimeConfig().rememberZncAutoConnectNetwork(bouncerServerId, networkKey, enable);
  }
}

package cafe.woden.ircclient.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ignore configuration.
 *
 * <p>Stored under {@code ircafe.ignore}. Runtime changes are persisted to the runtime YAML
 * via {@link cafe.woden.ircclient.config.RuntimeConfigStore}.
 */
@ConfigurationProperties(prefix = "ircafe.ignore")
public record IgnoreProperties(
    Boolean hardIgnoreIncludesCtcp,
    Map<String, ServerIgnore> servers
) {

  /**
   * Per-server ignore configuration.
   *
   * <p>{@code masks} are traditional (hard) ignore masks.
   *
   * <p>{@code softMasks} are reserved for a future "soft ignore" feature.
   * Soft ignores are stored/persisted the same way as hard ignores, but are not
   * currently applied.
   */
  public record ServerIgnore(List<String> masks, List<String> softMasks) {
    public ServerIgnore {
      if (masks == null) masks = List.of();
      if (softMasks == null) softMasks = List.of();
    }
  }

  public IgnoreProperties {
    if (hardIgnoreIncludesCtcp == null) hardIgnoreIncludesCtcp = Boolean.TRUE;
    if (servers == null) servers = Map.of();
  }
}

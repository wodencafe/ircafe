package cafe.woden.ircclient.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ignore configuration.
 *
 * <p>Stored under {@code ircafe.ignore}. Runtime changes are persisted to the runtime YAML via
 * {@link cafe.woden.ircclient.config.RuntimeConfigStore}.
 */
@ConfigurationProperties(prefix = "ircafe.ignore")
public record IgnoreProperties(
    Boolean hardIgnoreIncludesCtcp,
    Boolean softIgnoreIncludesCtcp,
    Map<String, ServerIgnore> servers) {

  /**
   * Per-server ignore configuration.
   *
   * <p>{@code masks} are traditional (hard) ignore masks.
   *
   * <p>{@code maskLevels} optionally maps a hard-ignore mask to explicit irssi-style levels. When
   * omitted for a mask, that mask behaves as {@code ALL}.
   *
   * <p>{@code maskChannels} optionally maps a hard-ignore mask to a list of channel patterns. When
   * omitted for a mask, the ignore applies in all contexts (channel + private).
   *
   * <p>{@code maskExpiresAt} optionally maps a hard-ignore mask to an absolute expiry time
   * (epoch-millis UTC). Expired entries are pruned at runtime.
   *
   * <p>{@code maskPatterns} optionally maps a hard-ignore mask to a message-text pattern.
   *
   * <p>{@code maskPatternModes} optionally maps a hard-ignore mask to the pattern mode token:
   * {@code glob}, {@code regexp}, or {@code full}.
   *
   * <p>{@code maskReplies} optionally maps a hard-ignore mask to whether reply-targeted messages
   * should also be ignored.
   *
   * <p>{@code softMasks} are soft-ignore masks. Soft-ignored users have inbound messages rendered
   * as spoilers rather than fully dropped.
   *
   * <p>CTCP handling for soft-ignored users is configurable via {@code softIgnoreIncludesCtcp}.
   */
  public record ServerIgnore(
      List<String> masks,
      Map<String, List<String>> maskLevels,
      Map<String, List<String>> maskChannels,
      Map<String, Long> maskExpiresAt,
      Map<String, String> maskPatterns,
      Map<String, String> maskPatternModes,
      Map<String, Boolean> maskReplies,
      List<String> softMasks) {
    public ServerIgnore {
      if (masks == null) masks = List.of();
      if (maskLevels == null) maskLevels = Map.of();
      if (maskChannels == null) maskChannels = Map.of();
      if (maskExpiresAt == null) maskExpiresAt = Map.of();
      if (maskPatterns == null) maskPatterns = Map.of();
      if (maskPatternModes == null) maskPatternModes = Map.of();
      if (maskReplies == null) maskReplies = Map.of();
      if (softMasks == null) softMasks = List.of();
    }
  }

  public IgnoreProperties {
    if (hardIgnoreIncludesCtcp == null) hardIgnoreIncludesCtcp = Boolean.TRUE;
    if (softIgnoreIncludesCtcp == null) softIgnoreIncludesCtcp = Boolean.FALSE;
    if (servers == null) servers = Map.of();
  }
}

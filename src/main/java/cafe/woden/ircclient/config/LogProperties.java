package cafe.woden.ircclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat logging / history persistence configuration.
 *
 */
@ConfigurationProperties(prefix = "ircafe.logging")
public record LogProperties(
    /** Master toggle for persistence logging. Default: false (privacy-first). */
    Boolean enabled,

    /**
     * If true (default), messages that are "soft ignored" (spoiler-covered) are still persisted,
     * but flagged so UI can render them as hidden by default when loading history.
     */
    Boolean logSoftIgnoredLines,

    /** If true (default), keep logs indefinitely. */
    Boolean keepForever,

    /**
     * Optional retention window in days.
     *
     * <p>Ignored when {@code keepForever=true}. If {@code <= 0}, treated as "no retention".
     */
    Integer retentionDays,

    /** Embedded HSQLDB storage configuration. */
    Hsqldb hsqldb
) {

  /** Embedded HSQLDB file settings. */
  public record Hsqldb(
      /** Base filename (no extension). HSQLDB will create .data/.script/.properties files. */
      String fileBaseName,

      /** If true (default), store the DB next to the runtime YAML config file. */
      Boolean nextToRuntimeConfig
  ) {
    public Hsqldb {
      if (fileBaseName == null || fileBaseName.isBlank()) {
        fileBaseName = "ircafe-chatlog";
      }
      if (nextToRuntimeConfig == null) {
        nextToRuntimeConfig = Boolean.TRUE;
      }
    }
  }

  public LogProperties {
    if (enabled == null) enabled = Boolean.FALSE;
    if (logSoftIgnoredLines == null) logSoftIgnoredLines = Boolean.TRUE;
    if (keepForever == null) keepForever = Boolean.TRUE;
    if (retentionDays == null || retentionDays <= 0) retentionDays = 0;
    if (hsqldb == null) hsqldb = new Hsqldb("ircafe-chatlog", Boolean.TRUE);
  }
}

package cafe.woden.ircclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ircafe.logging")
public record LogProperties(
    Boolean enabled,

    Boolean logSoftIgnoredLines,

    Boolean keepForever,

    /**
     * Optional retention window in days.
     *
     * <p>Ignored when {@code keepForever=true}. If {@code <= 0}, treated as "no retention".
     */
    Integer retentionDays,

    Hsqldb hsqldb
) {

  
  public record Hsqldb(
      String fileBaseName,

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

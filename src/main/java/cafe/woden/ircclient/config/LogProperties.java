package cafe.woden.ircclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ircafe.logging")
public record LogProperties(
    Boolean enabled,
    Boolean logSoftIgnoredLines,

    /** Whether PM/query messages are persisted in the local chat log DB. */
    Boolean logPrivateMessages,

    /** Whether PM/query targets are remembered and restored into the chat list. */
    Boolean savePrivateMessageList,
    Boolean keepForever,

    /**
     * Optional retention window in days.
     *
     * <p>Ignored when {@code keepForever=true}. If {@code <= 0}, treated as "no retention".
     */
    Integer retentionDays,
    Integer writerQueueMax,
    Integer writerBatchSize,
    Hsqldb hsqldb) {

  public record Hsqldb(String fileBaseName, Boolean nextToRuntimeConfig) {

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
    if (logPrivateMessages == null) logPrivateMessages = Boolean.TRUE;
    if (savePrivateMessageList == null) savePrivateMessageList = Boolean.TRUE;
    if (keepForever == null) keepForever = Boolean.TRUE;
    if (retentionDays == null || retentionDays <= 0) retentionDays = 0;
    if (writerQueueMax == null || writerQueueMax <= 0) writerQueueMax = 50_000;
    if (writerBatchSize == null || writerBatchSize <= 0) writerBatchSize = 250;
    if (hsqldb == null) hsqldb = new Hsqldb("ircafe-chatlog", Boolean.TRUE);
  }
}

package cafe.woden.ircclient.logging.model;

/** A single persisted log row including the DB identity id. */
public record LogRow(long id, LogLine line) {}

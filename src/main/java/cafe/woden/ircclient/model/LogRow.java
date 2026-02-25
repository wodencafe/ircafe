package cafe.woden.ircclient.model;

/** A single persisted log row including the DB identity id. */
public record LogRow(long id, LogLine line) {}

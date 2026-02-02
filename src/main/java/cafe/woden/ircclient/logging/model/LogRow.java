package cafe.woden.ircclient.logging.model;

/**
 * A single persisted log row including the DB identity id.
 *
 * <p>This is used for deterministic cursor-based paging ("load older messages") where
 * multiple rows may share the same timestamp.
 */
public record LogRow(long id, LogLine line) {
}

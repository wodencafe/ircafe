package cafe.woden.ircclient.logging.model;

/**
 * Direction of a persisted line.
 *
 * <p>We store this as a small VARCHAR in the DB.
 */
public enum LogDirection {
  IN,
  OUT,
  SYSTEM
}

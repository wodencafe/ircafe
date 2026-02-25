package cafe.woden.ircclient.model;

/**
 * Filter actions apply to the transcript view only (they do not delete or prevent logging).
 *
 * <p>MVP: only {@link #HIDE} is supported.
 */
public enum FilterAction {
  HIDE
}

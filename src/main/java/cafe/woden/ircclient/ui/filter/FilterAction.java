package cafe.woden.ircclient.ui.filter;

/**
 * Filter actions apply to the transcript view only (they do not delete or prevent logging).
 *
 * <p>MVP: only {@link #HIDE} is supported.
 */
public enum FilterAction {
  HIDE
}

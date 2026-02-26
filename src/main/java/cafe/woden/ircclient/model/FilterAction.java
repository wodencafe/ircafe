package cafe.woden.ircclient.model;

/**
 * Filter actions apply to the transcript view only (they do not delete or prevent logging).
 *
 * <p>Actions:
 *
 * <ul>
 *   <li>{@link #HIDE}: remove matching lines from the visible transcript.
 *   <li>{@link #DIM}: keep matching lines visible, but de-emphasize them.
 *   <li>{@link #HIGHLIGHT}: keep matching lines visible and visually emphasize them.
 * </ul>
 */
public enum FilterAction {
  HIDE,
  DIM,
  HIGHLIGHT
}

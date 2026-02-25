package cafe.woden.ircclient.logging.history;

/** UI-neutral state for the in-transcript "load older" control. */
public enum LoadOlderControlState {
  READY,
  LOADING,
  EXHAUSTED,
  UNAVAILABLE
}

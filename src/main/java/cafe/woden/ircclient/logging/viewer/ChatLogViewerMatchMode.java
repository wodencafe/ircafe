package cafe.woden.ircclient.logging.viewer;

/** Match mode used by log viewer text filters. */
public enum ChatLogViewerMatchMode {
  ANY,
  CONTAINS,
  GLOB,
  REGEX,
  LIST
}

package cafe.woden.ircclient.app;

/**
 * High-level connection lifecycle state for a server (and for the global UI).
 */
public enum ConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  RECONNECTING,
  DISCONNECTING
}

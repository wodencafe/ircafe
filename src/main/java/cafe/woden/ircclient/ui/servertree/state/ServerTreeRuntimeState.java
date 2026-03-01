package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.app.api.ConnectionState;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Maintains per-server connection/diagnostics/runtime metadata used by the server tree.
 *
 * <p>This keeps mutable runtime state separate from Swing tree wiring.
 */
public final class ServerTreeRuntimeState {

  private final Map<String, ConnectionState> serverStates = new HashMap<>();
  private final Map<String, Boolean> serverDesiredOnline = new HashMap<>();
  private final Map<String, String> serverLastError = new HashMap<>();
  private final Map<String, Long> serverNextRetryAtEpochMs = new HashMap<>();
  private final Map<String, ServerRuntimeMetadata> serverRuntimeMetadata = new HashMap<>();

  private final int capabilityTransitionLogLimit;
  private final Consumer<String> onServerDisconnected;

  public ServerTreeRuntimeState(
      int capabilityTransitionLogLimit, Consumer<String> onServerDisconnected) {
    this.capabilityTransitionLogLimit = Math.max(1, capabilityTransitionLogLimit);
    this.onServerDisconnected = onServerDisconnected == null ? __ -> {} : onServerDisconnected;
  }

  public void markServerKnown(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    serverStates.putIfAbsent(sid, ConnectionState.DISCONNECTED);
  }

  public void removeServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    serverStates.remove(sid);
    serverDesiredOnline.remove(sid);
    serverLastError.remove(sid);
    serverNextRetryAtEpochMs.remove(sid);
    serverRuntimeMetadata.remove(sid);
  }

  public ConnectionState connectionStateForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return ConnectionState.DISCONNECTED;
    return serverStates.getOrDefault(sid, ConnectionState.DISCONNECTED);
  }

  public boolean desiredOnlineForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return Boolean.TRUE.equals(serverDesiredOnline.get(sid));
  }

  public String connectionDiagnosticsTipForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return "";

    String err = Objects.toString(serverLastError.getOrDefault(sid, ""), "").trim();
    Long retryAt = serverNextRetryAtEpochMs.get(sid);

    StringBuilder out = new StringBuilder();
    if (!err.isEmpty()) {
      out.append(" Last error: ").append(err).append('.');
    }
    if (retryAt != null && retryAt > 0) {
      long deltaMs = retryAt - System.currentTimeMillis();
      if (deltaMs <= 0) {
        out.append(" Next retry: imminent.");
      } else {
        long sec = Math.max(1L, deltaMs / 1000L);
        out.append(" Next retry in ").append(sec).append("s.");
      }
    }
    return out.toString();
  }

  public boolean setServerConnectionState(String serverId, ConnectionState state) {
    if (serverId == null) return false;

    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    if (st == ConnectionState.DISCONNECTED) {
      serverStates.remove(serverId);
      onServerDisconnected.accept(serverId);
    } else {
      serverStates.put(serverId, st);
    }
    return true;
  }

  public boolean setServerDesiredOnline(String serverId, boolean desiredOnline) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    boolean prev = Boolean.TRUE.equals(serverDesiredOnline.get(sid));
    if (desiredOnline == prev) return false;

    if (desiredOnline) {
      serverDesiredOnline.put(sid, Boolean.TRUE);
    } else {
      serverDesiredOnline.remove(sid);
    }
    return true;
  }

  public boolean setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    String nextError = Objects.toString(lastError, "").trim();
    Long nextRetry = (nextRetryEpochMs == null || nextRetryEpochMs <= 0L) ? null : nextRetryEpochMs;

    String prevError = Objects.toString(serverLastError.getOrDefault(sid, ""), "").trim();
    Long prevRetry = serverNextRetryAtEpochMs.get(sid);
    if (Objects.equals(prevError, nextError) && Objects.equals(prevRetry, nextRetry)) {
      return false;
    }

    if (nextError.isEmpty()) {
      serverLastError.remove(sid);
    } else {
      serverLastError.put(sid, nextError);
    }

    if (nextRetry == null) {
      serverNextRetryAtEpochMs.remove(sid);
    } else {
      serverNextRetryAtEpochMs.put(sid, nextRetry);
    }

    return true;
  }

  public boolean setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    ServerRuntimeMetadata metadata = metadataForServer(sid);
    boolean changed = false;

    String host = Objects.toString(connectedHost, "").trim();
    if (!host.isEmpty() && !Objects.equals(metadata.connectedHost, host)) {
      metadata.connectedHost = host;
      changed = true;
    }

    if (connectedPort > 0 && metadata.connectedPort != connectedPort) {
      metadata.connectedPort = connectedPort;
      changed = true;
    }

    String nextNick = Objects.toString(nick, "").trim();
    if (!nextNick.isEmpty() && !Objects.equals(metadata.nick, nextNick)) {
      metadata.nick = nextNick;
      changed = true;
    }

    Instant connectedAt =
        at != null ? at : (metadata.connectedAt == null ? Instant.now() : metadata.connectedAt);
    if (!Objects.equals(metadata.connectedAt, connectedAt)) {
      metadata.connectedAt = connectedAt;
      changed = true;
    }

    return changed;
  }

  public boolean setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    String sid = normalizeServerId(serverId);
    String cap = Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (sid.isEmpty() || cap.isEmpty()) return false;

    String sub = Objects.toString(subcommand, "").trim().toUpperCase(java.util.Locale.ROOT);
    ServerRuntimeMetadata metadata = metadataForServer(sid);

    ServerRuntimeMetadata.CapabilityState next;
    if ("DEL".equals(sub)) {
      next = ServerRuntimeMetadata.CapabilityState.REMOVED;
    } else if ("NEW".equals(sub) || "LS".equals(sub)) {
      next = ServerRuntimeMetadata.CapabilityState.AVAILABLE;
    } else {
      next =
          enabled
              ? ServerRuntimeMetadata.CapabilityState.ENABLED
              : ServerRuntimeMetadata.CapabilityState.DISABLED;
    }

    ServerRuntimeMetadata.CapabilityState prev = metadata.ircv3Caps.put(cap, next);
    String effectiveSub = sub.isEmpty() ? "(unknown)" : sub;
    String prevSub = metadata.ircv3CapLastSubcommand.put(cap, effectiveSub);

    boolean stateChanged = !Objects.equals(prev, next);
    boolean subChanged = !Objects.equals(prevSub, effectiveSub);
    if (!(stateChanged || subChanged)) return false;

    metadata.ircv3CapTransitions.add(
        new ServerRuntimeMetadata.CapabilityTransition(Instant.now(), effectiveSub, cap, next));
    int overflow = metadata.ircv3CapTransitions.size() - capabilityTransitionLogLimit;
    if (overflow > 0) {
      metadata.ircv3CapTransitions.subList(0, overflow).clear();
    }
    return true;
  }

  public boolean setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    String sid = normalizeServerId(serverId);
    String key = Objects.toString(tokenName, "").trim().toUpperCase(java.util.Locale.ROOT);
    if (sid.isEmpty() || key.isEmpty()) return false;

    ServerRuntimeMetadata metadata = metadataForServer(sid);
    String val = tokenValue != null ? tokenValue.trim() : null;
    if (val != null && val.isEmpty()) {
      val = "";
    }

    String prev;
    if (val == null) {
      prev = metadata.isupport.remove(key);
    } else {
      prev = metadata.isupport.put(key, val);
    }
    return !Objects.equals(prev, val);
  }

  public boolean setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;

    ServerRuntimeMetadata metadata = metadataForServer(sid);
    boolean changed = false;

    String nextServerName = Objects.toString(serverName, "").trim();
    if (!nextServerName.isEmpty() && !Objects.equals(metadata.serverName, nextServerName)) {
      metadata.serverName = nextServerName;
      changed = true;
    }

    String nextServerVersion = Objects.toString(serverVersion, "").trim();
    if (!nextServerVersion.isEmpty()
        && !Objects.equals(metadata.serverVersion, nextServerVersion)) {
      metadata.serverVersion = nextServerVersion;
      changed = true;
    }

    String nextUserModes = Objects.toString(userModes, "").trim();
    if (!nextUserModes.isEmpty() && !Objects.equals(metadata.userModes, nextUserModes)) {
      metadata.userModes = nextUserModes;
      changed = true;
    }

    String nextChannelModes = Objects.toString(channelModes, "").trim();
    if (!nextChannelModes.isEmpty() && !Objects.equals(metadata.channelModes, nextChannelModes)) {
      metadata.channelModes = nextChannelModes;
      changed = true;
    }

    return changed;
  }

  public ServerRuntimeMetadata metadataForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) sid = "(server)";
    return serverRuntimeMetadata.computeIfAbsent(sid, __ -> new ServerRuntimeMetadata());
  }

  public ServerRuntimeMetadata metadataForServerIfPresent(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return null;
    return serverRuntimeMetadata.get(sid);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}

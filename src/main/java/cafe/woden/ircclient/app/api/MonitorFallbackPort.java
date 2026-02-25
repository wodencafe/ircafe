package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing port for monitor fallback behavior when MONITOR is unavailable. */
@ApplicationLayer
public interface MonitorFallbackPort {

  boolean isFallbackActive(String serverId);

  boolean shouldSuppressIsonServerResponse(String serverId);

  void requestImmediateRefresh(String serverId);
}

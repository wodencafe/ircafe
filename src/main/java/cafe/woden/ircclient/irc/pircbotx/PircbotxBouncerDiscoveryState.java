package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Internal state holder for per-connection bouncer discovery and ZNC session metadata. */
final class PircbotxBouncerDiscoveryState {
  private final AtomicBoolean zncPlaybackRequestedThisSession = new AtomicBoolean(false);
  private final AtomicBoolean zncListNetworksRequestedThisSession = new AtomicBoolean(false);
  private final AtomicBoolean zncListNetworksCaptureActive = new AtomicBoolean(false);
  private final AtomicLong zncListNetworksCaptureStartedMs = new AtomicLong(0);
  private final Map<String, BouncerDiscoveredNetwork> zncNetworksByNameLower =
      new ConcurrentHashMap<>();
  private final AtomicBoolean zncDetected = new AtomicBoolean(false);
  private final AtomicBoolean zncDetectedLogged = new AtomicBoolean(false);
  private final AtomicReference<String> zncBaseUser = new AtomicReference<>("");
  private final AtomicReference<String> zncClientId = new AtomicReference<>("");
  private final AtomicReference<String> zncNetwork = new AtomicReference<>("");
  private final AtomicBoolean sojuListNetworksRequestedThisSession = new AtomicBoolean(false);
  private final AtomicReference<String> sojuBouncerNetId = new AtomicReference<>("");
  private final Map<String, BouncerDiscoveredNetwork> sojuNetworksByNetId =
      new ConcurrentHashMap<>();
  private final Map<String, BouncerDiscoveredNetwork> genericBouncerNetworksById =
      new ConcurrentHashMap<>();

  boolean isZncDetected() {
    return zncDetected.get();
  }

  boolean markZncDetected() {
    return zncDetected.compareAndSet(false, true);
  }

  boolean markZncDetectionLogged() {
    return zncDetectedLogged.compareAndSet(false, true);
  }

  boolean zncDetectionLogged() {
    return zncDetectedLogged.get();
  }

  void clearZncDetection() {
    zncDetected.set(false);
    zncDetectedLogged.set(false);
  }

  String zncBaseUser() {
    return zncBaseUser.get();
  }

  String zncClientId() {
    return zncClientId.get();
  }

  String zncNetwork() {
    return zncNetwork.get();
  }

  void setZncLoginContext(String baseUser, String clientId, String network) {
    zncBaseUser.set(Objects.toString(baseUser, "").trim());
    zncClientId.set(Objects.toString(clientId, "").trim());
    zncNetwork.set(Objects.toString(network, "").trim());
  }

  void clearZncLoginContext() {
    setZncLoginContext("", "", "");
  }

  boolean beginZncPlaybackRequest() {
    return !zncPlaybackRequestedThisSession.getAndSet(true);
  }

  void clearZncPlaybackRequest() {
    zncPlaybackRequestedThisSession.set(false);
  }

  boolean zncPlaybackRequestedThisSession() {
    return zncPlaybackRequestedThisSession.get();
  }

  boolean beginZncListNetworksRequest() {
    return !zncListNetworksRequestedThisSession.getAndSet(true);
  }

  void beginZncListNetworksCapture(long startedAtMs) {
    zncListNetworksCaptureActive.set(true);
    zncListNetworksCaptureStartedMs.set(startedAtMs);
    zncNetworksByNameLower.clear();
  }

  boolean isZncListNetworksCaptureActive() {
    return zncListNetworksCaptureActive.get();
  }

  long zncListNetworksCaptureStartedAtMs() {
    return zncListNetworksCaptureStartedMs.get();
  }

  void finishZncListNetworksCapture() {
    zncListNetworksCaptureActive.set(false);
  }

  void clearZncListNetworksRequest() {
    zncListNetworksRequestedThisSession.set(false);
  }

  boolean zncListNetworksRequestedThisSession() {
    return zncListNetworksRequestedThisSession.get();
  }

  void clearZncDiscoveredNetworks() {
    zncNetworksByNameLower.clear();
  }

  int zncDiscoveredNetworkCount() {
    return zncNetworksByNameLower.size();
  }

  BouncerDiscoveredNetwork zncDiscoveredNetwork(String key) {
    return zncNetworksByNameLower.get(key);
  }

  void storeZncDiscoveredNetwork(String key, BouncerDiscoveredNetwork network) {
    if (key != null && network != null) {
      zncNetworksByNameLower.put(key, network);
    }
  }

  boolean beginSojuListNetworksRequest() {
    return !sojuListNetworksRequestedThisSession.getAndSet(true);
  }

  void clearSojuListNetworksRequest() {
    sojuListNetworksRequestedThisSession.set(false);
  }

  boolean sojuListNetworksRequestedThisSession() {
    return sojuListNetworksRequestedThisSession.get();
  }

  void clearSojuDiscoveredNetworks() {
    sojuNetworksByNetId.clear();
  }

  BouncerDiscoveredNetwork sojuDiscoveredNetwork(String netId) {
    return sojuNetworksByNetId.get(netId);
  }

  void storeSojuDiscoveredNetwork(String netId, BouncerDiscoveredNetwork network) {
    if (netId != null && network != null) {
      sojuNetworksByNetId.put(netId, network);
    }
  }

  boolean hasSojuDiscoveredNetwork(String netId) {
    return netId != null && sojuNetworksByNetId.containsKey(netId);
  }

  boolean hasAnySojuDiscoveredNetworks() {
    return !sojuNetworksByNetId.isEmpty();
  }

  String sojuBouncerNetId() {
    return sojuBouncerNetId.get();
  }

  void setSojuBouncerNetId(String netId) {
    sojuBouncerNetId.set(Objects.toString(netId, "").trim());
  }

  void clearSojuBouncerNetId() {
    sojuBouncerNetId.set("");
  }

  void clearGenericBouncerDiscoveredNetworks() {
    genericBouncerNetworksById.clear();
  }

  BouncerDiscoveredNetwork genericBouncerDiscoveredNetwork(String key) {
    return genericBouncerNetworksById.get(key);
  }

  void storeGenericBouncerDiscoveredNetwork(String key, BouncerDiscoveredNetwork network) {
    if (key != null && network != null) {
      genericBouncerNetworksById.put(key, network);
    }
  }

  boolean hasGenericBouncerDiscoveredNetwork(String key) {
    return key != null && genericBouncerNetworksById.containsKey(key);
  }

  boolean hasAnyGenericBouncerDiscoveredNetworks() {
    return !genericBouncerNetworksById.isEmpty();
  }
}

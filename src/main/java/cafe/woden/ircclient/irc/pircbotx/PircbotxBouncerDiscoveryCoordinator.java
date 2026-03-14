package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.GenericBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.irc.soju.SojuBouncerDiscoveryAdapter;
import cafe.woden.ircclient.irc.soju.SojuBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.znc.ZncBouncerDiscoveryAdapter;
import cafe.woden.ircclient.irc.znc.ZncBouncerNetworkMappingStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns bouncer discovery side effects for a single classic IRC connection.
 *
 * <p>This keeps the bridge listener focused on event translation instead of discovery state,
 * capture flow, and discovery event fan-out.
 */
final class PircbotxBouncerDiscoveryCoordinator {
  private static final Logger log =
      LoggerFactory.getLogger(PircbotxBouncerDiscoveryCoordinator.class);

  @FunctionalInterface
  private interface UnknownLineDiscoveryParser {
    BouncerDiscoveredNetwork parse(String originServerId, String rawLine);
  }

  private record UnknownLineDiscoveryAdapter(String adapterId, UnknownLineDiscoveryParser parser) {}

  private final String serverId;
  private final PircbotxConnectionState conn;
  private final boolean sojuDiscoveryEnabled;
  private final boolean zncDiscoveryEnabled;
  private final BouncerBackendRegistry bouncerBackends;
  private final BouncerDiscoveryEventPort bouncerDiscoveryEvents;
  private final SojuBouncerDiscoveryAdapter sojuDiscoveryAdapter =
      new SojuBouncerDiscoveryAdapter();
  private final ZncBouncerDiscoveryAdapter zncDiscoveryAdapter = new ZncBouncerDiscoveryAdapter();
  private final GenericBouncerDiscoveryAdapter genericDiscoveryAdapter =
      new GenericBouncerDiscoveryAdapter();
  private final List<UnknownLineDiscoveryAdapter> unknownLineDiscoveryAdapters;

  PircbotxBouncerDiscoveryCoordinator(
      String serverId,
      PircbotxConnectionState conn,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled,
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.sojuDiscoveryEnabled = sojuDiscoveryEnabled;
    this.zncDiscoveryEnabled = zncDiscoveryEnabled;
    this.bouncerBackends = Objects.requireNonNull(bouncerBackends, "bouncerBackends");
    this.bouncerDiscoveryEvents =
        bouncerDiscoveryEvents == null ? BouncerDiscoveryEventPort.noOp() : bouncerDiscoveryEvents;
    this.unknownLineDiscoveryAdapters = buildUnknownLineDiscoveryAdapters(sojuDiscoveryEnabled);
  }

  boolean maybeCaptureZncListNetworks(String fromNick, String text) {
    if (!zncDiscoveryEnabled) return false;
    if (!conn.zncListNetworksCaptureActive.get()) return false;

    String from = Objects.toString(fromNick, "").trim();
    if (!"*status".equalsIgnoreCase(from)) return false;

    long started = conn.zncListNetworksCaptureStartedMs.get();
    if (started > 0) {
      long age = System.currentTimeMillis() - started;
      // Safety valve: don't keep suppressing *status output forever if ZNC output format changes.
      if (age > 15_000L) {
        conn.zncListNetworksCaptureActive.set(false);
        return false;
      }
    }

    try {
      BouncerDiscoveredNetwork network = zncDiscoveryAdapter.parseListNetworksRow(serverId, text);
      if (network != null) {
        return upsertDiscoveredNetwork(network, "znc-listnetworks");
      }

      if (zncDiscoveryAdapter.looksLikeListNetworksDoneLine(text)) {
        conn.zncListNetworksCaptureActive.set(false);
        log.info(
            "[{}] znc: finished ListNetworks capture ({} networks)",
            serverId,
            conn.zncNetworksByNameLower.size());
        return true;
      }

      // Capture in progress: suppress all *status output (borders, headings, etc).
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean maybeCaptureUnknownLine(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return false;
    for (UnknownLineDiscoveryAdapter adapter : unknownLineDiscoveryAdapters) {
      BouncerDiscoveredNetwork network;
      try {
        network = adapter.parser().parse(serverId, rawLine);
      } catch (Exception e) {
        log.debug("[{}] bouncer discovery adapter '{}' threw", serverId, adapter.adapterId(), e);
        continue;
      }
      if (network == null) continue;
      if (upsertDiscoveredNetwork(network, adapter.adapterId())) {
        return true;
      }
    }
    return false;
  }

  void maybeRequestZncNetworks(PircBotX bot) {
    if (bot == null) return;
    if (!zncDiscoveryEnabled) return;
    if (!conn.zncDetected.get()) return;

    String net = conn.zncNetwork.get();
    if (net != null && !net.isBlank()) return;

    if (conn.zncListNetworksRequestedThisSession.getAndSet(true)) return;

    try {
      conn.zncListNetworksCaptureActive.set(true);
      conn.zncListNetworksCaptureStartedMs.set(System.currentTimeMillis());
      conn.zncNetworksByNameLower.clear();

      bot.sendIRC().message("*status", "ListNetworks");
      log.info("[{}] znc: requested network list (*status ListNetworks)", serverId);
    } catch (Exception ex) {
      conn.zncListNetworksCaptureActive.set(false);
      conn.zncListNetworksRequestedThisSession.set(false);
      log.warn("[{}] znc: failed to request network list", serverId, ex);
    }
  }

  void maybeRequestSojuNetworks(PircBotX bot) {
    if (bot == null) return;
    if (!sojuDiscoveryEnabled) return;
    if (!conn.sojuBouncerNetworksCapAcked.get()) return;

    String netId = conn.sojuBouncerNetId.get();
    if (netId != null && !netId.isBlank()) return;

    if (conn.sojuListNetworksRequestedThisSession.getAndSet(true)) return;

    try {
      bot.sendRaw().rawLine("BOUNCER LISTNETWORKS");
      log.info("[{}] soju: requested bouncer network list (BOUNCER LISTNETWORKS)", serverId);
    } catch (Exception ex) {
      conn.sojuListNetworksRequestedThisSession.set(false);
      log.warn("[{}] soju: failed to request bouncer network list", serverId, ex);
    }
  }

  void maybeMarkZncDetected(String via, String detail) {
    if (!conn.zncDetected.compareAndSet(false, true)) return;
    String extra = detail == null ? "" : detail;
    log.info("[{}] znc: detected via {} {}", serverId, via, extra);

    if (conn.zncDetectedLogged.compareAndSet(false, true)) {
      String baseUser = conn.zncBaseUser.get();
      String client = conn.zncClientId.get();
      String net = conn.zncNetwork.get();
      boolean hasNet = net != null && !net.isBlank();
      log.info(
          "[{}] znc: login parsed baseUser='{}' clientId='{}' network='{}' (controlSession={})",
          serverId,
          baseUser == null ? "" : baseUser,
          client == null ? "" : client,
          net == null ? "" : net,
          !hasNet);
    }
  }

  void observeSojuBouncerNetId(String maybeSojuNetId) {
    if (maybeSojuNetId == null || maybeSojuNetId.isBlank()) return;
    String prev = conn.sojuBouncerNetId.get();
    if (prev == null || prev.isBlank()) {
      conn.sojuBouncerNetId.set(maybeSojuNetId);
      log.info(
          "[{}] soju: BOUNCER_NETID={} (connection is bound to a bouncer network)",
          serverId,
          maybeSojuNetId);
    } else if (!Objects.equals(prev, maybeSojuNetId)) {
      conn.sojuBouncerNetId.set(maybeSojuNetId);
      log.info("[{}] soju: BOUNCER_NETID changed {} -> {}", serverId, prev, maybeSojuNetId);
    }
  }

  void onDisconnect() {
    for (String backendId : bouncerBackends.backendIds()) {
      notifyOriginDisconnected(backendId);
    }

    try {
      conn.sojuNetworksByNetId.clear();
      conn.sojuListNetworksRequestedThisSession.set(false);
      conn.sojuBouncerNetId.set("");
      conn.sojuBouncerNetworksCapAcked.set(false);
    } catch (Exception ignored) {
    }

    try {
      conn.zncListNetworksRequestedThisSession.set(false);
    } catch (Exception ignored) {
    }
    try {
      conn.genericBouncerNetworksById.clear();
    } catch (Exception ignored) {
    }
  }

  private List<UnknownLineDiscoveryAdapter> buildUnknownLineDiscoveryAdapters(boolean sojuEnabled) {
    ArrayList<UnknownLineDiscoveryAdapter> adapters = new ArrayList<>();
    if (sojuEnabled) {
      adapters.add(
          new UnknownLineDiscoveryAdapter(
              SojuBouncerNetworkMappingStrategy.BACKEND_ID,
              sojuDiscoveryAdapter::parseBouncerNetworkLine));
    }
    adapters.add(
        new UnknownLineDiscoveryAdapter(
            GenericBouncerNetworkMappingStrategy.BACKEND_ID,
            genericDiscoveryAdapter::parseNetworkLine));
    return List.copyOf(adapters);
  }

  private boolean upsertDiscoveredNetwork(
      BouncerDiscoveredNetwork network, String sourceAdapterId) {
    if (network == null) return false;
    String backend = normalizeLower(network.backendId());
    if (backend == null) return false;

    if (SojuBouncerNetworkMappingStrategy.BACKEND_ID.equals(backend)) {
      if (!sojuDiscoveryEnabled) return false;
      return upsertSojuDiscoveredNetwork(network, sourceAdapterId);
    }
    if (ZncBouncerNetworkMappingStrategy.BACKEND_ID.equals(backend)) {
      if (!zncDiscoveryEnabled) return false;
      return upsertZncDiscoveredNetwork(network, sourceAdapterId);
    }
    return upsertGenericDiscoveredNetwork(network, sourceAdapterId);
  }

  private boolean upsertSojuDiscoveredNetwork(BouncerDiscoveredNetwork network, String source) {
    String netId = normalize(network.networkId());
    if (netId == null) return false;

    BouncerDiscoveredNetwork prev = conn.sojuNetworksByNetId.putIfAbsent(netId, network);
    boolean changed = prev == null || !prev.equals(network);
    if (changed && prev != null) {
      conn.sojuNetworksByNetId.put(netId, network);
    }
    if (prev == null) {
      log.info(
          "[{}] soju: discovered network netId={} name={} (via {})",
          serverId,
          netId,
          network.displayName(),
          source);
    } else if (changed) {
      log.info(
          "[{}] soju: updated network netId={} name={} (via {})",
          serverId,
          netId,
          network.displayName(),
          source);
    }
    if (changed) {
      emitDiscoveredNetwork(network);
    }
    return true;
  }

  private boolean upsertZncDiscoveredNetwork(BouncerDiscoveredNetwork network, String source) {
    String key = normalizeLower(network.networkId());
    if (key == null) {
      key = normalizeLower(network.displayName());
    }
    if (key == null) return false;

    BouncerDiscoveredNetwork prev = conn.zncNetworksByNameLower.putIfAbsent(key, network);
    boolean changed = prev == null || !prev.equals(network);
    if (changed && prev != null) {
      conn.zncNetworksByNameLower.put(key, network);
    }
    String onIrc = network.attributes().getOrDefault("onIrc", "");
    if (prev == null) {
      log.info(
          "[{}] znc: discovered network id={} name={} (onIrc={}, via={})",
          serverId,
          key,
          network.displayName(),
          onIrc,
          source);
    } else if (changed) {
      log.info(
          "[{}] znc: updated network id={} name={} (onIrc={}, via={})",
          serverId,
          key,
          network.displayName(),
          onIrc,
          source);
    }
    if (changed) {
      emitDiscoveredNetwork(network);
    }
    return true;
  }

  private boolean upsertGenericDiscoveredNetwork(BouncerDiscoveredNetwork network, String source) {
    String key = normalizeLower(network.networkId());
    if (key == null) return false;

    BouncerDiscoveredNetwork prev = conn.genericBouncerNetworksById.putIfAbsent(key, network);
    boolean changed = prev == null || !prev.equals(network);
    if (changed && prev != null) {
      conn.genericBouncerNetworksById.put(key, network);
    }
    if (prev == null) {
      log.info(
          "[{}] generic bouncer: discovered network id={} name={} backend={} (via {})",
          serverId,
          key,
          network.displayName(),
          network.backendId(),
          source);
    } else if (changed) {
      log.info(
          "[{}] generic bouncer: updated network id={} name={} backend={} (via {})",
          serverId,
          key,
          network.displayName(),
          network.backendId(),
          source);
    }
    if (changed) {
      emitDiscoveredNetwork(network);
    }
    return true;
  }

  private void emitDiscoveredNetwork(BouncerDiscoveredNetwork network) {
    try {
      bouncerDiscoveryEvents.onNetworkDiscovered(network);
    } catch (Exception e) {
      log.debug(
          "[{}] {}: network discovered handler threw",
          serverId,
          Objects.toString(network.backendId(), "bouncer"),
          e);
    }
  }

  private void notifyOriginDisconnected(String backendId) {
    try {
      bouncerDiscoveryEvents.onOriginDisconnected(backendId, serverId);
    } catch (Exception e) {
      log.debug("[{}] {} disconnect cleanup failed: {}", serverId, backendId, e.toString());
    }
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v;
  }

  private static String normalizeLower(String value) {
    String v = normalize(value);
    return v == null ? null : v.toLowerCase(Locale.ROOT);
  }
}

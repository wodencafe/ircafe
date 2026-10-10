package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.networkIdFromStateMap;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseInt;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decodes native network lifecycle RPCs through session observation/removal callbacks. */
final class QuasselCoreNetworkLifecycleTranslator {
  private static final Logger log =
      LoggerFactory.getLogger(QuasselCoreNetworkLifecycleTranslator.class);

  private final String serverId;
  private final Supplier<String> claimCreatedName;
  private final BiConsumer<Integer, String> observeNetwork;
  private final IntConsumer forgetNetwork;
  private final BiConsumer<Integer, Map<?, ?>> observeState;

  QuasselCoreNetworkLifecycleTranslator(
      String serverId,
      Supplier<String> claimCreatedName,
      BiConsumer<Integer, String> observeNetwork,
      IntConsumer forgetNetwork,
      BiConsumer<Integer, Map<?, ?>> observeState) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.claimCreatedName = Objects.requireNonNull(claimCreatedName, "claimCreatedName");
    this.observeNetwork = Objects.requireNonNull(observeNetwork, "observeNetwork");
    this.forgetNetwork = Objects.requireNonNull(forgetNetwork, "forgetNetwork");
    this.observeState = Objects.requireNonNull(observeState, "observeState");
  }

  void handleRpc(String slotName, List<Object> params) {
    String slot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    if (slot.isEmpty() || !slot.contains("network")) return;
    if (params == null || params.isEmpty()) return;

    boolean remove = slot.contains("remove") || slot.contains("deleted");
    boolean createLike = !remove && (slot.contains("create") || slot.contains("added"));
    log.debug(
        "Observing Quassel network lifecycle RPC: serverId={}, slot={}, remove={}, params={}",
        serverId,
        slotName,
        remove,
        params);
    for (Object param : params) {
      observeParam(param, remove, createLike);
    }
  }

  private void observeParam(Object raw, boolean remove, boolean createLike) {
    if (raw == null) return;

    if (raw instanceof List<?> list) {
      for (Object value : list) {
        observeParam(value, remove, createLike);
      }
      return;
    }

    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      String type = Objects.toString(userType.typeName(), "").trim();
      Object value = userType.value();
      if ("NetworkInfo".equals(type) && value instanceof Map<?, ?> map) {
        observeParam(map, remove, createLike);
        return;
      }
      if ("NetworkId".equals(type)) {
        int networkId = tryParseInt(value);
        if (networkId < 0) return;
        if (remove) {
          log.debug(
              "Quassel network lifecycle RPC removed network by id: serverId={}, networkId={}",
              serverId,
              networkId);
          forgetNetwork.accept(networkId);
        } else {
          String observedName = createLike ? claimCreatedName.get() : "";
          log.debug(
              "Quassel network lifecycle RPC observed network by id: serverId={}, networkId={}, nameHint={}",
              serverId,
              networkId,
              observedName);
          observeNetwork.accept(networkId, observedName);
        }
        return;
      }
      observeParam(value, remove, createLike);
      return;
    }

    if (raw instanceof Map<?, ?> map) {
      int networkId = networkIdFromStateMap(map, -1);
      if (remove && networkId >= 0) {
        log.debug(
            "Quassel network lifecycle RPC removed network by map: serverId={}, networkId={}, map={}",
            serverId,
            networkId,
            map);
        forgetNetwork.accept(networkId);
        return;
      }
      String networkName =
          firstNonBlank(
              mapValueIgnoreCase(map, "networkName"),
              mapValueIgnoreCase(map, "networkname"),
              mapValueIgnoreCase(map, "name"));
      log.debug(
          "Quassel network lifecycle RPC observed network map: serverId={}, networkId={}, networkName={}, map={}",
          serverId,
          networkId,
          networkName,
          map);
      observeNetwork.accept(networkId, networkName);
      observeState.accept(networkId, map);
      return;
    }

    int networkId = tryParseInt(raw);
    if (networkId < 0) return;
    if (remove) {
      forgetNetwork.accept(networkId);
    } else {
      String observedName = createLike ? claimCreatedName.get() : "";
      observeNetwork.accept(networkId, observedName);
    }
  }
}

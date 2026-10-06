package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.normalizeMembershipKey;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Session-owned membership reconciliation shared by native joins, lifecycle sync and buffers. */
final class QuasselCoreChannelMembership {
  private static final int BUFFER_CHANNEL = 0x02;
  private static final String NETWORK_ADD_IRC_CHANNEL_SLOT = "addircchannel";
  private static final String NETWORK_REMOVE_IRC_CHANNEL_SLOT = "removeircchannel";

  // Membership is live connection state, not an evictable cache. Network/session teardown clears
  // it.
  private final Set<String> joinedKeys = ConcurrentHashMap.newKeySet();
  private final Consumer<IrcEvent> events;

  QuasselCoreChannelMembership(Consumer<IrcEvent> events) {
    this.events = Objects.requireNonNull(events, "events");
  }

  void observeJoin(Instant at, String target, int networkId) {
    String key = normalizeMembershipKey(target, networkId);
    if (!key.isEmpty() && joinedKeys.add(key)) {
      events.accept(new IrcEvent.JoinedChannel(at, target));
    }
  }

  void leave(String target, int networkId) {
    String key = normalizeMembershipKey(target, networkId);
    if (!key.isEmpty()) joinedKeys.remove(key);
  }

  void forgetNetwork(int networkId) {
    if (networkId < 0) return;
    String prefix = networkId + "|";
    for (String key : new ArrayList<>(joinedKeys)) {
      if (key.startsWith(prefix)) joinedKeys.remove(key);
    }
  }

  void clear() {
    joinedKeys.clear();
  }

  void reconcileNetwork(
      int networkId,
      boolean connected,
      Collection<BufferInfoValue> buffers,
      BiFunction<String, Integer, String> qualifyTarget,
      BiConsumer<String, Integer> observeHint) {
    if (networkId < 0) return;
    if (!connected) {
      forgetNetwork(networkId);
      return;
    }
    Instant now = Instant.now();
    for (BufferInfoValue info : buffers) {
      if (info != null && info.networkId() == networkId) {
        observeBuffer(info, now, qualifyTarget, observeHint);
      }
    }
  }

  void observeBuffer(
      BufferInfoValue info,
      Instant at,
      BiFunction<String, Integer, String> qualifyTarget,
      BiConsumer<String, Integer> observeHint) {
    if (info == null) return;
    String channel = Objects.toString(info.bufferName(), "").trim();
    if (channel.isEmpty()) return;
    if ((info.typeBits() & BUFFER_CHANNEL) == 0 && !looksLikeChannel(channel)) return;
    int networkId = info.networkId();
    String qualified = qualifyTarget.apply(channel, networkId);
    if (qualified.isEmpty()) return;
    // Buffer observations refresh routing even when the join was already emitted.
    observeHint.accept(qualified, networkId);
    observeJoin(at == null ? Instant.now() : at, qualified, networkId);
  }

  void observeNetworkLifecycle(
      String objectName,
      String slotName,
      List<Object> values,
      BiFunction<String, Integer, String> qualifyTarget) {
    String normalizedSlot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    if (normalizedSlot.isEmpty()) return;

    boolean isAdd = normalizedSlot.contains(NETWORK_ADD_IRC_CHANNEL_SLOT);
    boolean isRemove = normalizedSlot.contains(NETWORK_REMOVE_IRC_CHANNEL_SLOT);
    if (!isAdd && !isRemove) return;

    int networkId = parseNetworkId(objectName);
    ArrayList<String> candidates = new ArrayList<>();
    collectChannelNamesFromLifecyclePayload(values, candidates);
    if (candidates.isEmpty()) {
      String fallback = parseObjectLeafToken(objectName);
      if (looksLikeChannel(fallback)) {
        candidates.add(fallback);
      }
    }
    if (candidates.isEmpty()) return;

    LinkedHashSet<String> uniqueChannels = new LinkedHashSet<>();
    for (String raw : candidates) {
      String channel = Objects.toString(raw, "").trim();
      if (looksLikeChannel(channel)) {
        uniqueChannels.add(channel);
      }
    }
    if (uniqueChannels.isEmpty()) return;

    Instant now = Instant.now();
    for (String channel : uniqueChannels) {
      String qualified = qualifyTarget.apply(channel, networkId);
      if (isAdd) {
        observeJoin(now, qualified, networkId);
      } else {
        leave(qualified, networkId);
      }
    }
  }

  private static void collectChannelNamesFromLifecyclePayload(Object raw, List<String> out) {
    if (raw == null || out == null) return;
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectChannelNamesFromLifecyclePayload(value, out);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      collectChannelNamesFromLifecyclePayload(userType.value(), out);
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.BufferInfoValue info) {
      String channel = Objects.toString(info.bufferName(), "").trim();
      if (!channel.isEmpty()) out.add(channel);
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      String channel =
          firstNonBlank(
              mapValueIgnoreCase(map, "name"),
              mapValueIgnoreCase(map, "channel"),
              mapValueIgnoreCase(map, "bufferName"));
      if (!channel.isEmpty()) out.add(channel);
      return;
    }
    if (raw instanceof byte[] bytes) {
      String channel = new String(bytes, StandardCharsets.UTF_8).trim();
      if (!channel.isEmpty()) out.add(channel);
      return;
    }
    String channel = Objects.toString(raw, "").trim();
    if (!channel.isEmpty()) out.add(channel);
  }

  private static String parseObjectLeafToken(String objectName) {
    String token = Objects.toString(objectName, "").trim();
    if (token.isEmpty()) return "";
    int slash = token.lastIndexOf('/');
    if (slash >= 0 && slash < token.length() - 1) {
      token = token.substring(slash + 1).trim();
    }
    return token;
  }
}

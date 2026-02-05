package cafe.woden.ircclient.model;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class UserListStore {

  private final Map<String, Map<String, List<NickInfo>>> usersByServerAndChannel = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Set<String>>> lowerNickSetByServerAndChannel = new ConcurrentHashMap<>();
  private final Map<String, Map<String, String>> hostmaskByServerAndNickLower = new ConcurrentHashMap<>();

  private final Map<String, Map<String, AwayState>> awayStateByServerAndNickLower = new ConcurrentHashMap<>();

  private final Map<String, Map<String, String>> awayMessageByServerAndNickLower = new ConcurrentHashMap<>();

  private static String norm(String s) {
    return Objects.toString(s, "").trim();
  }

  private static String nickKey(String nick) {
    String n = norm(nick);
    return n.isEmpty() ? "" : n.toLowerCase(Locale.ROOT);
  }

  private static boolean isUsefulHostmask(String hostmask) {
    String hm = norm(hostmask);
    if (hm.isEmpty()) return false;

    int bang = hm.indexOf('!');
    int at = hm.indexOf('@');
    if (bang <= 0 || at <= bang + 1 || at >= hm.length() - 1) return false;

    String ident = hm.substring(bang + 1, at).trim();
    String host = hm.substring(at + 1).trim();

    boolean identUnknown = ident.isEmpty() || "*".equals(ident);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(identUnknown && hostUnknown);
  }

  private static boolean isKnownAway(AwayState state) {
    return state != null && state != AwayState.UNKNOWN;
  }

  public String getLearnedHostmask(String serverId, String nick) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return null;
    String nk = nickKey(nick);
    if (nk.isEmpty()) return null;

    Map<String, String> byNick = hostmaskByServerAndNickLower.get(sid);
    if (byNick == null) return null;
    String hm = byNick.get(nk);
    hm = norm(hm);
    return hm.isEmpty() ? null : hm;
  }

  private static String normalizeAwayMessage(AwayState state, String msg) {
    if (state == null || state != AwayState.AWAY) return null;
    String m = norm(msg);
    return m.isEmpty() ? null : m;
  }

  public List<NickInfo> get(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return Collections.emptyList();

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel == null) return Collections.emptyList();
    return byChannel.getOrDefault(ch, Collections.emptyList());
  }
  public Set<String> getLowerNickSet(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return Collections.emptySet();

    Map<String, Set<String>> byChannel = lowerNickSetByServerAndChannel.get(sid);
    if (byChannel == null) return Collections.emptySet();
    return byChannel.getOrDefault(ch, Collections.emptySet());
  }

  public void put(String serverId, String channel, List<NickInfo> nicks) {
    String sid = norm(serverId);
    String ch = norm(channel);
    if (sid.isEmpty() || ch.isEmpty()) return;

    // Merge any learned hostmasks / away-state into the roster, but do not overwrite a useful value
    // provided by the server.
    Map<String, String> knownHostmasks = hostmaskByServerAndNickLower.getOrDefault(sid, Map.of());
    Map<String, AwayState> knownAway = awayStateByServerAndNickLower.getOrDefault(sid, Map.of());
    Map<String, String> knownAwayMsg = awayMessageByServerAndNickLower.getOrDefault(sid, Map.of());
    List<NickInfo> safe;
    if (nicks == null || nicks.isEmpty() || (knownHostmasks.isEmpty() && knownAway.isEmpty() && knownAwayMsg.isEmpty())) {
      safe = nicks == null ? List.of() : List.copyOf(nicks);
    } else {
      java.util.ArrayList<NickInfo> merged = new java.util.ArrayList<>(nicks.size());
      for (NickInfo ni : nicks) {
        if (ni == null) {
          merged.add(null);
          continue;
        }
        String nk = nickKey(ni.nick());

        boolean changed = false;

        // Hostmask merge
        String hm = norm(ni.hostmask());
        String hmNext = hm;
        if ((hm.isEmpty() || !isUsefulHostmask(hm)) && !nk.isEmpty()) {
          String learned = knownHostmasks.get(nk);
          if (isUsefulHostmask(learned)) {
            hmNext = learned;
            changed = true;
          }
        }

        // Away state merge
        AwayState as = ni.awayState();
        AwayState asNext = (as == null) ? AwayState.UNKNOWN : as;
        if (!isKnownAway(asNext) && !nk.isEmpty()) {
          AwayState learned = knownAway.get(nk);
          if (isKnownAway(learned)) {
            asNext = learned;
            changed = true;
          }
        }

        // Away message merge (only meaningful for AWAY)
        String am = ni.awayMessage();
        String amNext = normalizeAwayMessage(asNext, am);
        if (asNext == AwayState.AWAY && amNext == null && !nk.isEmpty()) {
          String learnedMsg = knownAwayMsg.get(nk);
          learnedMsg = normalizeAwayMessage(asNext, learnedMsg);
          if (learnedMsg != null) {
            amNext = learnedMsg;
            changed = true;
          }
        }
        if (asNext != AwayState.AWAY && amNext != null) {
          amNext = null;
          changed = true;
        }

        if (changed) {
          merged.add(new NickInfo(ni.nick(), ni.prefix(), hmNext, asNext, amNext));
        } else {
          merged.add(ni);
        }
      }
      safe = List.copyOf(merged);
    }

    usersByServerAndChannel
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(ch, safe);

    // Precompute lowercased nick set for fast mention checking.
    // Note: this can run on the EDT (IRC events are observed on SwingEdt), so keep it lean.
    java.util.HashSet<String> lowerTmp = new java.util.HashSet<>(Math.max(16, safe.size() * 2));
    for (NickInfo ni : safe) {
      if (ni == null) continue;
      String nick = ni.nick();
      if (nick == null) continue;
      String s = nick.trim();
      if (s.isEmpty()) continue;
      lowerTmp.add(s.toLowerCase(Locale.ROOT));
    }
    Set<String> lower = java.util.Collections.unmodifiableSet(lowerTmp);

    lowerNickSetByServerAndChannel
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(ch, lower);
  }

  public void clear(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel != null) byChannel.remove(ch);

    Map<String, Set<String>> byChannelSet = lowerNickSetByServerAndChannel.get(sid);
    if (byChannelSet != null) byChannelSet.remove(ch);
  }

  public void clearServer(String serverId) {
    String sid = norm(serverId);
    if (sid.isEmpty()) return;
    usersByServerAndChannel.remove(sid);
    lowerNickSetByServerAndChannel.remove(sid);
    hostmaskByServerAndNickLower.remove(sid);
    awayStateByServerAndNickLower.remove(sid);
    awayMessageByServerAndNickLower.remove(sid);
  }

  public boolean updateHostmask(String serverId, String channel, String nick, String hostmask) {
    String sid = norm(serverId);
    String ch = norm(channel);
    String n = norm(nick);
    String hm = norm(hostmask);

    if (sid.isEmpty() || ch.isEmpty() || n.isEmpty() || hm.isEmpty()) return false;

    // Remember learned hostmask server-wide.
    if (isUsefulHostmask(hm)) {
      hostmaskByServerAndNickLower
          .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
          .put(nickKey(n), hm);
    }

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel == null) return false;

    List<NickInfo> cur = byChannel.get(ch);
    if (cur == null || cur.isEmpty()) return false;

    boolean changed = false;
    java.util.ArrayList<NickInfo> next = new java.util.ArrayList<>(cur.size());

    for (NickInfo ni : cur) {
      if (ni == null) {
        next.add(null);
        continue;
      }

      String niNick = Objects.toString(ni.nick(), "");
      if (!niNick.isBlank() && niNick.equalsIgnoreCase(n)) {
        String existing = Objects.toString(ni.hostmask(), "").trim();
        if (!Objects.equals(existing, hm)) {
          next.add(new NickInfo(ni.nick(), ni.prefix(), hm, ni.awayState(), ni.awayMessage()));
          changed = true;
          continue;
        }
      }

      next.add(ni);
    }

    if (!changed) return false;

    // Store a new immutable list instance so downstream UI can re-render.
    byChannel.put(ch, List.copyOf(next));
    return true;
  }
  public Set<String> updateHostmaskAcrossChannels(String serverId, String nick, String hostmask) {
    String sid = norm(serverId);
    String n = norm(nick);
    String hm = norm(hostmask);

    if (sid.isEmpty() || n.isEmpty() || hm.isEmpty() || !isUsefulHostmask(hm)) return Set.of();

    // Remember learned hostmask server-wide.
    hostmaskByServerAndNickLower
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(nickKey(n), hm);

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel == null || byChannel.isEmpty()) return Set.of();

    java.util.Set<String> changedChannels = new java.util.HashSet<>();

    for (Map.Entry<String, List<NickInfo>> e : byChannel.entrySet()) {
      String ch = e.getKey();
      List<NickInfo> cur = e.getValue();
      if (cur == null || cur.isEmpty()) continue;

      boolean changed = false;
      java.util.ArrayList<NickInfo> next = new java.util.ArrayList<>(cur.size());

      for (NickInfo ni : cur) {
        if (ni == null) {
          next.add(null);
          continue;
        }
        String niNick = norm(ni.nick());
        if (!niNick.isEmpty() && niNick.equalsIgnoreCase(n)) {
          String existing = norm(ni.hostmask());
          if (!Objects.equals(existing, hm)) {
            next.add(new NickInfo(ni.nick(), ni.prefix(), hm, ni.awayState(), ni.awayMessage()));
            changed = true;
            continue;
          }
        }
        next.add(ni);
      }

      if (changed) {
        byChannel.put(ch, List.copyOf(next));
        changedChannels.add(ch);
      }
    }

    return java.util.Set.copyOf(changedChannels);
  }

  public boolean updateAwayState(String serverId, String channel, String nick, AwayState awayState) {
    return updateAwayState(serverId, channel, nick, awayState, null);
  }

  public boolean updateAwayState(String serverId, String channel, String nick, AwayState awayState, String awayMessage) {
    String sid = norm(serverId);
    String ch = norm(channel);
    String n = norm(nick);
    AwayState as = (awayState == null) ? AwayState.UNKNOWN : awayState;
    String msg = normalizeAwayMessage(as, awayMessage);

    if (sid.isEmpty() || ch.isEmpty() || n.isEmpty() || !isKnownAway(as)) return false;

    // Remember learned away state server-wide.
    awayStateByServerAndNickLower
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(nickKey(n), as);

    // Remember learned away message server-wide (only meaningful for AWAY).
    if (as == AwayState.AWAY) {
      if (msg != null) {
        awayMessageByServerAndNickLower
            .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
            .put(nickKey(n), msg);
      }
    } else {
      Map<String, String> m = awayMessageByServerAndNickLower.get(sid);
      if (m != null) m.remove(nickKey(n));
    }

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel == null) return false;

    List<NickInfo> cur = byChannel.get(ch);
    if (cur == null || cur.isEmpty()) return false;

    boolean changed = false;
    java.util.ArrayList<NickInfo> next = new java.util.ArrayList<>(cur.size());

    for (NickInfo ni : cur) {
      if (ni == null) {
        next.add(null);
        continue;
      }
      String niNick = Objects.toString(ni.nick(), "");
      if (!niNick.isBlank() && niNick.equalsIgnoreCase(n)) {
        AwayState existing = (ni.awayState() == null) ? AwayState.UNKNOWN : ni.awayState();
        String existingMsg = normalizeAwayMessage(existing, ni.awayMessage());
        // If we learn "AWAY" without a reason (e.g. USERHOST +/-), don't erase an existing reason.
        String nextMsg = (as == AwayState.AWAY) ? ((msg != null) ? msg : existingMsg) : null;
        if (!Objects.equals(existing, as) || !Objects.equals(existingMsg, nextMsg)) {
          next.add(new NickInfo(ni.nick(), ni.prefix(), ni.hostmask(), as, nextMsg));
          changed = true;
          continue;
        }
      }
      next.add(ni);
    }

    if (!changed) return false;

    byChannel.put(ch, List.copyOf(next));
    return true;
  }

  public Set<String> updateAwayStateAcrossChannels(String serverId, String nick, AwayState awayState) {
    return updateAwayStateAcrossChannels(serverId, nick, awayState, null);
  }

  public Set<String> updateAwayStateAcrossChannels(String serverId, String nick, AwayState awayState, String awayMessage) {
    String sid = norm(serverId);
    String n = norm(nick);
    AwayState as = (awayState == null) ? AwayState.UNKNOWN : awayState;
    String msg = normalizeAwayMessage(as, awayMessage);

    if (sid.isEmpty() || n.isEmpty() || !isKnownAway(as)) return Set.of();

    // Remember learned away state server-wide.
    awayStateByServerAndNickLower
        .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
        .put(nickKey(n), as);

    // Remember learned away message server-wide (only meaningful for AWAY).
    if (as == AwayState.AWAY) {
      if (msg != null) {
        awayMessageByServerAndNickLower
            .computeIfAbsent(sid, k -> new ConcurrentHashMap<>())
            .put(nickKey(n), msg);
      }
    } else {
      Map<String, String> m = awayMessageByServerAndNickLower.get(sid);
      if (m != null) m.remove(nickKey(n));
    }

    Map<String, List<NickInfo>> byChannel = usersByServerAndChannel.get(sid);
    if (byChannel == null || byChannel.isEmpty()) return Set.of();

    java.util.Set<String> changedChannels = new java.util.HashSet<>();

    for (Map.Entry<String, List<NickInfo>> e : byChannel.entrySet()) {
      String ch = e.getKey();
      List<NickInfo> cur = e.getValue();
      if (cur == null || cur.isEmpty()) continue;

      boolean changed = false;
      java.util.ArrayList<NickInfo> next = new java.util.ArrayList<>(cur.size());

      for (NickInfo ni : cur) {
        if (ni == null) {
          next.add(null);
          continue;
        }
        String niNick = norm(ni.nick());
        if (!niNick.isEmpty() && niNick.equalsIgnoreCase(n)) {
          AwayState existing = (ni.awayState() == null) ? AwayState.UNKNOWN : ni.awayState();
          String existingMsg = normalizeAwayMessage(existing, ni.awayMessage());
          // If we learn "AWAY" without a reason (e.g. USERHOST +/-), don't erase an existing reason.
          String nextMsg = (as == AwayState.AWAY) ? ((msg != null) ? msg : existingMsg) : null;
          if (!Objects.equals(existing, as) || !Objects.equals(existingMsg, nextMsg)) {
            next.add(new NickInfo(ni.nick(), ni.prefix(), ni.hostmask(), as, nextMsg));
            changed = true;
            continue;
          }
        }
        next.add(ni);
      }

      if (changed) {
        byChannel.put(ch, List.copyOf(next));
        changedChannels.add(ch);
      }
    }

    return java.util.Set.copyOf(changedChannels);
  }
}

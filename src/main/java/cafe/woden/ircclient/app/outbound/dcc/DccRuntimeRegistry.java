package cafe.woden.ircclient.app.outbound.dcc;

import java.net.ServerSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared in-memory DCC runtime state for pending offers, active chats, and listeners. */
@Component
@ApplicationLayer
final class DccRuntimeRegistry {

  private final ConcurrentMap<String, PendingChatOffer> pendingChatOffers =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, PendingSendOffer> pendingSendOffers =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, DccChatSession> chatSessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ServerSocket> outgoingChatListeners =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ServerSocket> outgoingSendListeners =
      new ConcurrentHashMap<>();

  ConcurrentMap<String, PendingChatOffer> pendingChatOffers() {
    return pendingChatOffers;
  }

  ConcurrentMap<String, PendingSendOffer> pendingSendOffers() {
    return pendingSendOffers;
  }

  ConcurrentMap<String, DccChatSession> chatSessions() {
    return chatSessions;
  }

  ConcurrentMap<String, ServerSocket> outgoingChatListeners() {
    return outgoingChatListeners;
  }

  ConcurrentMap<String, ServerSocket> outgoingSendListeners() {
    return outgoingSendListeners;
  }
}

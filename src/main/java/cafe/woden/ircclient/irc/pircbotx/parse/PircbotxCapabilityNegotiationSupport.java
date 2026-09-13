package cafe.woden.ircclient.irc.pircbotx.parse;

import static cafe.woden.ircclient.util.Ircv3CapabilityNames.BATCH;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.CHATHISTORY;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_CHATHISTORY;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.MESSAGE_TAGS;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityLine;
import cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityNegotiationRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3ZncPlaybackRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.pircbotx.capability.BatchedEnableCapHandler;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.CapHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles application-owned capability state changes and fallback CAP requests. */
public final class PircbotxCapabilityNegotiationSupport {

  private static final Logger log =
      LoggerFactory.getLogger(PircbotxCapabilityNegotiationSupport.class);
  private static final List<String> FALLBACK_CAPABILITIES =
      List.of(MESSAGE_TAGS, BATCH, CHATHISTORY, DRAFT_CHATHISTORY);

  private final PircBotX bot;
  private final String serverId;
  private final PircbotxConnectionState conn;
  private final Consumer<ServerIrcEvent> sink;
  private final PircbotxCapabilityStateSupport capabilityStateSupport;
  private final Ircv3CapabilityNegotiationRuntimeSupport runtimeSupport;
  private final Ircv3ZncPlaybackRuntimeSupport zncPlaybackRuntimeSupport;

  public PircbotxCapabilityNegotiationSupport(
      PircBotX bot,
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> sink,
      PircbotxCapabilityStateSupport capabilityStateSupport,
      Ircv3CapabilityNegotiationRuntimeSupport runtimeSupport,
      Ircv3ZncPlaybackRuntimeSupport zncPlaybackRuntimeSupport) {
    this.bot = Objects.requireNonNull(bot, "bot");
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.sink = Objects.requireNonNull(sink, "sink");
    this.capabilityStateSupport =
        Objects.requireNonNull(capabilityStateSupport, "capabilityStateSupport");
    this.runtimeSupport = Objects.requireNonNull(runtimeSupport, "runtimeSupport");
    this.zncPlaybackRuntimeSupport =
        Objects.requireNonNull(zncPlaybackRuntimeSupport, "zncPlaybackRuntimeSupport");
  }

  public void observe(Ircv3CapabilityLine capLine) {
    observe(capLine, List.of());
  }

  public void observe(Ircv3CapabilityLine capLine, List<CapHandler> remainingCapHandlers) {
    Objects.requireNonNull(capLine, "capLine");
    if (!capLine.hasTokens()) return;

    Set<String> pendingCapabilities = pendingFallbackCapabilities(remainingCapHandlers);
    Ircv3CapabilityNegotiationRuntimeSupport.Plan plan =
        runtimeSupport.plan(
            new Ircv3InboundCommandRequest(
                "server",
                "CAP",
                "",
                List.of("*", capLine.action(), capLine.normalizedCaps()),
                Map.of(),
                "",
                false,
                0L,
                conn.isMessageTagsCapAcked(),
                conn.isBatchCapAcked(),
                conn.isChatHistoryCapAcked(),
                pendingCapabilities));

    applyCapabilityChanges(plan);
    maybeRequestMessageTagsFallback(plan);
    maybeRequestHistoryCapabilityFallback(plan);
  }

  private void applyCapabilityChanges(Ircv3CapabilityNegotiationRuntimeSupport.Plan plan) {
    for (Ircv3CapabilityNegotiationRuntimeSupport.CapabilityChange change : plan.changes()) {
      if (change.updateState()) {
        Ircv3ZncPlaybackRuntimeSupport.Detection detection =
            change.enabled()
                ? zncPlaybackRuntimeSupport.detectZncCapability(change.capabilityName())
                : Ircv3ZncPlaybackRuntimeSupport.Detection.notDetected();
        if (detection.detected() && conn.markZncDetected()) {
          log.debug(
              "[{}] detected ZNC via CAP {}: {}", serverId, change.action(), detection.evidence());
        }
        capabilityStateSupport.apply(change.capabilityName(), change.enabled(), change.action());
      }

      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.Ircv3CapabilityChanged(
                  Instant.now(), change.action(), change.capabilityName(), change.enabled())));
    }

    if (plan.refreshConnectionFeatures() && !plan.changes().isEmpty()) {
      String action = plan.changes().getFirst().action();
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ConnectionFeaturesUpdated(
                  Instant.now(), "cap-" + action.toLowerCase(Locale.ROOT))));
    }
  }

  private void maybeRequestMessageTagsFallback(Ircv3CapabilityNegotiationRuntimeSupport.Plan plan) {
    if (!plan.requestMessageTags()) return;
    if (!conn.beginMessageTagsFallbackRequest()) return;

    try {
      bot.sendCAP().request(MESSAGE_TAGS);
      log.debug(
          "[{}] fallback CAP REQ sent for message-tags (downstream capability remained unenabled)",
          serverId);
    } catch (Exception ex) {
      conn.clearMessageTagsFallbackRequest();
      log.debug("[{}] fallback CAP REQ for message-tags failed", serverId, ex);
    }
  }

  private void maybeRequestHistoryCapabilityFallback(
      Ircv3CapabilityNegotiationRuntimeSupport.Plan plan) {
    ArrayList<String> requestedCaps = new ArrayList<>(2);
    boolean requestedBatch = false;
    boolean requestedHistory = false;

    if (plan.requestBatch() && conn.beginBatchFallbackRequest()) {
      requestedCaps.add(BATCH);
      requestedBatch = true;
    }
    if (plan.requestHistory() && conn.beginChatHistoryFallbackRequest()) {
      requestedCaps.add(plan.historyCapability());
      requestedHistory = true;
    }

    if (requestedCaps.isEmpty()) return;

    try {
      bot.sendCAP().request(requestedCaps.toArray(new String[0]));
      log.debug("[{}] fallback CAP REQ sent for {}", serverId, String.join(", ", requestedCaps));
    } catch (Exception ex) {
      if (requestedBatch) {
        conn.clearBatchFallbackRequest();
      }
      if (requestedHistory) {
        conn.clearChatHistoryFallbackRequest();
      }
      log.debug("[{}] fallback CAP REQ for history capabilities failed", serverId, ex);
    }
  }

  private static Set<String> pendingFallbackCapabilities(List<CapHandler> remainingCapHandlers) {
    if (remainingCapHandlers == null || remainingCapHandlers.isEmpty()) return Set.of();
    LinkedHashSet<String> pending = new LinkedHashSet<>();
    for (String capability : FALLBACK_CAPABILITIES) {
      if (isCapabilityRequestPending(remainingCapHandlers, capability)) {
        pending.add(capability);
      }
    }
    return Set.copyOf(pending);
  }

  private static boolean isCapabilityRequestPending(
      List<CapHandler> remainingCapHandlers, String capability) {
    if (remainingCapHandlers == null || remainingCapHandlers.isEmpty()) return false;
    for (CapHandler handler : remainingCapHandlers) {
      if (handler instanceof BatchedEnableCapHandler batched && batched.isPending(capability)) {
        return true;
      }
    }
    return false;
  }
}

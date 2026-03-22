package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.outbound.dcc.OutboundDccCommandService;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.CtcpRoutingPort.PendingCtcp;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Coordinates inbound text/event transcript updates extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorInboundTextEventHandler {

  interface Callbacks {
    void observeChannelActivity(String serverId, String channel);

    void postTo(
        TargetRef dest, TargetRef active, boolean markUnreadIfNotActive, Consumer<TargetRef> write);

    void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write);

    boolean isFromSelf(String serverId, String from);

    void markPrivateMessagePeerOnline(String serverId, String nick);

    void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String text,
        InterceptorEventType eventType);

    boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body,
        String ctcpCommand,
        String ctcpValue);

    TargetRef safeStatusTarget();

    TargetRef resolveActiveOrStatus(String sid, TargetRef status);

    boolean isMutedChannel(TargetRef target);
  }

  private static final int INBOUND_MSGID_DEDUP_MAX_KEYS = 50_000;
  private static final Duration INBOUND_MSGID_DEDUP_TTL = Duration.ofMinutes(30);
  private static final int INBOUND_MSGID_DEDUP_COUNTER_MAX_KEYS = 4_096;
  private static final Duration INBOUND_MSGID_DEDUP_COUNTER_TTL = Duration.ofHours(6);
  private static final long INBOUND_MSGID_DEDUP_DIAG_MIN_EMIT_MS = 10_000L;

  private final IrcNegotiatedFeaturePort negotiatedFeaturePort;
  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final UserInfoEnrichmentService userInfoEnrichmentService;
  private final PendingEchoMessagePort pendingEchoMessageState;
  private final OutboundDccCommandService outboundDccCommandService;
  private final TrayNotificationsPort trayNotificationService;
  private final UiSettingsPort uiSettingsPort;
  private final IrcEventNotifierPort ircEventNotifierPort;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final CtcpRoutingPort ctcpRoutingState;
  private final MediatorInboundEventPreparationService eventPreparationService;

  private final Cache<InboundMessageDedupKey, Boolean> inboundMessageIdDedup =
      Caffeine.newBuilder()
          .maximumSize(INBOUND_MSGID_DEDUP_MAX_KEYS)
          .expireAfterAccess(INBOUND_MSGID_DEDUP_TTL)
          .build();
  private final Cache<InboundMessageDedupCounterKey, Long>
      inboundMessageIdDedupSuppressedCountByKey =
          Caffeine.newBuilder()
              .maximumSize(INBOUND_MSGID_DEDUP_COUNTER_MAX_KEYS)
              .expireAfterAccess(INBOUND_MSGID_DEDUP_COUNTER_TTL)
              .build();
  private final Cache<InboundMessageDedupCounterKey, Long>
      inboundMessageIdDedupDiagLastEmitMsByKey =
          Caffeine.newBuilder()
              .maximumSize(INBOUND_MSGID_DEDUP_COUNTER_MAX_KEYS)
              .expireAfterWrite(INBOUND_MSGID_DEDUP_COUNTER_TTL.toMillis(), TimeUnit.MILLISECONDS)
              .build();
  private final AtomicLong inboundMessageIdDedupSuppressedTotal = new AtomicLong();

  private record InboundMessageDedupKey(
      String serverId, String target, String eventType, String msgId) {}

  private record InboundMessageDedupCounterKey(String serverId, String target, String eventType) {}

  public void handleChannelMessage(
      Callbacks callbacks,
      String sid,
      PreparedServerIrcEvent prepared,
      IrcEvent.ChannelMessage event) {
    callbacks.observeChannelActivity(sid, event.channel());
    TargetRef channel = new TargetRef(sid, event.channel());
    TargetRef active = targetCoordinator.getActiveTarget();
    PreparedChannelText channelText = prepared.channelText();
    NotificationRuleMatch ruleMatch = channelText.ruleMatch();

    userInfoEnrichmentService.noteUserActivity(sid, event.from(), event.at());

    InboundIgnorePolicyPort.Decision decision = channelText.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) {
      return;
    }

    if (tryResolvePendingEchoChannelMessage(callbacks, sid, channel, active, event)) {
      return;
    }

    if (maybeApplyMessageEditFromTaggedLine(
        sid,
        channel,
        event.at(),
        event.from(),
        event.text(),
        event.messageId(),
        event.ircv3Tags())) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, channel, "channel-message", event.messageId(), event.ircv3Tags())) {
      return;
    }

    clearRemoteTypingIndicatorsForSender(channel, event.from());

    if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
      callbacks.postTo(
          channel,
          active,
          true,
          dest ->
              ui.appendSpoilerChatAt(
                  dest,
                  event.at(),
                  event.from(),
                  event.text(),
                  event.messageId(),
                  event.ircv3Tags()));
    } else {
      callbacks.postTo(
          channel,
          active,
          true,
          dest ->
              ui.appendChatAt(
                  dest,
                  event.at(),
                  event.from(),
                  event.text(),
                  false,
                  event.messageId(),
                  event.ircv3Tags(),
                  ruleMatch != null ? ruleMatch.highlightColor() : null));
    }

    recordRuleMatchIfPresent(callbacks, channel, active, event.from(), event.text(), ruleMatch);
    callbacks.recordInterceptorEvent(
        sid, event.channel(), event.from(), event.text(), InterceptorEventType.MESSAGE);

    if (channelText.mention()) {
      callbacks.recordInterceptorEvent(
          sid, event.channel(), event.from(), event.text(), InterceptorEventType.HIGHLIGHT);
      recordMentionHighlight(callbacks, channel, active, event.from(), event.text());

      if (!callbacks.isMutedChannel(channel)) {
        try {
          trayNotificationService.notifyHighlight(sid, event.channel(), event.from(), event.text());
        } catch (Exception ignored) {
        }
      }
    }
  }

  public void handleChannelAction(
      Callbacks callbacks,
      String sid,
      PreparedServerIrcEvent prepared,
      IrcEvent.ChannelAction event) {
    callbacks.observeChannelActivity(sid, event.channel());
    TargetRef channel = new TargetRef(sid, event.channel());
    TargetRef active = targetCoordinator.getActiveTarget();
    PreparedChannelText channelText = prepared.channelText();
    NotificationRuleMatch ruleMatch = channelText.ruleMatch();

    userInfoEnrichmentService.noteUserActivity(sid, event.from(), event.at());

    InboundIgnorePolicyPort.Decision decision = channelText.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, channel, "channel-action", event.messageId(), event.ircv3Tags())) {
      return;
    }

    clearRemoteTypingIndicatorsForSender(channel, event.from());

    if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
      callbacks.postTo(
          channel,
          active,
          true,
          dest ->
              ui.appendSpoilerChatAt(
                  dest,
                  event.at(),
                  event.from(),
                  "* " + event.action(),
                  event.messageId(),
                  event.ircv3Tags()));
    } else {
      callbacks.postTo(
          channel,
          active,
          true,
          dest ->
              ui.appendActionAt(
                  dest,
                  event.at(),
                  event.from(),
                  event.action(),
                  false,
                  event.messageId(),
                  event.ircv3Tags(),
                  ruleMatch != null ? ruleMatch.highlightColor() : null));
    }

    recordRuleMatchIfPresent(callbacks, channel, active, event.from(), event.action(), ruleMatch);
    callbacks.recordInterceptorEvent(
        sid, event.channel(), event.from(), event.action(), InterceptorEventType.ACTION);

    if (channelText.mention()) {
      callbacks.recordInterceptorEvent(
          sid, event.channel(), event.from(), event.action(), InterceptorEventType.HIGHLIGHT);
      recordMentionHighlight(callbacks, channel, active, event.from(), "* " + event.action());

      if (!callbacks.isMutedChannel(channel)) {
        try {
          trayNotificationService.notifyHighlight(
              sid, event.channel(), event.from(), "* " + event.action());
        } catch (Exception ignored) {
        }
      }
    }
  }

  public void handlePrivateMessage(
      Callbacks callbacks,
      String sid,
      PreparedServerIrcEvent prepared,
      IrcEvent.PrivateMessage event) {
    PreparedPrivateMessage privateMessage = prepared.privateMessage();
    boolean fromSelf = privateMessage.fromSelf();
    String peer = privateMessage.peer();
    TargetRef pm = new TargetRef(sid, peer);
    boolean allowAutoOpen = targetCoordinator.allowPrivateAutoOpenFromInbound(pm, fromSelf);

    if (fromSelf
        && "*playback".equalsIgnoreCase(peer)
        && event.text() != null
        && event.text().toLowerCase(Locale.ROOT).startsWith("play ")) {
      return;
    }

    if (!fromSelf) {
      userInfoEnrichmentService.noteUserActivity(sid, event.from(), event.at());
      callbacks.markPrivateMessagePeerOnline(sid, event.from());
    }

    ParsedCtcp ctcp = privateMessage.ctcp();
    if (!fromSelf && ctcp != null && "DCC".equals(ctcp.commandUpper())) {
      InboundIgnorePolicyPort.Decision dccDecision = privateMessage.dccDecision();
      if (dccDecision == InboundIgnorePolicyPort.Decision.HARD_DROP) {
        return;
      }

      boolean dccHandled =
          outboundDccCommandService.handleInboundDccOffer(
              event.at(),
              sid,
              event.from(),
              ctcp.arg(),
              dccDecision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER);
      if (dccHandled) {
        return;
      }
    }

    InboundIgnorePolicyPort.Decision decision = privateMessage.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) {
      return;
    }

    if (tryResolvePendingEchoPrivateMessage(callbacks, sid, pm, event, allowAutoOpen)) {
      return;
    }

    if (maybeApplyMessageEditFromTaggedLine(
        sid, pm, event.at(), event.from(), event.text(), event.messageId(), event.ircv3Tags())) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, pm, "private-message", event.messageId(), event.ircv3Tags())) {
      return;
    }

    if (!fromSelf) {
      clearRemoteTypingIndicatorsForSender(pm, event.from());
    }

    if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
      if (allowAutoOpen) {
        callbacks.postTo(
            pm,
            true,
            dest ->
                ui.appendSpoilerChatAt(
                    dest,
                    event.at(),
                    event.from(),
                    event.text(),
                    event.messageId(),
                    event.ircv3Tags()));
      } else {
        ui.appendSpoilerChatAt(
            pm, event.at(), event.from(), event.text(), event.messageId(), event.ircv3Tags());
      }
    } else if (allowAutoOpen) {
      callbacks.postTo(
          pm,
          true,
          dest ->
              ui.appendChatAt(
                  dest,
                  event.at(),
                  event.from(),
                  event.text(),
                  fromSelf,
                  event.messageId(),
                  event.ircv3Tags()));
    } else {
      ui.appendChatAt(
          pm,
          event.at(),
          event.from(),
          event.text(),
          fromSelf,
          event.messageId(),
          event.ircv3Tags());
    }

    callbacks.recordInterceptorEvent(
        sid,
        "pm:" + Objects.toString(peer, "").trim(),
        event.from(),
        event.text(),
        InterceptorEventType.PRIVATE_MESSAGE);

    if (!fromSelf) {
      String fromNick = Objects.toString(event.from(), "").trim();
      String title = fromNick.isEmpty() ? "Private message" : ("Private message from " + fromNick);
      maybeNotifyInboundPrivateConversation(
          callbacks,
          sid,
          fromNick,
          title,
          Objects.toString(event.text(), "").trim(),
          Objects.toString(event.text(), ""));
    }
  }

  public void handlePrivateAction(
      Callbacks callbacks,
      String sid,
      PreparedServerIrcEvent prepared,
      IrcEvent.PrivateAction event) {
    PreparedPrivateAction privateAction = prepared.privateAction();
    boolean fromSelf = privateAction.fromSelf();
    String peer = privateAction.peer();
    TargetRef pm = new TargetRef(sid, peer);
    boolean allowAutoOpen = targetCoordinator.allowPrivateAutoOpenFromInbound(pm, fromSelf);

    if (!fromSelf) {
      userInfoEnrichmentService.noteUserActivity(sid, event.from(), event.at());
      callbacks.markPrivateMessagePeerOnline(sid, event.from());
    }

    InboundIgnorePolicyPort.Decision decision = privateAction.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, pm, "private-action", event.messageId(), event.ircv3Tags())) {
      return;
    }

    if (!fromSelf) {
      clearRemoteTypingIndicatorsForSender(pm, event.from());
    }

    if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
      if (allowAutoOpen) {
        callbacks.postTo(
            pm,
            true,
            dest ->
                ui.appendSpoilerChatAt(
                    dest,
                    event.at(),
                    event.from(),
                    "* " + event.action(),
                    event.messageId(),
                    event.ircv3Tags()));
      } else {
        ui.appendSpoilerChatAt(
            pm,
            event.at(),
            event.from(),
            "* " + event.action(),
            event.messageId(),
            event.ircv3Tags());
      }
    } else if (allowAutoOpen) {
      callbacks.postTo(
          pm,
          true,
          dest ->
              ui.appendActionAt(
                  dest,
                  event.at(),
                  event.from(),
                  event.action(),
                  fromSelf,
                  event.messageId(),
                  event.ircv3Tags()));
    } else {
      ui.appendActionAt(
          pm,
          event.at(),
          event.from(),
          event.action(),
          fromSelf,
          event.messageId(),
          event.ircv3Tags());
    }

    callbacks.recordInterceptorEvent(
        sid,
        "pm:" + Objects.toString(peer, "").trim(),
        event.from(),
        event.action(),
        InterceptorEventType.PRIVATE_ACTION);

    if (!fromSelf) {
      String fromNick = Objects.toString(event.from(), "").trim();
      String title = fromNick.isEmpty() ? "Private action" : ("Private action from " + fromNick);
      String body = "* " + Objects.toString(event.action(), "").trim();
      maybeNotifyInboundPrivateConversation(
          callbacks, sid, fromNick, title, body, "* " + event.action());
    }
  }

  public void handleNotice(
      Callbacks callbacks,
      String sid,
      TargetRef status,
      PreparedServerIrcEvent prepared,
      IrcEvent.Notice event) {
    PreparedNotice notice = prepared.notice();
    boolean fromSelf = notice.fromSelf();
    callbacks.markPrivateMessagePeerOnline(sid, event.from());
    InboundIgnorePolicyPort.Decision decision = notice.decision();
    boolean spoiler = decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER;
    boolean suppress = decision == InboundIgnorePolicyPort.Decision.HARD_DROP;
    TargetRef dest = resolveNoticeDestination(callbacks, sid, status, event);

    if (maybeApplyMessageEditFromTaggedLine(
        sid, dest, event.at(), event.from(), event.text(), event.messageId(), event.ircv3Tags())) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, dest, "notice", event.messageId(), event.ircv3Tags())) {
      return;
    }

    handleNoticeOrSpoiler(
        callbacks,
        sid,
        dest,
        event.at(),
        event.from(),
        event.text(),
        spoiler,
        suppress,
        event.messageId(),
        event.ircv3Tags());

    String noticeChannel = notice.noticeChannel();
    callbacks.recordInterceptorEvent(
        sid,
        noticeChannel.isBlank() ? "status" : noticeChannel,
        event.from(),
        event.text(),
        InterceptorEventType.NOTICE);

    if (!fromSelf && !suppress) {
      String fromNick = Objects.toString(event.from(), "").trim();
      String title = fromNick.isEmpty() ? "Notice" : ("Notice from " + fromNick);
      String channel = noticeChannel.isBlank() ? null : noticeChannel;
      notifyIrcEvent(
          callbacks,
          IrcEventNotificationRule.EventType.NOTICE_RECEIVED,
          sid,
          channel,
          fromNick,
          title,
          Objects.toString(event.text(), "").trim());
    }
  }

  public void handleCtcpRequest(
      Callbacks callbacks,
      String sid,
      TargetRef status,
      PreparedServerIrcEvent prepared,
      IrcEvent.CtcpRequestReceived event) {
    PreparedCtcpRequest ctcpRequest = prepared.ctcpRequest();
    String command = ctcpRequest.command();
    String argument = ctcpRequest.argument();
    String ctcpText = ctcpRequest.normalizedText();
    InboundIgnorePolicyPort.Decision decision = ctcpRequest.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) {
      return;
    }

    TargetRef dest = resolveCtcpRequestDestination(callbacks, sid, status, event);
    maybeMarkPrivateMessagePeerOnlineForCtcp(callbacks, sid, event, dest);

    StringBuilder renderedBuilder =
        new StringBuilder()
            .append("\u2190 ")
            .append(event.from())
            .append(" CTCP ")
            .append(command.isBlank() ? Objects.toString(event.command(), "").trim() : command);
    if (!argument.isBlank()) {
      renderedBuilder.append(' ').append(argument);
    }
    if (event.channel() != null && !event.channel().isBlank()) {
      renderedBuilder.append(" in ").append(event.channel());
    }
    String rendered = renderedBuilder.toString();

    if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
      callbacks.postTo(
          dest, true, target -> ui.appendSpoilerChatAt(target, event.at(), "(ctcp)", rendered));
    } else {
      callbacks.postTo(
          dest, true, target -> ui.appendStatusAt(target, event.at(), "(ctcp)", rendered));
    }
    callbacks.recordInterceptorEvent(
        sid,
        Objects.toString(event.channel(), "").trim().isEmpty() ? "status" : event.channel(),
        event.from(),
        ctcpText.isBlank() ? "CTCP" : ctcpText,
        InterceptorEventType.CTCP);

    String fromNick = Objects.toString(event.from(), "").trim();
    String channel = Objects.toString(event.channel(), "").trim();
    if (channel.isBlank()) {
      channel = null;
    }
    String title =
        fromNick.isEmpty()
            ? "CTCP request received"
            : ("CTCP from " + fromNick + (channel == null ? "" : (" in " + channel)));
    String body = command.isEmpty() ? "CTCP request" : command;
    if (!argument.isEmpty()) {
      body = body + " " + argument;
    }

    notifyIrcEvent(
        callbacks,
        IrcEventNotificationRule.EventType.CTCP_RECEIVED,
        sid,
        channel,
        fromNick,
        title,
        body,
        command,
        argument);
  }

  private void maybeNotifyInboundPrivateConversation(
      Callbacks callbacks,
      String sid,
      String fromNick,
      String title,
      String notifyBody,
      String trayBody) {
    boolean customPmNotified =
        notifyIrcEvent(
            callbacks,
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            sid,
            null,
            fromNick,
            title,
            notifyBody);
    boolean pmRulesEnabled =
        ircEventNotifierPort != null
            && ircEventNotifierPort.hasEnabledRuleFor(
                IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED);
    if (!customPmNotified && !pmRulesEnabled) {
      try {
        trayNotificationService.notifyPrivateMessage(sid, fromNick, trayBody);
      } catch (Exception ignored) {
      }
    }
  }

  private void handleNoticeOrSpoiler(
      Callbacks callbacks,
      String sid,
      TargetRef status,
      Instant at,
      String from,
      String text,
      boolean spoiler,
      boolean suppressOutput,
      String messageId,
      Map<String, String> ircv3Tags) {
    ParsedCtcp ctcp = eventPreparationService.parseCtcp(text);
    if (ctcp != null) {
      String cmd = ctcp.commandUpper();
      String arg = ctcp.arg();

      TargetRef dest = null;
      String rendered = null;

      if ("VERSION".equals(cmd)) {
        PendingCtcp pending = ctcpRoutingState.remove(sid, from, cmd, null);
        if (pending != null) {
          dest = pending.target();
          rendered = from + " VERSION: " + (arg.isBlank() ? "(no version)" : arg);
        }
      } else if ("PING".equals(cmd)) {
        String token = arg;
        int sp = token.indexOf(' ');
        if (sp >= 0) {
          token = token.substring(0, sp);
        }
        PendingCtcp pending = ctcpRoutingState.remove(sid, from, cmd, token);
        if (pending != null) {
          dest = pending.target();
          long rtt = Math.max(0L, System.currentTimeMillis() - pending.startedMs());
          rendered = from + " PING reply: " + rtt + "ms";
        }
      } else if ("TIME".equals(cmd)) {
        PendingCtcp pending = ctcpRoutingState.remove(sid, from, cmd, null);
        if (pending != null) {
          dest = pending.target();
          rendered = from + " TIME: " + (arg.isBlank() ? "(no time)" : arg);
        }
      }
      if (dest == null && rendered == null) {
        if ("VERSION".equals(cmd)) {
          dest = status;
          rendered = from + " VERSION: " + (arg.isBlank() ? "(no version)" : arg);
        } else if ("PING".equals(cmd)) {
          dest = status;
          rendered = from + " PING: " + (arg.isBlank() ? "(no payload)" : arg);
        } else if ("TIME".equals(cmd)) {
          dest = status;
          rendered = from + " TIME: " + (arg.isBlank() ? "(no time)" : arg);
        } else {
          PendingCtcp pending = ctcpRoutingState.remove(sid, from, cmd, null);
          dest = pending != null ? pending.target() : status;
          rendered = from + " " + cmd + (arg.isBlank() ? "" : ": " + arg);
        }
      }

      if (dest != null && rendered != null) {
        if (suppressOutput) {
          return;
        }
        ui.ensureTargetExists(dest);
        if (spoiler) {
          ui.appendSpoilerChatAt(dest, at, "(ctcp)", rendered);
        } else {
          ui.appendStatusAt(dest, at, "(ctcp)", rendered);
        }
        if (!dest.equals(targetCoordinator.getActiveTarget()) && !callbacks.isMutedChannel(dest)) {
          ui.markUnread(dest);
        }
        return;
      }
    }

    if (suppressOutput) {
      return;
    }

    if (spoiler) {
      ui.appendSpoilerChatAt(status, at, "(notice) " + from, text, messageId, ircv3Tags);
    } else {
      ui.appendNoticeAt(status, at, "(notice) " + from, text, messageId, ircv3Tags);
    }
  }

  private TargetRef resolveNoticeDestination(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.Notice event) {
    TargetRef dest = null;
    String target = event.target();
    String from = Objects.toString(event.from(), "").trim();
    boolean serverNotice = from.isEmpty() || "server".equalsIgnoreCase(from);
    if (serverNotice) {
      if (target != null && !target.isBlank()) {
        TargetRef noticeTarget = new TargetRef(sid, target);
        if (noticeTarget.isChannel()) {
          dest = noticeTarget;
        }
      }
      if (dest == null) {
        dest = status != null ? status : callbacks.safeStatusTarget();
      }
    } else if (target != null && !target.isBlank()) {
      TargetRef noticeTarget = new TargetRef(sid, target);
      if (noticeTarget.isChannel()) {
        dest = noticeTarget;
      }
    }
    if (dest == null) {
      dest = callbacks.resolveActiveOrStatus(sid, status);
    }
    return dest;
  }

  private TargetRef resolveCtcpRequestDestination(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.CtcpRequestReceived event) {
    if (uiSettingsPort.get().ctcpRequestsInActiveTargetEnabled()) {
      return callbacks.resolveActiveOrStatus(sid, status);
    }
    if (event.channel() != null && !event.channel().isBlank()) {
      return new TargetRef(sid, event.channel());
    }
    if (event.from() != null && !event.from().isBlank()) {
      return new TargetRef(sid, event.from());
    }
    return status != null ? status : callbacks.safeStatusTarget();
  }

  private void maybeMarkPrivateMessagePeerOnlineForCtcp(
      Callbacks callbacks,
      String serverId,
      IrcEvent.CtcpRequestReceived event,
      @Nullable TargetRef destination) {
    if (event == null || destination == null) {
      return;
    }
    if (event.channel() != null && !event.channel().isBlank()) {
      return;
    }

    String from = normalizePrivateMessagePeer(event.from());
    if (from.isEmpty()) {
      return;
    }

    String destinationPeer = normalizePrivateMessagePeer(destination.target());
    if (destinationPeer.isEmpty() || !from.equalsIgnoreCase(destinationPeer)) {
      return;
    }

    callbacks.markPrivateMessagePeerOnline(serverId, from);
  }

  private void recordRuleMatchIfPresent(
      Callbacks callbacks,
      TargetRef channel,
      TargetRef active,
      String from,
      String text,
      NotificationRuleMatch match) {
    if (channel == null || match == null) {
      return;
    }
    if (callbacks.isMutedChannel(channel)) {
      return;
    }
    if (active == null || !channel.equals(active)) {
      ui.markHighlight(channel);
    }
    ui.recordRuleMatch(
        channel, from, match.ruleLabel(), snippetAround(text, match.start(), match.end()));
  }

  private void recordMentionHighlight(
      Callbacks callbacks, TargetRef channel, TargetRef active, String fromNick, String snippet) {
    if (channel == null) {
      return;
    }
    if (callbacks.isMutedChannel(channel)) {
      return;
    }
    if (active == null || !channel.equals(active)) {
      ui.markHighlight(channel);
    }
    ui.recordHighlight(channel, fromNick, snippet);
  }

  private boolean tryResolvePendingEchoChannelMessage(
      Callbacks callbacks,
      String sid,
      TargetRef channel,
      TargetRef active,
      IrcEvent.ChannelMessage event) {
    if (!callbacks.isFromSelf(sid, event.from())) {
      return false;
    }
    var pending =
        pendingEchoMessageState.consumeByTargetAndText(channel, event.from(), event.text());
    if (pending.isEmpty()) {
      return false;
    }

    var entry = pending.get();
    callbacks.postTo(
        channel,
        active,
        true,
        dest -> {
          boolean replaced =
              ui.resolvePendingOutgoingChat(
                  dest,
                  entry.pendingId(),
                  event.at(),
                  event.from(),
                  event.text(),
                  event.messageId(),
                  event.ircv3Tags());
          if (!replaced) {
            ui.appendChatAt(
                dest,
                event.at(),
                event.from(),
                event.text(),
                true,
                event.messageId(),
                event.ircv3Tags());
          }
        });
    return true;
  }

  private boolean tryResolvePendingEchoPrivateMessage(
      Callbacks callbacks,
      String sid,
      TargetRef fallbackPm,
      IrcEvent.PrivateMessage event,
      boolean allowAutoOpen) {
    if (!callbacks.isFromSelf(sid, event.from())) {
      return false;
    }

    var pending =
        pendingEchoMessageState.consumeByTargetAndText(fallbackPm, event.from(), event.text());
    if (pending.isEmpty()) {
      pending = pendingEchoMessageState.consumePrivateFallback(sid, event.from(), event.text());
    }
    if (pending.isEmpty()) {
      return false;
    }

    var entry = pending.get();
    TargetRef dest = entry.target() != null ? entry.target() : fallbackPm;
    if (allowAutoOpen) {
      callbacks.postTo(
          dest,
          true,
          target -> {
            boolean replaced =
                ui.resolvePendingOutgoingChat(
                    target,
                    entry.pendingId(),
                    event.at(),
                    event.from(),
                    event.text(),
                    event.messageId(),
                    event.ircv3Tags());
            if (!replaced) {
              ui.appendChatAt(
                  target,
                  event.at(),
                  event.from(),
                  event.text(),
                  true,
                  event.messageId(),
                  event.ircv3Tags());
            }
          });
    } else {
      boolean replaced =
          ui.resolvePendingOutgoingChat(
              dest,
              entry.pendingId(),
              event.at(),
              event.from(),
              event.text(),
              event.messageId(),
              event.ircv3Tags());
      if (!replaced) {
        ui.appendChatAt(
            dest,
            event.at(),
            event.from(),
            event.text(),
            true,
            event.messageId(),
            event.ircv3Tags());
      }
    }
    return true;
  }

  private boolean maybeApplyMessageEditFromTaggedLine(
      String sid,
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    if (sid == null || sid.isBlank()) {
      return false;
    }
    if (target == null || target.isUiOnly()) {
      return false;
    }
    if (!negotiatedFeaturePort.isMessageEditAvailable(sid)) {
      return false;
    }

    String targetMsgId = firstIrcv3TagValue(ircv3Tags, "draft/edit", "+draft/edit");
    if (targetMsgId.isBlank()) {
      return false;
    }

    return ui.applyMessageEdit(
        target,
        at,
        Objects.toString(from, "").trim(),
        targetMsgId,
        Objects.toString(text, ""),
        messageId,
        ircv3Tags);
  }

  private boolean shouldSuppressInboundDuplicateByMsgId(
      String sid, TargetRef target, String eventType, String messageId, Map<String, String> tags) {
    if (hasMessageMutationTag(tags)) {
      return false;
    }

    String msgId = effectiveMessageIdForDedup(messageId, tags);
    if (msgId.isBlank()) {
      return false;
    }

    String sidKey = Objects.toString(sid, "").trim().toLowerCase(Locale.ROOT);
    String targetKey = normalizeDedupTarget(target);
    String eventKey = Objects.toString(eventType, "").trim().toLowerCase(Locale.ROOT);
    InboundMessageDedupKey key = new InboundMessageDedupKey(sidKey, targetKey, eventKey, msgId);
    boolean duplicate = inboundMessageIdDedup.asMap().putIfAbsent(key, Boolean.TRUE) != null;
    if (duplicate) {
      recordInboundMessageIdSuppression(sidKey, targetKey, eventKey, msgId);
    }
    return duplicate;
  }

  private static String normalizeDedupTarget(TargetRef target) {
    if (target == null) {
      return "";
    }
    return Objects.toString(target.target(), "").trim().toLowerCase(Locale.ROOT);
  }

  private void recordInboundMessageIdSuppression(
      String serverId, String target, String eventType, String messageId) {
    InboundMessageDedupCounterKey counterKey =
        new InboundMessageDedupCounterKey(serverId, target, eventType);
    long keyCount =
        inboundMessageIdDedupSuppressedCountByKey
            .asMap()
            .compute(counterKey, (key, previous) -> previous == null ? 1L : (previous + 1L));
    long total = inboundMessageIdDedupSuppressedTotal.incrementAndGet();
    maybePublishInboundMessageIdSuppression(counterKey, keyCount, total, messageId);
  }

  private void maybePublishInboundMessageIdSuppression(
      InboundMessageDedupCounterKey key, long keyCount, long total, String messageId) {
    if (applicationEventPublisher == null || key == null) {
      return;
    }
    if (keyCount > 1 && (keyCount % 25L) != 0L) {
      return;
    }

    long now = System.currentTimeMillis();
    Long last = inboundMessageIdDedupDiagLastEmitMsByKey.asMap().get(key);
    if (last != null && (now - last.longValue()) < INBOUND_MSGID_DEDUP_DIAG_MIN_EMIT_MS) {
      return;
    }
    inboundMessageIdDedupDiagLastEmitMsByKey.put(key, now);

    try {
      applicationEventPublisher.publishEvent(
          new IrcMediator.InboundMessageDedupDiagnostics(
              key.serverId(),
              key.target(),
              key.eventType(),
              keyCount,
              total,
              Objects.toString(messageId, "")));
    } catch (Exception ignored) {
    }
  }

  private static String effectiveMessageIdForDedup(String messageId, Map<String, String> tags) {
    String direct = Objects.toString(messageId, "").trim();
    if (!direct.isBlank()) {
      return direct;
    }
    return firstIrcv3TagValue(
        tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid", "znc.in/msgid", "+znc.in/msgid");
  }

  private static boolean hasMessageMutationTag(Map<String, String> tags) {
    return !firstIrcv3TagValue(tags, "draft/edit", "+draft/edit").isBlank()
        || !firstIrcv3TagValue(tags, "draft/react", "+draft/react").isBlank()
        || !firstIrcv3TagValue(tags, "draft/unreact", "+draft/unreact").isBlank()
        || !firstIrcv3TagValue(tags, "draft/delete", "+draft/delete").isBlank()
        || !firstIrcv3TagValue(tags, "draft/redact", "+draft/redact").isBlank();
  }

  private static String firstIrcv3TagValue(Map<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) {
      return "";
    }
    for (String key : keys) {
      String wanted = normalizeIrcv3TagKey(key);
      if (wanted.isEmpty()) {
        continue;
      }
      for (Map.Entry<String, String> entry : tags.entrySet()) {
        String actual = normalizeIrcv3TagKey(entry.getKey());
        if (!wanted.equals(actual)) {
          continue;
        }
        String raw = Objects.toString(entry.getValue(), "").trim();
        if (!raw.isEmpty()) {
          return raw;
        }
      }
    }
    return "";
  }

  private static String normalizeIrcv3TagKey(String rawKey) {
    String key = Objects.toString(rawKey, "").trim();
    if (key.startsWith("@")) {
      key = key.substring(1).trim();
    }
    if (key.startsWith("+")) {
      key = key.substring(1).trim();
    }
    if (key.isEmpty()) {
      return "";
    }
    return key.toLowerCase(Locale.ROOT);
  }

  private void clearRemoteTypingIndicatorsForSender(TargetRef target, String fromNick) {
    if (target == null) {
      return;
    }
    String nick = Objects.toString(fromNick, "").trim();
    if (nick.isEmpty()) {
      return;
    }

    ui.showTypingIndicator(target, nick, "done");
    if (!target.isChannel()) {
      return;
    }

    ui.showTypingActivity(target, "done");
    ui.showUsersTypingIndicator(target, nick, "done");
  }

  private boolean notifyIrcEvent(
      Callbacks callbacks,
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      String title,
      String body) {
    return notifyIrcEvent(
        callbacks, eventType, serverId, channel, sourceNick, title, body, null, null);
  }

  private boolean notifyIrcEvent(
      Callbacks callbacks,
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      String title,
      String body,
      String ctcpCommand,
      String ctcpValue) {
    return callbacks.notifyIrcEvent(
        eventType, serverId, channel, sourceNick, title, body, ctcpCommand, ctcpValue);
  }

  private static String normalizePrivateMessagePeer(String raw) {
    String nick = MediatorInboundEventPreparationService.normalizeNickForCompare(raw);
    nick = Objects.toString(nick, "").trim();
    if (nick.isEmpty()) {
      return "";
    }
    if ("server".equalsIgnoreCase(nick)) {
      return "";
    }
    if (nick.startsWith("*")) {
      return "";
    }
    return nick;
  }

  private static String snippetAround(String message, int start, int end) {
    if (message == null) {
      return "";
    }
    int len = message.length();
    if (len == 0) {
      return "";
    }

    int s = Math.max(0, Math.min(start, len));
    int e = Math.max(0, Math.min(end, len));
    if (e < s) {
      int tmp = s;
      s = e;
      e = tmp;
    }

    int context = 70;
    int from = Math.max(0, s - context);
    int to = Math.min(len, e + context);

    String snippet = message.substring(from, to).trim().replaceAll("\\s+", " ");
    if (from > 0) {
      snippet = "…" + snippet;
    }
    if (to < len) {
      snippet = snippet + "…";
    }

    int max = 200;
    if (snippet.length() > max) {
      snippet = snippet.substring(0, max - 1) + "…";
    }
    return snippet;
  }
}

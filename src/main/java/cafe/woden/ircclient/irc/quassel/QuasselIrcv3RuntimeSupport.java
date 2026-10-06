package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.ircv3.Ircv3ChannelContextRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundCommandSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3IsupportRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageIdRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageMutationRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageTagsRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.ircv3.Ircv3StandardReplyRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3TypingRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Runtime-provider bridge for IRCv3 behavior exposed by the Quassel transport adapter. */
@Component
@InfrastructureLayer
public final class QuasselIrcv3RuntimeSupport {

  private final Ircv3InboundCommandSignalRuntimeCatalog inboundCommandCatalog;
  private final Ircv3MessageTagsRuntimeCatalog messageTagsCatalog;
  private final Ircv3IsupportRuntimeSupport isupportRuntimeSupport;
  private final Ircv3StandardReplyRuntimeSupport standardReplyRuntimeSupport;
  private final Ircv3ChatHistoryRuntimeSupport chatHistoryRuntimeSupport;
  private final Ircv3ChannelContextRuntimeSupport channelContextRuntimeSupport;
  private final Ircv3MessageMutationRuntimeSupport messageMutationRuntimeSupport;
  private final Ircv3ReadMarkerRuntimeSupport readMarkerRuntimeSupport;
  private final Ircv3TypingRuntimeSupport typingRuntimeSupport;

  @Autowired
  public QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs catalogs) {
    this(
        Objects.requireNonNull(catalogs, "catalogs").outboundCommands(),
        catalogs.inboundTags(),
        catalogs.inboundCommands(),
        catalogs.messageTags());
  }

  public QuasselIrcv3RuntimeSupport(
      Ircv3OutboundCommandRuntimeCatalog outboundCatalog,
      Ircv3InboundTagSignalRuntimeCatalog inboundTagCatalog,
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommandCatalog,
      Ircv3MessageTagsRuntimeCatalog messageTagsCatalog) {
    Objects.requireNonNull(outboundCatalog, "outboundCatalog");
    Objects.requireNonNull(inboundTagCatalog, "inboundTagCatalog");
    this.inboundCommandCatalog =
        Objects.requireNonNull(inboundCommandCatalog, "inboundCommandCatalog");
    this.messageTagsCatalog = Objects.requireNonNull(messageTagsCatalog, "messageTagsCatalog");
    this.isupportRuntimeSupport = new Ircv3IsupportRuntimeSupport(this.inboundCommandCatalog);
    this.standardReplyRuntimeSupport =
        new Ircv3StandardReplyRuntimeSupport(
            this.inboundCommandCatalog, new Ircv3MessageIdRuntimeSupport(inboundTagCatalog));
    this.chatHistoryRuntimeSupport = new Ircv3ChatHistoryRuntimeSupport(outboundCatalog);
    this.channelContextRuntimeSupport = new Ircv3ChannelContextRuntimeSupport(inboundTagCatalog);
    this.messageMutationRuntimeSupport =
        Ircv3MessageMutationRuntimeSupport.inboundOnly(
            inboundTagCatalog, this.inboundCommandCatalog);
    this.readMarkerRuntimeSupport =
        new Ircv3ReadMarkerRuntimeSupport(
            outboundCatalog, inboundTagCatalog, this.inboundCommandCatalog);
    this.typingRuntimeSupport =
        new Ircv3TypingRuntimeSupport(
            outboundCatalog, inboundTagCatalog, this.inboundCommandCatalog);
  }

  public List<String> typingRawLines(String target, String state) {
    return typingRuntimeSupport
        .render(target, state)
        .map(plan -> List.of(plan.rawLine()))
        .orElseGet(List::of);
  }

  public List<String> readMarkerRawLines(String target, Instant markerAt) {
    return List.of(readMarkerRuntimeSupport.render(target, markerAt).rawLine());
  }

  public Ircv3ChatHistoryRuntimeSupport.Plan chatHistoryBefore(
      String target, String selector, int limit, Instant fallbackTimestamp) {
    return chatHistoryRuntimeSupport.before(target, selector, limit, fallbackTimestamp);
  }

  public Ircv3ChatHistoryRuntimeSupport.Plan chatHistoryLatest(
      String target, String selector, int limit) {
    return chatHistoryRuntimeSupport.latest(target, selector, limit);
  }

  public Ircv3ChatHistoryRuntimeSupport.Plan chatHistoryBetween(
      String target, String startSelector, String endSelector, int limit) {
    return chatHistoryRuntimeSupport.between(target, startSelector, endSelector, limit);
  }

  public Ircv3ChatHistoryRuntimeSupport.Plan chatHistoryAround(
      String target, String selector, int limit) {
    return chatHistoryRuntimeSupport.around(target, selector, limit);
  }

  public String channelContext(
      String command,
      String sourceNick,
      String rawTarget,
      List<String> parameters,
      Map<String, String> tags,
      String rawLine) {
    return channelContext(
        new Ircv3InboundTagRequest(command, sourceNick, rawTarget, parameters, tags, rawLine));
  }

  String channelContext(Ircv3InboundTagRequest request) {
    return channelContextRuntimeSupport.resolve(request);
  }

  public List<Ircv3InboundTagSignal> conversationSignals(
      String command,
      String sourceNick,
      String rawTarget,
      List<String> parameters,
      Map<String, String> tags,
      String rawLine) {
    return conversationSignals(
        new Ircv3InboundTagRequest(command, sourceNick, rawTarget, parameters, tags, rawLine));
  }

  List<Ircv3InboundTagSignal> conversationSignals(Ircv3InboundTagRequest request) {
    ArrayList<Ircv3InboundTagSignal> signals =
        new ArrayList<>(messageMutationRuntimeSupport.conversationSignals(request));
    typingRuntimeSupport
        .fromTags(request)
        .ifPresent(
            observed ->
                signals.add(
                    Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.TYPING, observed.state())));
    readMarkerRuntimeSupport
        .fromTags(request)
        .ifPresent(
            observed ->
                signals.add(
                    Ircv3InboundTagSignal.of(
                        Ircv3InboundTagSignalType.READ_MARKER, observed.marker())));
    return List.copyOf(signals);
  }

  public Optional<Ircv3ReadMarkerRuntimeSupport.CommandObservation> readMarkerFromCommand(
      String sourceNick,
      String command,
      String rawLine,
      List<String> parameters,
      Map<String, String> tags) {
    return readMarkerRuntimeSupport.fromCommand(
        new Ircv3InboundCommandRequest(sourceNick, command, rawLine, parameters, tags));
  }

  public Optional<Ircv3MessageMutationRuntimeSupport.CommandRedactionObservation>
      redactionFromCommand(
          String sourceNick,
          String command,
          String rawLine,
          List<String> parameters,
          Map<String, String> tags) {
    return messageMutationRuntimeSupport.redactionFromCommand(
        new Ircv3InboundCommandRequest(sourceNick, command, rawLine, parameters, tags));
  }

  public Optional<Ircv3IsupportRuntimeSupport.MonitorSupport> monitorSupport(String rawLine) {
    return isupportRuntimeSupport.monitorSupport(rawLine);
  }

  public Map<String, String> messageTags(String rawLine) {
    return messageTagsCatalog.parseRawLine(rawLine);
  }

  public List<Ircv3InboundCommandSignal> monitorSignals(String rawLine) {
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) {
      return List.of();
    }
    return inboundCommandCatalog.parse(
        Ircv3InboundCommandOperation.MONITOR,
        new Ircv3InboundCommandRequest("", "", raw, List.of(), Map.of()));
  }

  public Optional<Ircv3StandardReplyRuntimeSupport.Observation> standardReply(
      String command,
      String rawLine,
      List<String> parameters,
      String trailing,
      Map<String, String> tags,
      String fallbackMessageId) {
    ArrayList<String> providerParameters =
        new ArrayList<>(Objects.requireNonNullElse(parameters, List.of()));
    String description = Objects.toString(trailing, "").trim();
    if (!description.isEmpty()) {
      providerParameters.add(":" + description);
    }
    return standardReplyRuntimeSupport.observe(
        command, rawLine, providerParameters, tags, fallbackMessageId);
  }
}

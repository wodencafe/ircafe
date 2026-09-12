package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.config.api.Ircv3StsPolicyConfigPort;

/** Explicit installed-provider runtime wiring for focused transport tests. */
public final class Ircv3RuntimeTestFixtures {

  private Ircv3RuntimeTestFixtures() {}

  public static Ircv3RuntimeCatalogs catalogs() {
    return Ircv3RuntimeCatalogs.applicationClasspath();
  }

  public static Ircv3StsPolicyService stsPolicyService() {
    return stsPolicyService(null, catalogs());
  }

  public static Ircv3StsPolicyService stsPolicyService(Ircv3StsPolicyConfigPort runtimeConfig) {
    return stsPolicyService(runtimeConfig, catalogs());
  }

  public static Ircv3StsPolicyService stsPolicyService(Ircv3RuntimeCatalogs catalogs) {
    return stsPolicyService(null, catalogs);
  }

  public static Ircv3StsPolicyService stsPolicyService(
      Ircv3StsPolicyConfigPort runtimeConfig, Ircv3RuntimeCatalogs catalogs) {
    return new Ircv3StsPolicyService(runtimeConfig, catalogs.inboundCommands());
  }

  public static Ircv3MessageIdRuntimeSupport messageId() {
    return messageId(catalogs());
  }

  public static Ircv3MessageIdRuntimeSupport messageId(Ircv3RuntimeCatalogs catalogs) {
    return new Ircv3MessageIdRuntimeSupport(catalogs.inboundTags());
  }

  public static Ircv3SaslRuntimeSupport sasl() {
    return new Ircv3SaslRuntimeSupport(catalogs().inboundCommands());
  }

  public static Ircv3ZncPlaybackRuntimeSupport zncPlayback() {
    Ircv3RuntimeCatalogs catalogs = catalogs();
    return new Ircv3ZncPlaybackRuntimeSupport(catalogs.inboundCommands(), catalogs.inboundTags());
  }

  public static Ircv3ChatHistoryRuntimeSupport chatHistory() {
    return new Ircv3ChatHistoryRuntimeSupport(catalogs().outboundCommands());
  }

  public static Ircv3MonitorCommandRuntimeSupport monitorCommand() {
    return new Ircv3MonitorCommandRuntimeSupport(catalogs().outboundCommands());
  }

  public static Ircv3LabeledResponseRuntimeSupport labeledResponse() {
    return new Ircv3LabeledResponseRuntimeSupport(catalogs().inboundTags());
  }

  public static Runtime runtime() {
    Ircv3RuntimeCatalogs catalogs = catalogs();
    Ircv3MessageIdRuntimeSupport messageId = messageId(catalogs);
    Ircv3MessageTagsRuntimeSupport messageTags =
        new Ircv3MessageTagsRuntimeSupport(catalogs.messageTags(), messageId);
    Ircv3ServerTimeRuntimeSupport serverTime =
        new Ircv3ServerTimeRuntimeSupport(catalogs.inboundTags(), messageTags);
    Ircv3ZncPlaybackRuntimeSupport zncPlayback =
        new Ircv3ZncPlaybackRuntimeSupport(catalogs.inboundCommands(), catalogs.inboundTags());
    Ircv3MessageMutationRuntimeSupport messageMutation =
        new Ircv3MessageMutationRuntimeSupport(
            catalogs.messageMutations(), catalogs.inboundTags(), catalogs.inboundCommands());
    Ircv3ReadMarkerRuntimeSupport readMarker =
        new Ircv3ReadMarkerRuntimeSupport(
            catalogs.outboundCommands(), catalogs.inboundTags(), catalogs.inboundCommands());
    Ircv3AccountTagRuntimeSupport accountTag =
        new Ircv3AccountTagRuntimeSupport(catalogs.inboundTags());
    Ircv3ChannelContextRuntimeSupport channelContext =
        new Ircv3ChannelContextRuntimeSupport(catalogs.inboundTags());
    Ircv3CapabilityNegotiationRuntimeSupport capabilityNegotiation =
        new Ircv3CapabilityNegotiationRuntimeSupport(catalogs.inboundCommands());
    Ircv3StandardReplyRuntimeSupport standardReply =
        new Ircv3StandardReplyRuntimeSupport(catalogs.inboundCommands(), messageId);
    Ircv3MultilineCapabilityRuntimeSupport multiline =
        new Ircv3MultilineCapabilityRuntimeSupport(catalogs.inboundCommands());
    Ircv3IsupportRuntimeSupport isupport =
        new Ircv3IsupportRuntimeSupport(catalogs.inboundCommands());
    Ircv3TypingRuntimeSupport typing =
        new Ircv3TypingRuntimeSupport(
            catalogs.outboundCommands(), catalogs.inboundTags(), catalogs.inboundCommands());
    Ircv3SaslRuntimeSupport sasl = new Ircv3SaslRuntimeSupport(catalogs.inboundCommands());
    return new Runtime(
        catalogs,
        messageId,
        serverTime,
        messageTags,
        zncPlayback,
        messageMutation,
        readMarker,
        accountTag,
        channelContext,
        capabilityNegotiation,
        standardReply,
        multiline,
        isupport,
        typing,
        sasl);
  }

  public record Runtime(
      Ircv3RuntimeCatalogs catalogs,
      Ircv3MessageIdRuntimeSupport messageId,
      Ircv3ServerTimeRuntimeSupport serverTime,
      Ircv3MessageTagsRuntimeSupport messageTags,
      Ircv3ZncPlaybackRuntimeSupport zncPlayback,
      Ircv3MessageMutationRuntimeSupport messageMutation,
      Ircv3ReadMarkerRuntimeSupport readMarker,
      Ircv3AccountTagRuntimeSupport accountTag,
      Ircv3ChannelContextRuntimeSupport channelContext,
      Ircv3CapabilityNegotiationRuntimeSupport capabilityNegotiation,
      Ircv3StandardReplyRuntimeSupport standardReply,
      Ircv3MultilineCapabilityRuntimeSupport multiline,
      Ircv3IsupportRuntimeSupport isupport,
      Ircv3TypingRuntimeSupport typing,
      Ircv3SaslRuntimeSupport sasl) {
    public Ircv3StsPolicyService stsPolicyService() {
      return Ircv3RuntimeTestFixtures.stsPolicyService(catalogs);
    }
  }
}

package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** Shared catalog of IRCv3 capabilities users can request or toggle from the UI. */
public final class Ircv3CapabilityCatalog {

  private static final List<String> REQUESTABLE_CAPABILITIES =
      List.of(
          "multi-prefix",
          "cap-notify",
          "invite-notify",
          "away-notify",
          "account-notify",
          "monitor",
          "extended-monitor",
          "extended-join",
          "setname",
          "chghost",
          "message-tags",
          "server-time",
          "standard-replies",
          "echo-message",
          "labeled-response",
          "draft/read-marker",
          "draft/multiline",
          "draft/message-redaction",
          "batch",
          "draft/chathistory",
          "znc.in/playback",
          "account-tag",
          "userhost-in-names");

  private Ircv3CapabilityCatalog() {}

  public static List<String> requestableCapabilities() {
    return REQUESTABLE_CAPABILITIES;
  }
}

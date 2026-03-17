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
          "sts",
          "server-time",
          "standard-replies",
          "echo-message",
          "labeled-response",
          "draft/channel-context",
          "draft/reply",
          "draft/react",
          "draft/unreact",
          "draft/message-edit",
          "message-edit",
          "draft/message-redaction",
          "message-redaction",
          "draft/typing",
          "typing",
          "read-marker",
          "draft/read-marker",
          "multiline",
          "draft/multiline",
          "batch",
          "chathistory",
          "draft/chathistory",
          "znc.in/playback",
          "account-tag",
          "userhost-in-names");

  private Ircv3CapabilityCatalog() {}

  public static List<String> requestableCapabilities() {
    return REQUESTABLE_CAPABILITIES;
  }
}

package cafe.woden.ircclient.irc.ircv3;

import java.util.List;

/** Shared catalog of IRCv3 capabilities users can request or toggle from the UI. */
public final class Ircv3CapabilityCatalog {

  private Ircv3CapabilityCatalog() {}

  public static List<String> requestableCapabilities() {
    return Ircv3ExtensionRegistry.requestableCapabilityTokens();
  }
}

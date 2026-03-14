package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.hooks.events.WhoisEvent;

/** Emits structured WHOIS results and related observed user metadata. */
public final class PircbotxWhoisResultEmitter {
  private final String serverId;
  private final Consumer<ServerIrcEvent> emit;

  public PircbotxWhoisResultEmitter(String serverId, Consumer<ServerIrcEvent> emit) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.emit = Objects.requireNonNull(emit, "emit");
  }

  public void onWhois(WhoisEvent event) {
    if (event == null) return;

    try {
      String nick = PircbotxUtil.safeStr(() -> event.getNick(), "");
      String login = PircbotxUtil.safeStr(() -> event.getLogin(), "");
      String host = PircbotxUtil.safeStr(() -> event.getHostname(), "");
      String real = PircbotxUtil.safeStr(() -> event.getRealname(), "");
      String server = PircbotxUtil.safeStr(() -> event.getServer(), "");
      String serverInfo = PircbotxUtil.safeStr(() -> event.getServerInfo(), "");
      List<String> channels = PircbotxUtil.safeList(() -> event.getChannels());
      long idleSeconds = PircbotxUtil.safeLong(() -> event.getIdleSeconds(), -1);
      long signOnTime = PircbotxUtil.safeLong(() -> event.getSignOnTime(), -1);
      String registeredAs = PircbotxUtil.safeStr(() -> event.getRegisteredAs(), "");
      ArrayList<String> lines = new ArrayList<>();

      String ident = login.isBlank() ? "" : login;
      String hostPart = host.isBlank() ? "" : host;
      String userHost =
          (!ident.isBlank() || !hostPart.isBlank())
              ? (ident + "@" + hostPart).replaceAll("^@|@$", "")
              : "";
      if (!nick.isBlank() && !userHost.isBlank() && userHost.contains("@")) {
        String observed = nick + "!" + userHost;
        emit.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserHostmaskObserved(Instant.now(), "", nick, observed)));
      }

      if (!userHost.isBlank()) lines.add("User: " + userHost);
      if (!real.isBlank()) lines.add("Realname: " + real);
      if (!server.isBlank()) {
        if (!serverInfo.isBlank()) lines.add("Server: " + server + " (" + serverInfo + ")");
        else lines.add("Server: " + server);
      }
      if (!registeredAs.isBlank()) lines.add("Account: " + registeredAs);
      if (idleSeconds >= 0) lines.add("Idle: " + idleSeconds + "s");
      if (signOnTime > 0) lines.add("Sign-on: " + signOnTime);
      if (channels != null && !channels.isEmpty()) {
        lines.add("Channels: " + String.join(" ", channels));
      }
      if (lines.isEmpty()) lines.add("(no WHOIS details)");

      String resolvedNick = nick == null || nick.isBlank() ? "(unknown)" : nick;
      emit.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.WhoisResult(Instant.now(), resolvedNick, List.copyOf(lines))));
    } catch (Exception ex) {
      emit.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.Error(Instant.now(), "Whois parse failed", ex)));
    }
  }
}

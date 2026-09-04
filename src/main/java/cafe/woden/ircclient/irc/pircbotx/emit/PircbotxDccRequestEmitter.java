package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3ServerTimeRuntimeSupport;
import java.net.InetAddress;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.User;
import org.pircbotx.UserHostmask;
import org.pircbotx.hooks.events.IncomingChatRequestEvent;
import org.pircbotx.hooks.events.IncomingFileTransferEvent;

/** Translates PircBotX's specialized inbound DCC callbacks into application CTCP events. */
public final class PircbotxDccRequestEmitter {
  private final String serverId;
  private final Consumer<ServerIrcEvent> emit;
  private final Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport;

  public PircbotxDccRequestEmitter(
      String serverId,
      Consumer<ServerIrcEvent> emit,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.emit = Objects.requireNonNull(emit, "emit");
    this.serverTimeRuntimeSupport =
        Objects.requireNonNull(serverTimeRuntimeSupport, "serverTimeRuntimeSupport");
  }

  public void onIncomingChatRequest(IncomingChatRequestEvent event) {
    if (event == null) return;

    String argument =
        appendToken(
            "CHAT chat " + address(event.getAddress()) + " " + event.getPort(), event.getToken());
    emit(event, sender(event.getUser(), event.getUserHostmask()), argument);
  }

  public void onIncomingFileTransfer(IncomingFileTransferEvent event) {
    if (event == null) return;

    String fileName = Objects.toString(event.getRawFilename(), "").replace('"', '_');
    String argument =
        appendToken(
            "SEND \""
                + fileName
                + "\" "
                + address(event.getAddress())
                + " "
                + event.getPort()
                + " "
                + event.getFilesize(),
            event.getToken());
    emit(event, sender(event.getUser(), event.getUserHostmask()), argument);
  }

  private void emit(Object event, String from, String argument) {
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.CtcpRequestReceived(
                serverTimeRuntimeSupport.resolveEventOrNow(event), from, "DCC", argument, null)));
  }

  private static String sender(User user, UserHostmask userHostmask) {
    String nick = Objects.toString(user != null ? user.getNick() : "", "").trim();
    if (!nick.isEmpty()) return nick;
    nick = Objects.toString(userHostmask != null ? userHostmask.getNick() : "", "").trim();
    return nick.isEmpty() ? "server" : nick;
  }

  private static String address(InetAddress address) {
    return address == null ? "" : address.getHostAddress();
  }

  private static String appendToken(String argument, String token) {
    String normalizedToken = Objects.toString(token, "").trim();
    return normalizedToken.isEmpty() ? argument : argument + " " + normalizedToken;
  }
}

package cafe.woden.ircclient.app.outbound.dcc;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Shared active DCC chat session lifecycle support. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class DccChatSessionSupport {

  private static final String DCC_TAG = "(dcc)";
  private static final String DCC_ERR_TAG = "(dcc-error)";

  @NonNull private final UiPort ui;

  @Qualifier("ircMediatorInteractionPort")
  @NonNull
  private final IrcMediatorInteractionPort mediatorIrc;

  @Qualifier(ExecutorConfig.OUTBOUND_DCC_EXECUTOR)
  @NonNull
  private final ExecutorService io;

  @NonNull private final DccCommandSupport dccCommandSupport;
  @NonNull private final DccRuntimeRegistry dccRuntimeRegistry;

  boolean sendChatMessage(String sid, String nick, String message) {
    String key = DccCommandSupport.peerKey(sid, nick);
    ConcurrentMap<String, DccChatSession> chatSessions = dccRuntimeRegistry.chatSessions();
    DccChatSession session = chatSessions.get(key);
    if (session == null) {
      return false;
    }

    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, nick);
    try {
      synchronized (session.writeLock()) {
        session.writer().write(message);
        session.writer().write("\r\n");
        session.writer().flush();
      }
      String me = mediatorIrc.currentNick(sid).orElse("me");
      ui.appendChat(pm, "(" + me + ")", "[DCC] " + message, true);
      return true;
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "Failed to write DCC chat message: " + e.getMessage());
      closeChatSession(sid, nick, "write-failed", true);
      return true;
    }
  }

  void startChatSession(String sid, String nick, Socket socket, String connectedText)
      throws IOException {
    String key = DccCommandSupport.peerKey(sid, nick);
    ConcurrentMap<String, DccChatSession> chatSessions = dccRuntimeRegistry.chatSessions();
    DccChatSession previous = chatSessions.remove(key);
    if (previous != null) {
      closeQuietly(previous.socket());
    }

    BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    DccChatSession session =
        new DccChatSession(sid, nick, socket, writer, new Object(), new AtomicBoolean(false));
    chatSessions.put(key, session);

    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, nick);
    ui.appendStatus(pm, DCC_TAG, "DCC CHAT " + connectedText + " with " + nick + ".");
    if (connectedText != null && connectedText.contains("outgoing")) {
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "chat-out"),
          "Chat (outgoing)",
          "Connected",
          "",
          null,
          DccTransferStore.ActionHint.NONE);
    }
    if (connectedText != null && connectedText.contains("incoming")) {
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "chat-in"),
          "Chat (incoming)",
          "Connected",
          "",
          null,
          DccTransferStore.ActionHint.NONE);
    }
    dccCommandSupport.upsertTransfer(
        sid,
        nick,
        DccCommandSupport.transferEntryId(sid, nick, "chat-active"),
        "Chat",
        "Active",
        connectedText,
        null,
        DccTransferStore.ActionHint.CLOSE_CHAT);

    io.execute(() -> readDccChatLoop(key, session));
  }

  boolean closeChatSession(String sid, String nick, String message, boolean announce) {
    String key = DccCommandSupport.peerKey(sid, nick);
    ConcurrentMap<String, DccChatSession> chatSessions = dccRuntimeRegistry.chatSessions();
    DccChatSession session = chatSessions.remove(key);
    if (session == null) return false;
    session.closing().set(true);
    closeQuietly(session.socket());
    if (announce) {
      TargetRef pm = dccCommandSupport.ensurePmTarget(sid, nick);
      ui.appendStatus(pm, DCC_TAG, message);
    }
    dccCommandSupport.upsertTransfer(
        sid,
        nick,
        DccCommandSupport.transferEntryId(sid, nick, "chat-active"),
        "Chat",
        "Closed",
        "",
        null,
        DccTransferStore.ActionHint.NONE);
    return true;
  }

  void shutdown() {
    ConcurrentMap<String, DccChatSession> chatSessions = dccRuntimeRegistry.chatSessions();
    for (DccChatSession session : chatSessions.values()) {
      if (session != null) {
        session.closing().set(true);
        closeQuietly(session.socket());
      }
    }
    chatSessions.clear();
  }

  private void readDccChatLoop(String key, DccChatSession session) {
    TargetRef pm = dccCommandSupport.ensurePmTarget(session.serverId(), session.nick());
    ConcurrentMap<String, DccChatSession> chatSessions = dccRuntimeRegistry.chatSessions();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(session.socket().getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String text = line;
        if (text.endsWith("\r")) {
          text = text.substring(0, text.length() - 1);
        }
        ui.appendChat(pm, session.nick(), "[DCC] " + text, false);
        dccCommandSupport.markUnreadIfInactive(pm);
      }
      if (!session.closing().get()) {
        ui.appendStatus(pm, DCC_TAG, session.nick() + " closed the DCC CHAT session.");
        dccCommandSupport.upsertTransfer(
            session.serverId(),
            session.nick(),
            DccCommandSupport.transferEntryId(session.serverId(), session.nick(), "chat-active"),
            "Chat",
            "Peer closed",
            "",
            null,
            DccTransferStore.ActionHint.NONE);
      }
    } catch (Exception e) {
      if (!session.closing().get()) {
        ui.appendError(pm, DCC_ERR_TAG, "DCC chat connection lost: " + e.getMessage());
        dccCommandSupport.upsertTransfer(
            session.serverId(),
            session.nick(),
            DccCommandSupport.transferEntryId(session.serverId(), session.nick(), "chat-active"),
            "Chat",
            "Connection lost",
            e.getMessage(),
            null,
            DccTransferStore.ActionHint.NONE);
      }
    } finally {
      chatSessions.remove(key, session);
      closeQuietly(session.socket());
    }
  }

  private static void closeQuietly(Socket socket) {
    if (socket == null) return;
    try {
      socket.close();
    } catch (Exception ignored) {
    }
  }
}

record DccChatSession(
    String serverId,
    String nick,
    Socket socket,
    BufferedWriter writer,
    Object writeLock,
    AtomicBoolean closing) {}

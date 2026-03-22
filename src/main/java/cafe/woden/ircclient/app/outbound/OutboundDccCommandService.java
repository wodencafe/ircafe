package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Handles DCC chat and file transfer commands plus inbound DCC offers.
 *
 * <p>Supported commands: /dcc chat &lt;nick&gt; /dcc send &lt;nick&gt; &lt;file-path&gt; /dcc
 * accept &lt;nick&gt; /dcc get &lt;nick&gt; [save-path] /dcc msg &lt;nick&gt; &lt;text&gt; /dcc
 * close &lt;nick&gt; /dcc list
 */
@Component
@ApplicationLayer
public class OutboundDccCommandService {
  private static final int OFFER_ACCEPT_TIMEOUT_MS = 120_000;
  private static final int CONNECT_TIMEOUT_MS = 20_000;
  private static final int IO_TIMEOUT_MS = 30_000;
  private static final int BUFFER_SIZE = 64 * 1024;
  private static final String DCC_TAG = "(dcc)";
  private static final String DCC_ERR_TAG = "(dcc-error)";

  private final UiPort ui;

  @Qualifier("ircMediatorInteractionPort")
  private final IrcMediatorInteractionPort mediatorIrc;

  private final TargetCoordinator targetCoordinator;
  private final ConnectionCoordinator connectionCoordinator;

  @Qualifier(ExecutorConfig.OUTBOUND_DCC_EXECUTOR)
  private final ExecutorService io;

  private final DccCommandSupport dccCommandSupport;
  private final DccChatSessionSupport dccChatSessionSupport;
  private final DccInboundOfferSupport dccInboundOfferSupport;

  private final ConcurrentMap<String, PendingChatOffer> pendingChatOffers =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, PendingSendOffer> pendingSendOffers =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, DccChatSession> chatSessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ServerSocket> outgoingChatListeners =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ServerSocket> outgoingSendListeners =
      new ConcurrentHashMap<>();

  public OutboundDccCommandService(
      UiPort ui,
      IrcMediatorInteractionPort mediatorIrc,
      TargetCoordinator targetCoordinator,
      ConnectionCoordinator connectionCoordinator,
      DccTransferStore dccTransferStore,
      ExecutorService io) {
    this.ui = ui;
    this.mediatorIrc = mediatorIrc;
    this.targetCoordinator = targetCoordinator;
    this.connectionCoordinator = connectionCoordinator;
    this.io = io;
    this.dccCommandSupport = new DccCommandSupport(ui, targetCoordinator, dccTransferStore);
    this.dccChatSessionSupport =
        new DccChatSessionSupport(ui, mediatorIrc, io, dccCommandSupport, chatSessions);
    this.dccInboundOfferSupport =
        new DccInboundOfferSupport(dccCommandSupport, pendingChatOffers, pendingSendOffers);
  }

  public void handleDcc(
      CompositeDisposable disposables, String subcommand, String nick, String argument) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef out = (at != null) ? at : targetCoordinator.safeStatusTarget();
    String sid = (at == null) ? "" : normalizeToken(at.serverId());
    String sub = normalizeToken(subcommand).toLowerCase(Locale.ROOT);
    String n = normalizeToken(nick);
    String arg = Objects.toString(argument, "").trim();

    if (sub.isEmpty() || "help".equals(sub)) {
      appendUsage(out);
      return;
    }

    if (sid.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Select a server first.");
      return;
    }

    switch (sub) {
      case "chat" -> offerChat(disposables, sid, out, n);
      case "send" -> offerSend(disposables, sid, out, n, arg);
      case "accept" -> acceptChatOffer(sid, out, n);
      case "get", "recv", "receive" -> acceptSendOffer(sid, out, n, arg);
      case "msg" -> sendChatMessage(sid, out, n, arg);
      case "close" -> closeChatSessionByCommand(sid, out, n);
      case "list" -> listDccState(sid, out);
      case "panel", "transfers" -> openDccTransfersPanel(sid);
      default -> {
        ui.appendStatus(out, DCC_TAG, "Unknown /dcc subcommand: " + sub);
        appendUsage(out);
      }
    }
  }

  /**
   * Handles inbound CTCP DCC payloads from private messages.
   *
   * @return true when the payload is recognized as DCC and consumed.
   */
  public boolean handleInboundDccOffer(
      Instant at, String serverId, String fromNick, String dccArgument, boolean spoiler) {
    return dccInboundOfferSupport.handleInboundDccOffer(
        at, serverId, fromNick, dccArgument, spoiler);
  }

  private void offerChat(CompositeDisposable disposables, String sid, TargetRef out, String nick) {
    String n = normalizeNick(nick);
    if (n.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc chat <nick>");
      return;
    }

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(new TargetRef(sid, "status"), "(conn)", "Not connected");
      return;
    }

    InetAddress advertised = resolveAdvertisableIpv4();
    if (advertised == null) {
      ui.appendError(out, DCC_ERR_TAG, "Could not determine a local IPv4 address for DCC.");
      return;
    }

    long ipAsLong = ipv4AsUnsignedLong(advertised);
    if (ipAsLong < 0L) {
      ui.appendError(out, DCC_ERR_TAG, "DCC currently requires IPv4.");
      return;
    }

    String key = peerKey(sid, n);
    TargetRef pm = ensurePmTarget(sid, n);

    try {
      ServerSocket listener = new ServerSocket();
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress(0));
      listener.setSoTimeout(OFFER_ACCEPT_TIMEOUT_MS);
      replaceListener(outgoingChatListeners, key, listener);

      int port = listener.getLocalPort();
      String ctcp = "\u0001DCC CHAT chat " + ipAsLong + " " + port + "\u0001";
      ui.appendStatus(
          pm,
          DCC_TAG,
          "Offering DCC CHAT to " + n + " on " + advertised.getHostAddress() + ":" + port);
      upsertTransfer(
          sid,
          n,
          transferEntryId(sid, n, "chat-out"),
          "Chat (outgoing)",
          "Offering",
          advertised.getHostAddress() + ":" + port,
          null,
          DccTransferStore.ActionHint.NONE);

      disposables.add(
          mediatorIrc
              .sendPrivateMessage(sid, n, ctcp)
              .subscribe(
                  () -> {
                    ui.appendStatus(pm, DCC_TAG, "Offer sent. Waiting for " + n + " to connect…");
                    upsertTransfer(
                        sid,
                        n,
                        transferEntryId(sid, n, "chat-out"),
                        "Chat (outgoing)",
                        "Waiting for peer",
                        advertised.getHostAddress() + ":" + port,
                        null,
                        DccTransferStore.ActionHint.NONE);
                    io.execute(() -> awaitOutgoingChatConnection(sid, n, key, listener));
                  },
                  err -> {
                    removeListener(outgoingChatListeners, key, listener);
                    ui.appendError(pm, DCC_ERR_TAG, String.valueOf(err));
                    upsertTransfer(
                        sid,
                        n,
                        transferEntryId(sid, n, "chat-out"),
                        "Chat (outgoing)",
                        "Failed to send offer",
                        String.valueOf(err),
                        null,
                        DccTransferStore.ActionHint.NONE);
                  }));
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "Could not start DCC chat listener: " + e.getMessage());
      upsertTransfer(
          sid,
          n,
          transferEntryId(sid, n, "chat-out"),
          "Chat (outgoing)",
          "Failed to listen",
          e.getMessage(),
          null,
          DccTransferStore.ActionHint.NONE);
    }
  }

  private void awaitOutgoingChatConnection(
      String sid, String nick, String key, ServerSocket listener) {
    TargetRef pm = ensurePmTarget(sid, nick);
    try (ServerSocket ignored = listener) {
      Socket socket = listener.accept();
      dccChatSessionSupport.startChatSession(sid, nick, socket, "connected (outgoing)");
    } catch (SocketTimeoutException e) {
      ui.appendStatus(pm, DCC_TAG, "DCC CHAT offer to " + nick + " timed out.");
      upsertTransfer(
          sid,
          nick,
          transferEntryId(sid, nick, "chat-out"),
          "Chat (outgoing)",
          "Timed out",
          "",
          null,
          DccTransferStore.ActionHint.NONE);
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "DCC CHAT accept failed: " + e.getMessage());
      upsertTransfer(
          sid,
          nick,
          transferEntryId(sid, nick, "chat-out"),
          "Chat (outgoing)",
          "Failed",
          e.getMessage(),
          null,
          DccTransferStore.ActionHint.NONE);
    } finally {
      removeListener(outgoingChatListeners, key, listener);
    }
  }

  private void offerSend(
      CompositeDisposable disposables, String sid, TargetRef out, String nick, String filePathArg) {
    String n = normalizeNick(nick);
    if (n.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc send <nick> <file-path>");
      return;
    }

    String rawPath = Objects.toString(filePathArg, "").trim();
    if (rawPath.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc send <nick> <file-path>");
      return;
    }

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(new TargetRef(sid, "status"), "(conn)", "Not connected");
      return;
    }

    Path source = expandPath(rawPath);
    if (!Files.isRegularFile(source)) {
      ui.appendError(out, DCC_ERR_TAG, "File not found: " + source);
      return;
    }

    long size;
    try {
      size = Files.size(source);
    } catch (Exception e) {
      ui.appendError(out, DCC_ERR_TAG, "Could not stat file: " + e.getMessage());
      return;
    }

    InetAddress advertised = resolveAdvertisableIpv4();
    if (advertised == null) {
      ui.appendError(out, DCC_ERR_TAG, "Could not determine a local IPv4 address for DCC.");
      return;
    }
    long ipAsLong = ipv4AsUnsignedLong(advertised);
    if (ipAsLong < 0L) {
      ui.appendError(out, DCC_ERR_TAG, "DCC currently requires IPv4.");
      return;
    }

    String key = peerKey(sid, n);
    TargetRef pm = ensurePmTarget(sid, n);
    String fileName =
        sanitizeOfferFileName(
            source.getFileName() == null ? "file.bin" : source.getFileName().toString());
    String fileToken = encodeDccFileName(fileName);

    try {
      ServerSocket listener = new ServerSocket();
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress(0));
      listener.setSoTimeout(OFFER_ACCEPT_TIMEOUT_MS);
      replaceListener(outgoingSendListeners, key, listener);

      int port = listener.getLocalPort();
      String ctcp =
          "\u0001DCC SEND " + fileToken + " " + ipAsLong + " " + port + " " + size + "\u0001";
      ui.appendStatus(
          pm, DCC_TAG, "Offering file " + fileName + " (" + formatBytes(size) + ") to " + n);
      upsertTransfer(
          sid,
          n,
          transferEntryId(sid, n, "send-out"),
          "Send file (outgoing)",
          "Offering",
          fileName + " (" + formatBytes(size) + ")",
          0,
          DccTransferStore.ActionHint.NONE);

      disposables.add(
          mediatorIrc
              .sendPrivateMessage(sid, n, ctcp)
              .subscribe(
                  () -> {
                    ui.appendStatus(pm, DCC_TAG, "Offer sent. Waiting for " + n + " to connect…");
                    upsertTransfer(
                        sid,
                        n,
                        transferEntryId(sid, n, "send-out"),
                        "Send file (outgoing)",
                        "Waiting for peer",
                        fileName + " (" + formatBytes(size) + ")",
                        0,
                        DccTransferStore.ActionHint.NONE);
                    io.execute(
                        () ->
                            awaitOutgoingSendConnection(
                                sid, n, key, listener, source, fileName, size));
                  },
                  err -> {
                    removeListener(outgoingSendListeners, key, listener);
                    ui.appendError(pm, DCC_ERR_TAG, String.valueOf(err));
                    upsertTransfer(
                        sid,
                        n,
                        transferEntryId(sid, n, "send-out"),
                        "Send file (outgoing)",
                        "Failed to send offer",
                        String.valueOf(err),
                        0,
                        DccTransferStore.ActionHint.NONE);
                  }));
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "Could not start DCC send listener: " + e.getMessage());
      upsertTransfer(
          sid,
          n,
          transferEntryId(sid, n, "send-out"),
          "Send file (outgoing)",
          "Failed to listen",
          e.getMessage(),
          0,
          DccTransferStore.ActionHint.NONE);
    }
  }

  private void awaitOutgoingSendConnection(
      String sid,
      String nick,
      String key,
      ServerSocket listener,
      Path source,
      String displayName,
      long size) {
    TargetRef pm = ensurePmTarget(sid, nick);
    try (ServerSocket ignored = listener;
        Socket socket = listener.accept()) {
      socket.setSoTimeout(IO_TIMEOUT_MS);
      ui.appendStatus(pm, DCC_TAG, nick + " connected. Sending " + displayName + "…");
      upsertTransfer(
          sid,
          nick,
          transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Transferring",
          displayName + " (" + formatBytes(size) + ")",
          0,
          DccTransferStore.ActionHint.NONE);
      sendFileToSocket(sid, nick, pm, socket, source, displayName, size);
      ui.appendStatus(
          pm, DCC_TAG, "DCC SEND complete: " + displayName + " (" + formatBytes(size) + ")");
      upsertTransfer(
          sid,
          nick,
          transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Completed",
          displayName + " (" + formatBytes(size) + ")",
          100,
          DccTransferStore.ActionHint.NONE);
    } catch (SocketTimeoutException e) {
      ui.appendStatus(pm, DCC_TAG, "DCC SEND offer to " + nick + " timed out.");
      upsertTransfer(
          sid,
          nick,
          transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Timed out",
          displayName + " (" + formatBytes(size) + ")",
          null,
          DccTransferStore.ActionHint.NONE);
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "DCC SEND failed: " + e.getMessage());
      upsertTransfer(
          sid,
          nick,
          transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Failed",
          e.getMessage(),
          null,
          DccTransferStore.ActionHint.NONE);
    } finally {
      removeListener(outgoingSendListeners, key, listener);
    }
  }

  private void acceptChatOffer(String sid, TargetRef out, String nick) {
    String n = normalizeNick(nick);
    if (n.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc accept <nick>");
      return;
    }

    String key = peerKey(sid, n);
    PendingChatOffer offer = pendingChatOffers.remove(key);
    if (offer == null) {
      ui.appendStatus(out, DCC_TAG, "No pending DCC CHAT offer from " + n + ".");
      return;
    }

    TargetRef pm = ensurePmTarget(sid, n);
    ui.appendStatus(
        pm,
        DCC_TAG,
        "Connecting to DCC CHAT " + offer.host().getHostAddress() + ":" + offer.port() + " …");
    upsertTransfer(
        sid,
        n,
        transferEntryId(sid, n, "chat-in"),
        "Chat (incoming)",
        "Connecting",
        offer.host().getHostAddress() + ":" + offer.port(),
        null,
        DccTransferStore.ActionHint.NONE);

    io.execute(
        () -> {
          Socket socket = new Socket();
          try {
            socket.connect(new InetSocketAddress(offer.host(), offer.port()), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(IO_TIMEOUT_MS);
            dccChatSessionSupport.startChatSession(sid, n, socket, "connected (incoming)");
          } catch (Exception e) {
            closeQuietly(socket);
            ui.appendError(pm, DCC_ERR_TAG, "DCC CHAT connect failed: " + e.getMessage());
            upsertTransfer(
                sid,
                n,
                transferEntryId(sid, n, "chat-in"),
                "Chat (incoming)",
                "Failed",
                e.getMessage(),
                null,
                DccTransferStore.ActionHint.NONE);
          }
        });
  }

  private void acceptSendOffer(String sid, TargetRef out, String nick, String saveArg) {
    String n = normalizeNick(nick);
    if (n.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc get <nick> [save-path]");
      return;
    }

    String key = peerKey(sid, n);
    PendingSendOffer offer = pendingSendOffers.get(key);
    if (offer == null) {
      ui.appendStatus(out, DCC_TAG, "No pending DCC SEND offer from " + n + ".");
      return;
    }

    Path destination = resolveDownloadPath(offer.fileName(), saveArg);
    if (destination == null) {
      ui.appendError(out, DCC_ERR_TAG, "Invalid destination path.");
      return;
    }

    if (Files.exists(destination)) {
      ui.appendError(out, DCC_ERR_TAG, "Destination already exists: " + destination);
      return;
    }

    pendingSendOffers.remove(key, offer);
    TargetRef pm = ensurePmTarget(sid, n);
    ui.appendStatus(pm, DCC_TAG, "Receiving " + offer.fileName() + " to " + destination + " …");
    String localPath = destination.toAbsolutePath().normalize().toString();
    upsertTransfer(
        sid,
        n,
        transferEntryId(sid, n, "send-in"),
        "Receive file (incoming)",
        "Connecting",
        offer.fileName() + " -> " + destination.getFileName(),
        localPath,
        0,
        DccTransferStore.ActionHint.NONE);

    io.execute(
        () -> {
          boolean completed = false;
          try {
            Path parent = destination.toAbsolutePath().getParent();
            if (parent != null) {
              Files.createDirectories(parent);
            }
            receiveFileFromOffer(sid, n, pm, offer, destination, localPath);
            completed = true;
            ui.appendStatus(
                pm,
                DCC_TAG,
                "DCC GET complete: "
                    + destination.getFileName()
                    + " ("
                    + formatBytes(offer.size())
                    + ")");
            upsertTransfer(
                sid,
                n,
                transferEntryId(sid, n, "send-in"),
                "Receive file (incoming)",
                "Completed",
                destination.getFileName() + " (" + formatBytes(offer.size()) + ")",
                localPath,
                100,
                DccTransferStore.ActionHint.NONE);
          } catch (Exception e) {
            ui.appendError(pm, DCC_ERR_TAG, "DCC GET failed: " + e.getMessage());
            upsertTransfer(
                sid,
                n,
                transferEntryId(sid, n, "send-in"),
                "Receive file (incoming)",
                "Failed",
                e.getMessage(),
                localPath,
                null,
                DccTransferStore.ActionHint.NONE);
          } finally {
            if (!completed) {
              try {
                Files.deleteIfExists(destination);
              } catch (Exception ignored) {
              }
              pendingSendOffers.putIfAbsent(key, offer);
              upsertTransfer(
                  sid,
                  n,
                  transferEntryId(sid, n, "send-in"),
                  "Receive file (incoming)",
                  "Offer pending",
                  offer.fileName() + " (" + formatBytes(offer.size()) + ")",
                  localPath,
                  0,
                  DccTransferStore.ActionHint.GET_FILE);
            }
          }
        });
  }

  private void sendChatMessage(String sid, TargetRef out, String nick, String text) {
    String n = normalizeNick(nick);
    String message = Objects.toString(text, "").trim();
    if (n.isEmpty() || message.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc msg <nick> <text>");
      return;
    }

    if (!dccChatSessionSupport.sendChatMessage(sid, n, message)) {
      ui.appendStatus(out, DCC_TAG, "No active DCC chat session with " + n + ".");
    }
  }

  private void closeChatSessionByCommand(String sid, TargetRef out, String nick) {
    String n = normalizeNick(nick);
    if (n.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc close <nick>");
      return;
    }

    boolean closed =
        dccChatSessionSupport.closeChatSession(sid, n, "Closed DCC CHAT session.", true);
    if (!closed) {
      ui.appendStatus(out, DCC_TAG, "No active DCC chat session with " + n + ".");
    }
  }

  private void listDccState(String sid, TargetRef out) {
    int activeChats = 0;
    for (DccChatSession session : chatSessions.values()) {
      if (sid.equals(session.serverId())) activeChats++;
    }

    int pendingChats = 0;
    for (PendingChatOffer offer : pendingChatOffers.values()) {
      if (sid.equals(offer.serverId())) pendingChats++;
    }

    int pendingSends = 0;
    for (PendingSendOffer offer : pendingSendOffers.values()) {
      if (sid.equals(offer.serverId())) pendingSends++;
    }

    ui.appendStatus(
        out,
        DCC_TAG,
        "DCC state: active chats="
            + activeChats
            + ", pending chat offers="
            + pendingChats
            + ", pending sends="
            + pendingSends);

    for (PendingChatOffer offer : pendingChatOffers.values()) {
      if (!sid.equals(offer.serverId())) continue;
      ui.appendStatus(
          out,
          DCC_TAG,
          "Pending CHAT from "
              + offer.fromNick()
              + " at "
              + offer.host().getHostAddress()
              + ":"
              + offer.port());
    }

    for (PendingSendOffer offer : pendingSendOffers.values()) {
      if (!sid.equals(offer.serverId())) continue;
      ui.appendStatus(
          out,
          DCC_TAG,
          "Pending SEND from "
              + offer.fromNick()
              + ": "
              + offer.fileName()
              + " ("
              + formatBytes(offer.size())
              + ")");
    }

    for (DccChatSession session : chatSessions.values()) {
      if (!sid.equals(session.serverId())) continue;
      ui.appendStatus(out, DCC_TAG, "Active CHAT with " + session.nick());
    }
  }

  private void openDccTransfersPanel(String sid) {
    String serverId = normalizeToken(sid);
    if (serverId.isEmpty()) return;
    TargetRef panel = TargetRef.dccTransfers(serverId);
    ui.ensureTargetExists(panel);
    ui.selectTarget(panel);
  }

  private void appendUsage(TargetRef out) {
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc chat <nick>");
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc send <nick> <file-path>");
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc accept <nick>");
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc get <nick> [save-path]");
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc msg <nick> <text>  (alias: /dccmsg <nick> <text>)");
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc close <nick>");
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc list");
    ui.appendStatus(out, DCC_TAG, "Usage: /dcc panel");
  }

  private void sendFileToSocket(
      String sid,
      String nick,
      TargetRef pm,
      Socket socket,
      Path source,
      String displayName,
      long size)
      throws IOException {
    long sent = 0L;
    int lastReportedPercent = -1;
    try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(source));
        BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
      byte[] buf = new byte[BUFFER_SIZE];
      int n;
      while ((n = in.read(buf)) >= 0) {
        out.write(buf, 0, n);
        sent += n;
        int pct = percent(sent, size);
        if (shouldReportProgress(lastReportedPercent, pct)) {
          lastReportedPercent = pct;
          ui.appendStatus(pm, DCC_TAG, "Sending " + displayName + " … " + pct + "%");
          upsertTransfer(
              sid,
              nick,
              transferEntryId(sid, nick, "send-out"),
              "Send file (outgoing)",
              "Transferring",
              displayName + " (" + formatBytes(size) + ")",
              pct,
              DccTransferStore.ActionHint.NONE);
        }
      }
      out.flush();
    }
  }

  private void receiveFileFromOffer(
      String sid,
      String nick,
      TargetRef pm,
      PendingSendOffer offer,
      Path destination,
      String localPath)
      throws IOException {
    long expected = offer.size();
    long received = 0L;
    int lastReportedPercent = -1;

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(offer.host(), offer.port()), CONNECT_TIMEOUT_MS);
      socket.setSoTimeout(IO_TIMEOUT_MS);

      try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
          DataOutputStream ack =
              new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
          BufferedOutputStream fileOut =
              new BufferedOutputStream(
                  Files.newOutputStream(
                      destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
        byte[] buf = new byte[BUFFER_SIZE];
        while (expected <= 0L || received < expected) {
          int max =
              (expected > 0L) ? (int) Math.min((long) buf.length, expected - received) : buf.length;
          int n = in.read(buf, 0, max);
          if (n < 0) break;
          fileOut.write(buf, 0, n);
          received += n;

          ack.writeInt((int) (received & 0xFFFF_FFFFL));
          ack.flush();

          int pct = percent(received, expected);
          if (shouldReportProgress(lastReportedPercent, pct)) {
            lastReportedPercent = pct;
            ui.appendStatus(pm, DCC_TAG, "Receiving " + offer.fileName() + " … " + pct + "%");
            upsertTransfer(
                sid,
                nick,
                transferEntryId(sid, nick, "send-in"),
                "Receive file (incoming)",
                "Transferring",
                offer.fileName() + " -> " + destination.getFileName(),
                localPath,
                pct,
                DccTransferStore.ActionHint.NONE);
          }
        }
        fileOut.flush();
      }
    }

    if (expected > 0L && received != expected) {
      throw new IOException("Transfer ended early (" + received + " / " + expected + " bytes)");
    }
  }

  private static boolean shouldReportProgress(int previousPercent, int currentPercent) {
    if (currentPercent < 0) return false;
    if (currentPercent == 100 && previousPercent != 100) return true;
    return currentPercent >= previousPercent + 10;
  }

  private static int percent(long value, long total) {
    if (total <= 0L) return -1;
    if (value <= 0L) return 0;
    long p = (value * 100L) / total;
    if (p < 0L) p = 0L;
    if (p > 100L) p = 100L;
    return (int) p;
  }

  private TargetRef ensurePmTarget(String sid, String nick) {
    return dccCommandSupport.ensurePmTarget(sid, nick);
  }

  private void markUnreadIfInactive(TargetRef target) {
    dccCommandSupport.markUnreadIfInactive(target);
  }

  private void upsertTransfer(
      String sid,
      String nick,
      String entryId,
      String kind,
      String status,
      String detail,
      Integer progressPercent,
      DccTransferStore.ActionHint actionHint) {
    dccCommandSupport.upsertTransfer(
        sid, nick, entryId, kind, status, detail, progressPercent, actionHint);
  }

  private void upsertTransfer(
      String sid,
      String nick,
      String entryId,
      String kind,
      String status,
      String detail,
      String localPath,
      Integer progressPercent,
      DccTransferStore.ActionHint actionHint) {
    dccCommandSupport.upsertTransfer(
        sid, nick, entryId, kind, status, detail, localPath, progressPercent, actionHint);
  }

  private static String transferEntryId(String sid, String nick, String suffix) {
    return DccCommandSupport.transferEntryId(sid, nick, suffix);
  }

  private static void replaceListener(
      ConcurrentMap<String, ServerSocket> listeners, String key, ServerSocket next) {
    ServerSocket prev = listeners.put(key, next);
    closeQuietly(prev);
  }

  private static void removeListener(
      ConcurrentMap<String, ServerSocket> listeners, String key, ServerSocket socket) {
    listeners.remove(key, socket);
    closeQuietly(socket);
  }

  private static String encodeDccFileName(String name) {
    String n = sanitizeOfferFileName(name);
    if (n.indexOf(' ') >= 0) return "\"" + n.replace("\"", "_") + "\"";
    return n;
  }

  private static String sanitizeOfferFileName(String fileName) {
    return DccCommandSupport.sanitizeOfferFileName(fileName);
  }

  private static String normalizeToken(String raw) {
    return DccCommandSupport.normalizeToken(raw);
  }

  private static String normalizeNick(String raw) {
    return DccCommandSupport.normalizeNick(raw);
  }

  private static String peerKey(String sid, String nick) {
    return DccCommandSupport.peerKey(sid, nick);
  }

  private static InetAddress resolveAdvertisableIpv4() {
    InetAddress firstPrivate = null;
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces != null && interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        try {
          if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
        } catch (Exception ignored) {
          continue;
        }
        Enumeration<InetAddress> addresses = ni.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress addr = addresses.nextElement();
          if (!(addr instanceof Inet4Address)) continue;
          if (addr.isLoopbackAddress()) continue;
          if (!addr.isSiteLocalAddress()) return addr;
          if (firstPrivate == null) firstPrivate = addr;
        }
      }
      if (firstPrivate != null) return firstPrivate;
      InetAddress local = InetAddress.getLocalHost();
      if (local instanceof Inet4Address) return local;
    } catch (Exception ignored) {
    }
    return null;
  }

  private static long ipv4AsUnsignedLong(InetAddress address) {
    if (!(address instanceof Inet4Address)) return -1L;
    byte[] b = address.getAddress();
    if (b == null || b.length != 4) return -1L;
    return ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16) | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
  }

  private static Path expandPath(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.startsWith("~/")) {
      String home = System.getProperty("user.home", "");
      if (!home.isBlank()) {
        s = home + s.substring(1);
      }
    } else if ("~".equals(s)) {
      String home = System.getProperty("user.home", "");
      if (!home.isBlank()) {
        s = home;
      }
    }
    return Paths.get(s).toAbsolutePath().normalize();
  }

  private static Path resolveDownloadPath(String offeredFileName, String saveArg) {
    String arg = Objects.toString(saveArg, "").trim();
    String safeName = sanitizeOfferFileName(offeredFileName);
    if (arg.isEmpty()) {
      Path home = Paths.get(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
      return home.resolve("Downloads").resolve(safeName);
    }

    Path p = expandPath(arg);
    if (Files.exists(p) && Files.isDirectory(p)) {
      return p.resolve(safeName);
    }

    if (arg.endsWith("/") || arg.endsWith("\\")) {
      return p.resolve(safeName);
    }
    return p;
  }

  private static String formatBytes(long bytes) {
    return DccCommandSupport.formatBytes(bytes);
  }

  private static void closeQuietly(Closeable c) {
    if (c == null) return;
    try {
      c.close();
    } catch (Exception ignored) {
    }
  }

  private static void closeQuietly(Socket s) {
    if (s == null) return;
    try {
      s.close();
    } catch (Exception ignored) {
    }
  }

  @PreDestroy
  void shutdown() {
    dccChatSessionSupport.shutdown();

    for (ServerSocket listener : outgoingChatListeners.values()) closeQuietly(listener);
    for (ServerSocket listener : outgoingSendListeners.values()) closeQuietly(listener);
    outgoingChatListeners.clear();
    outgoingSendListeners.clear();

    pendingChatOffers.clear();
    pendingSendOffers.clear();
  }
}

package cafe.woden.ircclient.app.outbound.dcc;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.io.Closeable;
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
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Shared outgoing-offer, pending-acceptance, and DCC state-reporting support. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class DccOfferCommandSupport {

  private static final String DCC_TAG = "(dcc)";
  private static final String DCC_ERR_TAG = "(dcc-error)";
  private static final int OFFER_ACCEPT_TIMEOUT_MS = 120_000;
  private static final int CONNECT_TIMEOUT_MS = 20_000;
  private static final int IO_TIMEOUT_MS = 30_000;

  @NonNull private final UiPort ui;

  @Qualifier("ircMediatorInteractionPort")
  @NonNull
  private final IrcMediatorInteractionPort mediatorIrc;

  @NonNull private final ConnectionCoordinator connectionCoordinator;

  @Qualifier(ExecutorConfig.OUTBOUND_DCC_EXECUTOR)
  @NonNull
  private final ExecutorService io;

  @NonNull private final DccCommandSupport dccCommandSupport;
  @NonNull private final DccChatSessionSupport dccChatSessionSupport;
  @NonNull private final DccFileTransferIoSupport dccFileTransferIoSupport;
  @NonNull private final DccRuntimeRegistry dccRuntimeRegistry;

  void offerChat(CompositeDisposable disposables, String sid, TargetRef out, String nick) {
    String normalizedNick = DccCommandSupport.normalizeNick(nick);
    if (normalizedNick.isEmpty()) {
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

    String key = DccCommandSupport.peerKey(sid, normalizedNick);
    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, normalizedNick);

    try {
      ServerSocket listener = new ServerSocket();
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress(0));
      listener.setSoTimeout(OFFER_ACCEPT_TIMEOUT_MS);
      replaceListener(outgoingChatListeners(), key, listener);

      int port = listener.getLocalPort();
      String ctcp = "\u0001DCC CHAT chat " + ipAsLong + " " + port + "\u0001";
      ui.appendStatus(
          pm,
          DCC_TAG,
          "Offering DCC CHAT to "
              + normalizedNick
              + " on "
              + advertised.getHostAddress()
              + ":"
              + port);
      dccCommandSupport.upsertTransfer(
          sid,
          normalizedNick,
          DccCommandSupport.transferEntryId(sid, normalizedNick, "chat-out"),
          "Chat (outgoing)",
          "Offering",
          advertised.getHostAddress() + ":" + port,
          null,
          DccTransferStore.ActionHint.NONE);

      disposables.add(
          mediatorIrc
              .sendPrivateMessage(sid, normalizedNick, ctcp)
              .subscribe(
                  () -> {
                    ui.appendStatus(
                        pm, DCC_TAG, "Offer sent. Waiting for " + normalizedNick + " to connect…");
                    dccCommandSupport.upsertTransfer(
                        sid,
                        normalizedNick,
                        DccCommandSupport.transferEntryId(sid, normalizedNick, "chat-out"),
                        "Chat (outgoing)",
                        "Waiting for peer",
                        advertised.getHostAddress() + ":" + port,
                        null,
                        DccTransferStore.ActionHint.NONE);
                    io.execute(
                        () -> awaitOutgoingChatConnection(sid, normalizedNick, key, listener));
                  },
                  err -> {
                    removeListener(outgoingChatListeners(), key, listener);
                    ui.appendError(pm, DCC_ERR_TAG, String.valueOf(err));
                    dccCommandSupport.upsertTransfer(
                        sid,
                        normalizedNick,
                        DccCommandSupport.transferEntryId(sid, normalizedNick, "chat-out"),
                        "Chat (outgoing)",
                        "Failed to send offer",
                        String.valueOf(err),
                        null,
                        DccTransferStore.ActionHint.NONE);
                  }));
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "Could not start DCC chat listener: " + e.getMessage());
      dccCommandSupport.upsertTransfer(
          sid,
          normalizedNick,
          DccCommandSupport.transferEntryId(sid, normalizedNick, "chat-out"),
          "Chat (outgoing)",
          "Failed to listen",
          e.getMessage(),
          null,
          DccTransferStore.ActionHint.NONE);
    }
  }

  void offerSend(
      CompositeDisposable disposables, String sid, TargetRef out, String nick, String filePathArg) {
    String normalizedNick = DccCommandSupport.normalizeNick(nick);
    if (normalizedNick.isEmpty()) {
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

    String key = DccCommandSupport.peerKey(sid, normalizedNick);
    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, normalizedNick);
    String fileName =
        DccCommandSupport.sanitizeOfferFileName(
            source.getFileName() == null ? "file.bin" : source.getFileName().toString());
    String fileToken = encodeDccFileName(fileName);

    try {
      ServerSocket listener = new ServerSocket();
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress(0));
      listener.setSoTimeout(OFFER_ACCEPT_TIMEOUT_MS);
      replaceListener(outgoingSendListeners(), key, listener);

      int port = listener.getLocalPort();
      String ctcp =
          "\u0001DCC SEND " + fileToken + " " + ipAsLong + " " + port + " " + size + "\u0001";
      ui.appendStatus(
          pm,
          DCC_TAG,
          "Offering file "
              + fileName
              + " ("
              + DccCommandSupport.formatBytes(size)
              + ") to "
              + normalizedNick);
      dccCommandSupport.upsertTransfer(
          sid,
          normalizedNick,
          DccCommandSupport.transferEntryId(sid, normalizedNick, "send-out"),
          "Send file (outgoing)",
          "Offering",
          fileName + " (" + DccCommandSupport.formatBytes(size) + ")",
          0,
          DccTransferStore.ActionHint.NONE);

      disposables.add(
          mediatorIrc
              .sendPrivateMessage(sid, normalizedNick, ctcp)
              .subscribe(
                  () -> {
                    ui.appendStatus(
                        pm, DCC_TAG, "Offer sent. Waiting for " + normalizedNick + " to connect…");
                    dccCommandSupport.upsertTransfer(
                        sid,
                        normalizedNick,
                        DccCommandSupport.transferEntryId(sid, normalizedNick, "send-out"),
                        "Send file (outgoing)",
                        "Waiting for peer",
                        fileName + " (" + DccCommandSupport.formatBytes(size) + ")",
                        0,
                        DccTransferStore.ActionHint.NONE);
                    io.execute(
                        () ->
                            awaitOutgoingSendConnection(
                                sid, normalizedNick, key, listener, source, fileName, size));
                  },
                  err -> {
                    removeListener(outgoingSendListeners(), key, listener);
                    ui.appendError(pm, DCC_ERR_TAG, String.valueOf(err));
                    dccCommandSupport.upsertTransfer(
                        sid,
                        normalizedNick,
                        DccCommandSupport.transferEntryId(sid, normalizedNick, "send-out"),
                        "Send file (outgoing)",
                        "Failed to send offer",
                        String.valueOf(err),
                        0,
                        DccTransferStore.ActionHint.NONE);
                  }));
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "Could not start DCC send listener: " + e.getMessage());
      dccCommandSupport.upsertTransfer(
          sid,
          normalizedNick,
          DccCommandSupport.transferEntryId(sid, normalizedNick, "send-out"),
          "Send file (outgoing)",
          "Failed to listen",
          e.getMessage(),
          0,
          DccTransferStore.ActionHint.NONE);
    }
  }

  void acceptChatOffer(String sid, TargetRef out, String nick) {
    String normalizedNick = DccCommandSupport.normalizeNick(nick);
    if (normalizedNick.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc accept <nick>");
      return;
    }

    String key = DccCommandSupport.peerKey(sid, normalizedNick);
    PendingChatOffer offer = pendingChatOffers().remove(key);
    if (offer == null) {
      ui.appendStatus(out, DCC_TAG, "No pending DCC CHAT offer from " + normalizedNick + ".");
      return;
    }

    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, normalizedNick);
    ui.appendStatus(
        pm,
        DCC_TAG,
        "Connecting to DCC CHAT " + offer.host().getHostAddress() + ":" + offer.port() + " …");
    dccCommandSupport.upsertTransfer(
        sid,
        normalizedNick,
        DccCommandSupport.transferEntryId(sid, normalizedNick, "chat-in"),
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
            dccChatSessionSupport.startChatSession(
                sid, normalizedNick, socket, "connected (incoming)");
          } catch (Exception e) {
            closeQuietly(socket);
            ui.appendError(pm, DCC_ERR_TAG, "DCC CHAT connect failed: " + e.getMessage());
            dccCommandSupport.upsertTransfer(
                sid,
                normalizedNick,
                DccCommandSupport.transferEntryId(sid, normalizedNick, "chat-in"),
                "Chat (incoming)",
                "Failed",
                e.getMessage(),
                null,
                DccTransferStore.ActionHint.NONE);
          }
        });
  }

  void acceptSendOffer(String sid, TargetRef out, String nick, String saveArg) {
    String normalizedNick = DccCommandSupport.normalizeNick(nick);
    if (normalizedNick.isEmpty()) {
      ui.appendStatus(out, DCC_TAG, "Usage: /dcc get <nick> [save-path]");
      return;
    }

    String key = DccCommandSupport.peerKey(sid, normalizedNick);
    PendingSendOffer offer = pendingSendOffers().get(key);
    if (offer == null) {
      ui.appendStatus(out, DCC_TAG, "No pending DCC SEND offer from " + normalizedNick + ".");
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

    pendingSendOffers().remove(key, offer);
    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, normalizedNick);
    ui.appendStatus(pm, DCC_TAG, "Receiving " + offer.fileName() + " to " + destination + " …");
    String localPath = destination.toAbsolutePath().normalize().toString();
    dccCommandSupport.upsertTransfer(
        sid,
        normalizedNick,
        DccCommandSupport.transferEntryId(sid, normalizedNick, "send-in"),
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
            dccFileTransferIoSupport.receiveFileFromOffer(
                sid, normalizedNick, pm, offer, destination, localPath);
            completed = true;
            ui.appendStatus(
                pm,
                DCC_TAG,
                "DCC GET complete: "
                    + destination.getFileName()
                    + " ("
                    + DccCommandSupport.formatBytes(offer.size())
                    + ")");
            dccCommandSupport.upsertTransfer(
                sid,
                normalizedNick,
                DccCommandSupport.transferEntryId(sid, normalizedNick, "send-in"),
                "Receive file (incoming)",
                "Completed",
                destination.getFileName()
                    + " ("
                    + DccCommandSupport.formatBytes(offer.size())
                    + ")",
                localPath,
                100,
                DccTransferStore.ActionHint.NONE);
          } catch (Exception e) {
            ui.appendError(pm, DCC_ERR_TAG, "DCC GET failed: " + e.getMessage());
            dccCommandSupport.upsertTransfer(
                sid,
                normalizedNick,
                DccCommandSupport.transferEntryId(sid, normalizedNick, "send-in"),
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
              pendingSendOffers().putIfAbsent(key, offer);
              dccCommandSupport.upsertTransfer(
                  sid,
                  normalizedNick,
                  DccCommandSupport.transferEntryId(sid, normalizedNick, "send-in"),
                  "Receive file (incoming)",
                  "Offer pending",
                  offer.fileName() + " (" + DccCommandSupport.formatBytes(offer.size()) + ")",
                  localPath,
                  0,
                  DccTransferStore.ActionHint.GET_FILE);
            }
          }
        });
  }

  void listDccState(String sid, TargetRef out) {
    int activeChats = 0;
    for (DccChatSession session : chatSessions().values()) {
      if (sid.equals(session.serverId())) activeChats++;
    }

    int pendingChats = 0;
    for (PendingChatOffer offer : pendingChatOffers().values()) {
      if (sid.equals(offer.serverId())) pendingChats++;
    }

    int pendingSends = 0;
    for (PendingSendOffer offer : pendingSendOffers().values()) {
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

    for (PendingChatOffer offer : pendingChatOffers().values()) {
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

    for (PendingSendOffer offer : pendingSendOffers().values()) {
      if (!sid.equals(offer.serverId())) continue;
      ui.appendStatus(
          out,
          DCC_TAG,
          "Pending SEND from "
              + offer.fromNick()
              + ": "
              + offer.fileName()
              + " ("
              + DccCommandSupport.formatBytes(offer.size())
              + ")");
    }

    for (DccChatSession session : chatSessions().values()) {
      if (!sid.equals(session.serverId())) continue;
      ui.appendStatus(out, DCC_TAG, "Active CHAT with " + session.nick());
    }
  }

  void shutdown() {
    for (ServerSocket listener : outgoingChatListeners().values()) {
      closeQuietly(listener);
    }
    for (ServerSocket listener : outgoingSendListeners().values()) {
      closeQuietly(listener);
    }
    outgoingChatListeners().clear();
    outgoingSendListeners().clear();
    pendingChatOffers().clear();
    pendingSendOffers().clear();
  }

  private void awaitOutgoingChatConnection(
      String sid, String nick, String key, ServerSocket listener) {
    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, nick);
    try (ServerSocket ignored = listener) {
      Socket socket = listener.accept();
      dccChatSessionSupport.startChatSession(sid, nick, socket, "connected (outgoing)");
    } catch (SocketTimeoutException e) {
      ui.appendStatus(pm, DCC_TAG, "DCC CHAT offer to " + nick + " timed out.");
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "chat-out"),
          "Chat (outgoing)",
          "Timed out",
          "",
          null,
          DccTransferStore.ActionHint.NONE);
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "DCC CHAT accept failed: " + e.getMessage());
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "chat-out"),
          "Chat (outgoing)",
          "Failed",
          e.getMessage(),
          null,
          DccTransferStore.ActionHint.NONE);
    } finally {
      removeListener(outgoingChatListeners(), key, listener);
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
    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, nick);
    try (ServerSocket ignored = listener;
        Socket socket = listener.accept()) {
      socket.setSoTimeout(IO_TIMEOUT_MS);
      ui.appendStatus(pm, DCC_TAG, nick + " connected. Sending " + displayName + "…");
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Transferring",
          displayName + " (" + DccCommandSupport.formatBytes(size) + ")",
          0,
          DccTransferStore.ActionHint.NONE);
      dccFileTransferIoSupport.sendFileToSocket(sid, nick, pm, socket, source, displayName, size);
      ui.appendStatus(
          pm,
          DCC_TAG,
          "DCC SEND complete: " + displayName + " (" + DccCommandSupport.formatBytes(size) + ")");
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Completed",
          displayName + " (" + DccCommandSupport.formatBytes(size) + ")",
          100,
          DccTransferStore.ActionHint.NONE);
    } catch (SocketTimeoutException e) {
      ui.appendStatus(pm, DCC_TAG, "DCC SEND offer to " + nick + " timed out.");
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Timed out",
          displayName + " (" + DccCommandSupport.formatBytes(size) + ")",
          null,
          DccTransferStore.ActionHint.NONE);
    } catch (Exception e) {
      ui.appendError(pm, DCC_ERR_TAG, "DCC SEND failed: " + e.getMessage());
      dccCommandSupport.upsertTransfer(
          sid,
          nick,
          DccCommandSupport.transferEntryId(sid, nick, "send-out"),
          "Send file (outgoing)",
          "Failed",
          e.getMessage(),
          null,
          DccTransferStore.ActionHint.NONE);
    } finally {
      removeListener(outgoingSendListeners(), key, listener);
    }
  }

  private static void replaceListener(
      ConcurrentMap<String, ServerSocket> listeners, String key, ServerSocket next) {
    ServerSocket previous = listeners.put(key, next);
    closeQuietly(previous);
  }

  private static void removeListener(
      ConcurrentMap<String, ServerSocket> listeners, String key, ServerSocket socket) {
    listeners.remove(key, socket);
    closeQuietly(socket);
  }

  private static String encodeDccFileName(String name) {
    String normalizedName = DccCommandSupport.sanitizeOfferFileName(name);
    if (normalizedName.indexOf(' ') >= 0) {
      return "\"" + normalizedName.replace("\"", "_") + "\"";
    }
    return normalizedName;
  }

  private static InetAddress resolveAdvertisableIpv4() {
    InetAddress firstPrivate = null;
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces != null && interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
        try {
          if (!networkInterface.isUp()
              || networkInterface.isLoopback()
              || networkInterface.isVirtual()) {
            continue;
          }
        } catch (Exception ignored) {
          continue;
        }
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (!(address instanceof Inet4Address)) continue;
          if (address.isLoopbackAddress()) continue;
          if (!address.isSiteLocalAddress()) return address;
          if (firstPrivate == null) firstPrivate = address;
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
    byte[] bytes = address.getAddress();
    if (bytes == null || bytes.length != 4) return -1L;
    return ((bytes[0] & 0xFFL) << 24)
        | ((bytes[1] & 0xFFL) << 16)
        | ((bytes[2] & 0xFFL) << 8)
        | (bytes[3] & 0xFFL);
  }

  private static Path expandPath(String raw) {
    String path = Objects.toString(raw, "").trim();
    if (path.startsWith("~/")) {
      String home = System.getProperty("user.home", "");
      if (!home.isBlank()) {
        path = home + path.substring(1);
      }
    } else if ("~".equals(path)) {
      String home = System.getProperty("user.home", "");
      if (!home.isBlank()) {
        path = home;
      }
    }
    return Paths.get(path).toAbsolutePath().normalize();
  }

  private static Path resolveDownloadPath(String offeredFileName, String saveArg) {
    String arg = Objects.toString(saveArg, "").trim();
    String safeName = DccCommandSupport.sanitizeOfferFileName(offeredFileName);
    if (arg.isEmpty()) {
      Path home = Paths.get(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
      return home.resolve("Downloads").resolve(safeName);
    }

    Path path = expandPath(arg);
    if (Files.exists(path) && Files.isDirectory(path)) {
      return path.resolve(safeName);
    }

    if (arg.endsWith("/") || arg.endsWith("\\")) {
      return path.resolve(safeName);
    }
    return path;
  }

  private static void closeQuietly(Closeable closeable) {
    if (closeable == null) return;
    try {
      closeable.close();
    } catch (Exception ignored) {
    }
  }

  private static void closeQuietly(Socket socket) {
    if (socket == null) return;
    try {
      socket.close();
    } catch (Exception ignored) {
    }
  }

  private ConcurrentMap<String, PendingChatOffer> pendingChatOffers() {
    return dccRuntimeRegistry.pendingChatOffers();
  }

  private ConcurrentMap<String, PendingSendOffer> pendingSendOffers() {
    return dccRuntimeRegistry.pendingSendOffers();
  }

  private ConcurrentMap<String, DccChatSession> chatSessions() {
    return dccRuntimeRegistry.chatSessions();
  }

  private ConcurrentMap<String, ServerSocket> outgoingChatListeners() {
    return dccRuntimeRegistry.outgoingChatListeners();
  }

  private ConcurrentMap<String, ServerSocket> outgoingSendListeners() {
    return dccRuntimeRegistry.outgoingSendListeners();
  }
}

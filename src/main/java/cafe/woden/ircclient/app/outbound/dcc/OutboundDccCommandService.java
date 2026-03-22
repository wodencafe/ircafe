package cafe.woden.ircclient.app.outbound.dcc;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.net.ServerSocket;
import java.time.Instant;
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
  private static final String DCC_TAG = "(dcc)";

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final DccChatSessionSupport dccChatSessionSupport;
  private final DccInboundOfferSupport dccInboundOfferSupport;
  private final DccOfferCommandSupport dccOfferCommandSupport;

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
      @Qualifier(ExecutorConfig.OUTBOUND_DCC_EXECUTOR) ExecutorService io) {
    this.ui = ui;
    this.targetCoordinator = targetCoordinator;
    DccCommandSupport dccCommandSupport =
        new DccCommandSupport(ui, targetCoordinator, dccTransferStore);
    this.dccChatSessionSupport =
        new DccChatSessionSupport(ui, mediatorIrc, io, dccCommandSupport, chatSessions);
    DccFileTransferIoSupport dccFileTransferIoSupport =
        new DccFileTransferIoSupport(ui, dccCommandSupport, 20_000, 30_000, 64 * 1024);
    this.dccInboundOfferSupport =
        new DccInboundOfferSupport(dccCommandSupport, pendingChatOffers, pendingSendOffers);
    this.dccOfferCommandSupport =
        new DccOfferCommandSupport(
            ui,
            mediatorIrc,
            connectionCoordinator,
            io,
            dccCommandSupport,
            dccChatSessionSupport,
            dccFileTransferIoSupport,
            pendingChatOffers,
            pendingSendOffers,
            chatSessions,
            outgoingChatListeners,
            outgoingSendListeners,
            120_000,
            20_000,
            30_000);
  }

  public void handleDcc(
      CompositeDisposable disposables, String subcommand, String nick, String argument) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef out = (at != null) ? at : targetCoordinator.safeStatusTarget();
    String sid = (at == null) ? "" : DccCommandSupport.normalizeToken(at.serverId());
    String sub = DccCommandSupport.normalizeToken(subcommand).toLowerCase(Locale.ROOT);
    String n = DccCommandSupport.normalizeToken(nick);
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
      case "chat" -> dccOfferCommandSupport.offerChat(disposables, sid, out, n);
      case "send" -> dccOfferCommandSupport.offerSend(disposables, sid, out, n, arg);
      case "accept" -> dccOfferCommandSupport.acceptChatOffer(sid, out, n);
      case "get", "recv", "receive" -> dccOfferCommandSupport.acceptSendOffer(sid, out, n, arg);
      case "msg" -> sendChatMessage(sid, out, n, arg);
      case "close" -> closeChatSessionByCommand(sid, out, n);
      case "list" -> dccOfferCommandSupport.listDccState(sid, out);
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

  private void sendChatMessage(String sid, TargetRef out, String nick, String text) {
    String n = DccCommandSupport.normalizeNick(nick);
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
    String n = DccCommandSupport.normalizeNick(nick);
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

  private void openDccTransfersPanel(String sid) {
    String serverId = DccCommandSupport.normalizeToken(sid);
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

  @PreDestroy
  void shutdown() {
    dccChatSessionSupport.shutdown();
    dccOfferCommandSupport.shutdown();
  }
}

package cafe.woden.ircclient.app.outbound.dcc;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
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
@RequiredArgsConstructor
public class OutboundDccCommandService {
  private static final String DCC_TAG = "(dcc)";

  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final DccChatSessionSupport dccChatSessionSupport;
  @NonNull private final DccInboundOfferSupport dccInboundOfferSupport;
  @NonNull private final DccOfferCommandSupport dccOfferCommandSupport;

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

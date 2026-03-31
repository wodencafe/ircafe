package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistrar;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistry;
import cafe.woden.ircclient.app.outbound.messaging.OutboundSayQuoteCommandService;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registers fallback handling for unknown slash commands. */
@Component
@ApplicationLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class UnknownOutboundCommandRegistrar implements OutboundCommandRegistrar {

  @NonNull private final UserCommandAliasesBus userCommandAliasesBus;
  @NonNull private final OutboundSayQuoteCommandService outboundSayQuoteCommandService;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final UiPort ui;

  @Override
  public void registerCommands(OutboundCommandRegistry registry) {
    registry.register(ParsedInput.Unknown.class, this::handleUnknown);
  }

  private void handleUnknown(
      io.reactivex.rxjava3.disposables.CompositeDisposable disposables, ParsedInput.Unknown cmd) {
    if (userCommandAliasesBus.unknownCommandAsRawEnabled()) {
      String rawLine = unknownCommandAsRawLine(cmd.raw());
      if (!rawLine.isBlank()) {
        outboundSayQuoteCommandService.handleQuote(disposables, rawLine);
        return;
      }
    }
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef tgt = (at != null) ? at : targetCoordinator.safeStatusTarget();
    ui.appendStatus(tgt, "(system)", "Unknown command: " + cmd.raw());
  }

  private static String unknownCommandAsRawLine(String rawUnknown) {
    String line = Objects.toString(rawUnknown, "").trim();
    if (line.startsWith("/")) line = line.substring(1);
    return line.trim();
  }
}

package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Clears a target's transcript and reloads the recent history slice so features like
 * filtering/placeholder folding can be applied to already-existing messages.
 */
@Component
@Lazy
public class TranscriptRebuildService {

  private static final Logger log = LoggerFactory.getLogger(TranscriptRebuildService.class);

  private final UiPort ui;
  private final ChatHistoryService history;

  public TranscriptRebuildService(UiPort ui, ChatHistoryService history) {
    this.ui = Objects.requireNonNull(ui, "ui");
    this.history = history;
  }

  /**
   * Rebuilds the transcript for a specific target.
   *
   * @return true if a history reload was kicked off, false if rebuild was skipped.
   */
  public boolean rebuild(TargetRef target) {
    if (target == null || target.isUiOnly()) return false;

    boolean canReload = true;
    try {
      if (history != null) {
        canReload = history.canReloadRecent(target);
      }
    } catch (Exception ignored) {
      canReload = true;
    }

    // If we can’t reload history, clearing would be destructive; skip.
    if (!canReload) return false;

    // These rebuilds are rare and can look like "the UI randomly changed" (because we clear the
    // visible transcript and replay recent history). Keep a breadcrumb at INFO, and make the
    // call stack available at DEBUG for troubleshooting.
    log.info("[rebuild] rebuilding transcript for {}", target);
    if (log.isDebugEnabled()) {
      log.debug("[rebuild] call stack", new RuntimeException("rebuild requested"));
    }

    try {
      if (history != null) {
        history.reset(target);
      }
    } catch (Exception e) {
      log.debug("History reset failed during rebuild for {}", target, e);
    }

    try {
      ui.clearTranscript(target);
    } catch (Exception e) {
      log.debug("UI transcript clear failed during rebuild for {}", target, e);
    }

    try {
      if (history != null) {
        history.reloadRecent(target);
      }
    } catch (Exception e) {
      log.debug("History reloadRecent failed during rebuild for {}", target, e);
    }

    return true;
  }
}

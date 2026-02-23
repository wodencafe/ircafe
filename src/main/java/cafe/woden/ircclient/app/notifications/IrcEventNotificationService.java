package cafe.woden.ircclient.app.notifications;

import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;

/**
 * Evaluates configured IRC-event notification rules and dispatches matching notification actions.
 */
@Component
@Lazy
public class IrcEventNotificationService {

  private static final Logger log = LoggerFactory.getLogger(IrcEventNotificationService.class);
  private static final long SCRIPT_TIMEOUT_SECONDS = 8L;

  private final IrcEventNotificationRulesBus rulesBus;
  private final TrayNotificationService trayNotificationService;
  private final NotificationStore notificationStore;
  private final PushyNotificationService pushyNotificationService;
  private final ExecutorService scriptExecutor;

  public IrcEventNotificationService(
      IrcEventNotificationRulesBus rulesBus,
      TrayNotificationService trayNotificationService,
      NotificationStore notificationStore,
      PushyNotificationService pushyNotificationService,
      @Qualifier(ExecutorConfig.IRC_EVENT_SCRIPT_EXECUTOR) ExecutorService scriptExecutor
  ) {
    this.rulesBus = rulesBus;
    this.trayNotificationService = trayNotificationService;
    this.notificationStore = notificationStore;
    this.pushyNotificationService = pushyNotificationService;
    this.scriptExecutor = scriptExecutor;
  }

  /**
   * Returns true if at least one rule matched and actions were evaluated.
   */
  public boolean notifyConfigured(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body,
      String activeServerId,
      String activeTarget
  ) {
    if (eventType == null) return false;

    List<IrcEventNotificationRule> rules = rulesBus != null ? rulesBus.get() : List.of();
    if (rules == null || rules.isEmpty()) return false;

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;

    String target = Objects.toString(channel, "").trim();
    if (target.isEmpty()) target = "status";

    String source = Objects.toString(sourceNick, "").trim();
    if (source.isEmpty()) source = "server";

    String t = Objects.toString(title, "").trim();
    if (t.isEmpty()) t = eventType.toString();

    String b = Objects.toString(body, "").trim();
    String activeSid = Objects.toString(activeServerId, "").trim();
    String activeTgt = Objects.toString(activeTarget, "").trim();
    boolean activeSameServer = !activeSid.isEmpty() && sid.equalsIgnoreCase(activeSid);
    boolean anyMatched = false;

    for (IrcEventNotificationRule matched : rules) {
      if (matched == null) continue;
      if (!matched.matches(eventType, sourceNick, sourceIsSelf, channel, activeSameServer, activeTgt)) continue;
      anyMatched = true;
      dispatchMatchedRule(matched, eventType, sid, target, source, sourceIsSelf, t, b);
    }

    return anyMatched;
  }

  private void dispatchMatchedRule(
      IrcEventNotificationRule matched,
      IrcEventNotificationRule.EventType eventType,
      String sid,
      String target,
      String source,
      Boolean sourceIsSelf,
      String title,
      String body
  ) {
    if (matched == null) return;

    if (matched.notificationsNodeEnabled() && notificationStore != null) {
      notificationStore.recordIrcEvent(sid, target, source, title, body);
    }

    boolean showToast = matched.toastEnabled();
    boolean showStatusBar = matched.statusBarEnabled();
    boolean playSound = matched.soundEnabled();
    if ((showToast || showStatusBar || playSound) && trayNotificationService != null) {
      trayNotificationService.notifyCustom(
          sid,
          target,
          title,
          body,
          showToast,
          showStatusBar,
          matched.focusScope(),
          playSound,
          matched.soundId(),
          matched.soundUseCustom(),
          matched.soundCustomPath());
    }

    if (matched.scriptEnabled()) {
      dispatchScript(matched, eventType, sid, target, source, sourceIsSelf, title, body);
    }

    if (pushyNotificationService != null) {
      try {
        pushyNotificationService.notifyEvent(eventType, sid, target, source, sourceIsSelf, title, body);
      } catch (Exception ignored) {
      }
    }
  }

  public boolean hasEnabledRuleFor(IrcEventNotificationRule.EventType eventType) {
    if (eventType == null) return false;
    List<IrcEventNotificationRule> rules = rulesBus != null ? rulesBus.get() : List.of();
    if (rules == null || rules.isEmpty()) return false;
    for (IrcEventNotificationRule r : rules) {
      if (r == null) continue;
      if (!r.enabled()) continue;
      if (r.eventType() == eventType) return true;
    }
    return false;
  }

  private void dispatchScript(
      IrcEventNotificationRule rule,
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body
  ) {
    String script = Objects.toString(rule != null ? rule.scriptPath() : "", "").trim();
    if (script.isEmpty()) return;

    String scriptArgs = Objects.toString(rule != null ? rule.scriptArgs() : "", "").trim();
    if (scriptArgs.isEmpty()) scriptArgs = null;
    String scriptWorkingDirectory = Objects.toString(rule != null ? rule.scriptWorkingDirectory() : "", "").trim();
    if (scriptWorkingDirectory.isEmpty()) scriptWorkingDirectory = null;

    String args = scriptArgs;
    String cwd = scriptWorkingDirectory;
    scriptExecutor.execute(() -> runScript(script, args, cwd, eventType, serverId, channel, sourceNick, sourceIsSelf, title, body));
  }

  private void runScript(
      String scriptPath,
      String scriptArgs,
      String scriptWorkingDirectory,
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body
  ) {
    try {
      List<String> command = new ArrayList<>();
      command.add(scriptPath);
      command.addAll(parseCommandArgs(scriptArgs));

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectInput(ProcessBuilder.Redirect.PIPE);
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      pb.redirectError(ProcessBuilder.Redirect.DISCARD);

      if (scriptWorkingDirectory != null && !scriptWorkingDirectory.isBlank()) {
        File cwd = new File(scriptWorkingDirectory);
        if (!cwd.isDirectory()) {
          log.warn("[ircafe] Event notification script working directory does not exist: {}", scriptWorkingDirectory);
          return;
        }
        pb.directory(cwd);
      }

      java.util.Map<String, String> env = pb.environment();
      putEnv(env, "IRCAFE_EVENT_TYPE", eventType != null ? eventType.name() : "");
      putEnv(env, "IRCAFE_SERVER_ID", serverId);
      putEnv(env, "IRCAFE_CHANNEL", channel);
      putEnv(env, "IRCAFE_SOURCE_NICK", sourceNick);
      putEnv(env, "IRCAFE_SOURCE_IS_SELF", sourceIsSelf == null ? "unknown" : Boolean.toString(sourceIsSelf));
      putEnv(env, "IRCAFE_TITLE", title);
      putEnv(env, "IRCAFE_BODY", body);
      putEnv(env, "IRCAFE_TIMESTAMP_MS", Long.toString(System.currentTimeMillis()));

      Process p = pb.start();
      boolean exited = p.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!exited) {
        p.destroyForcibly();
        log.warn("[ircafe] Event notification script timed out after {}s: {}", SCRIPT_TIMEOUT_SECONDS, scriptPath);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] Could not run event notification script: {}", scriptPath, ex);
    }
  }

  /**
   * Shell-like tokenizer for script argument strings.
   *
   * <p>Supports single/double quotes and backslash escaping without shell expansion.
   */
  private static List<String> parseCommandArgs(String rawArgs) {
    String input = Objects.toString(rawArgs, "").trim();
    if (input.isEmpty()) return List.of();

    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);

      if (escaping) {
        current.append(c);
        escaping = false;
        continue;
      }

      if (c == '\\') {
        escaping = true;
        continue;
      }

      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }

      if (c == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }

      if (Character.isWhitespace(c) && !inSingle && !inDouble) {
        appendToken(out, current);
        continue;
      }

      current.append(c);
    }

    if (escaping) current.append('\\');
    if (inSingle || inDouble) {
      throw new IllegalArgumentException("Unterminated quoted script arguments.");
    }
    appendToken(out, current);
    return out;
  }

  private static void appendToken(List<String> out, StringBuilder current) {
    if (current == null || current.isEmpty()) return;
    out.add(current.toString());
    current.setLength(0);
  }

  private static void putEnv(java.util.Map<String, String> env, String key, String value) {
    if (env == null || key == null || key.isBlank()) return;
    env.put(key, Objects.toString(value, ""));
  }
}

package cafe.woden.ircclient.app.notifications;

import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import cafe.woden.ircclient.model.NotificationRule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Matches inbound messages against user-configured notification rules.
 *
 * <p>This is intentionally a pure matcher: it does not decide whether a match should generate a
 * notification (that logic lives in the mediator).
 */
@Component
@Lazy
public class NotificationRuleMatcher {

  private static final Logger log = LoggerFactory.getLogger(NotificationRuleMatcher.class);

  private final UiSettingsPort uiSettingsPort;
  private final PropertyChangeListener settingsListener = this::onSettingsChanged;

  private volatile Compiled compiled;

  public NotificationRuleMatcher(UiSettingsPort uiSettingsPort) {
    this.uiSettingsPort = Objects.requireNonNull(uiSettingsPort, "uiSettingsPort");
    this.compiled = compile(uiSettingsPort.get());
  }

  @PostConstruct
  public void start() {
    uiSettingsPort.addListener(settingsListener);
  }

  @PreDestroy
  public void stop() {
    uiSettingsPort.removeListener(settingsListener);
  }

  /** Returns all rule matches for the given message. At most one match is returned per rule. */
  public List<NotificationRuleMatch> matchAll(String message) {
    if (message == null || message.isBlank()) return List.of();

    Compiled snap = compiled;
    if (snap.rules.isEmpty()) return List.of();

    List<NotificationRuleMatch> out = new ArrayList<>();

    // Tokenize once for whole-word matches.
    List<Token> tokens = snap.hasWholeWordRules ? tokenize(message) : List.of();

    String messageLower = null;
    if (snap.needsMessageLower) {
      messageLower = message.toLowerCase(Locale.ROOT);
    }

    for (CompiledRule r : snap.rules) {
      NotificationRule rule = r.rule;
      if (!rule.enabled()) continue;

      if (rule.type() == NotificationRule.Type.REGEX) {
        Pattern p = r.regex;
        if (p == null) continue; // invalid regex skipped during compile
        Matcher m = p.matcher(message);
        if (m.find()) {
          out.add(
              new NotificationRuleMatch(
                  rule.label(), m.group(), m.start(), m.end(), rule.highlightFg()));
        }
        continue;
      }

      // WORD
      String pat = rule.pattern();
      if (pat.isEmpty()) continue;

      if (rule.wholeWord()) {
        int plen = pat.length();
        for (Token tok : tokens) {
          int tlen = tok.end - tok.start;
          if (tlen != plen) continue;

          boolean ok =
              rule.caseSensitive()
                  ? message.regionMatches(false, tok.start, pat, 0, plen)
                  : message.regionMatches(true, tok.start, pat, 0, plen);

          if (ok) {
            out.add(
                new NotificationRuleMatch(
                    rule.label(),
                    message.substring(tok.start, tok.end),
                    tok.start,
                    tok.end,
                    rule.highlightFg()));
            break;
          }
        }
      } else {
        int idx;
        if (rule.caseSensitive()) {
          idx = message.indexOf(pat);
        } else {
          String patLower = r.wordLower;
          if (patLower == null) patLower = pat.toLowerCase(Locale.ROOT);
          idx =
              (messageLower != null ? messageLower : message.toLowerCase(Locale.ROOT))
                  .indexOf(patLower);
        }

        if (idx >= 0) {
          out.add(
              new NotificationRuleMatch(
                  rule.label(),
                  message.substring(idx, idx + pat.length()),
                  idx,
                  idx + pat.length(),
                  rule.highlightFg()));
        }
      }
    }

    return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
  }

  private void onSettingsChanged(PropertyChangeEvent ev) {
    try {
      this.compiled = compile(uiSettingsPort.get());
    } catch (Exception e) {
      // Don't let a bad rule list take down the app; keep last known good.
      log.warn("Failed to refresh notification rule matcher; keeping previous compiled rules.", e);
    }
  }

  private static Compiled compile(UiSettingsSnapshot settings) {
    List<NotificationRule> rules = settings != null ? settings.notificationRules() : List.of();
    if (rules == null || rules.isEmpty()) return new Compiled(List.of(), false, false);

    List<CompiledRule> compiled = new ArrayList<>(rules.size());
    boolean needsLower = false;
    boolean hasWholeWord = false;

    for (NotificationRule r : rules) {
      if (r == null) continue;
      if (!r.enabled()) continue;
      if (r.pattern().isEmpty()) continue;

      if (r.type() == NotificationRule.Type.REGEX) {
        Pattern p = null;
        try {
          int flags = Pattern.UNICODE_CASE;
          if (!r.caseSensitive()) flags |= Pattern.CASE_INSENSITIVE;
          p = Pattern.compile(r.pattern(), flags);
        } catch (Exception ex) {
          log.warn("Invalid notification REGEX rule '{}': {}", r.label(), ex.getMessage());
        }
        compiled.add(CompiledRule.forRegex(r, p));
      } else {
        if (!r.caseSensitive() && !r.wholeWord()) needsLower = true;
        if (r.wholeWord()) hasWholeWord = true;
        String lower =
            (!r.caseSensitive() && !r.wholeWord()) ? r.pattern().toLowerCase(Locale.ROOT) : null;
        compiled.add(CompiledRule.forWord(r, lower));
      }
    }

    return new Compiled(Collections.unmodifiableList(compiled), needsLower, hasWholeWord);
  }

  private static List<Token> tokenize(String message) {
    int len = message.length();
    if (len == 0) return List.of();

    List<Token> toks = new ArrayList<>();
    int i = 0;

    while (i < len) {
      while (i < len && !isWordChar(message.charAt(i))) i++;
      if (i >= len) break;
      int start = i;
      while (i < len && isWordChar(message.charAt(i))) i++;
      int end = i;
      toks.add(new Token(start, end));
    }

    return toks;
  }

  /**
   * "Word" tokenization for notification rules.
   *
   * <p>We intentionally keep this tighter than IRC nick chars; for more complex matching, users can
   * switch to REGEX rules.
   */
  private static boolean isWordChar(char ch) {
    if (ch >= '0' && ch <= '9') return true;
    if (ch >= 'A' && ch <= 'Z') return true;
    if (ch >= 'a' && ch <= 'z') return true;
    return ch == '_' || ch == '-';
  }

  private record Token(int start, int end) {}

  private record Compiled(
      List<CompiledRule> rules, boolean needsMessageLower, boolean hasWholeWordRules) {}

  private static final class CompiledRule {
    private final NotificationRule rule;
    private final Pattern regex;
    private final String wordLower;

    private CompiledRule(NotificationRule rule, Pattern regex, String wordLower) {
      this.rule = rule;
      this.regex = regex;
      this.wordLower = wordLower;
    }

    static CompiledRule forRegex(NotificationRule rule, Pattern regex) {
      return new CompiledRule(rule, regex, null);
    }

    static CompiledRule forWord(NotificationRule rule, String wordLower) {
      return new CompiledRule(rule, null, wordLower);
    }
  }
}

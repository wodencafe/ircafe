package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.NotificationRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NotificationRuleTestReportSupport {
  private NotificationRuleTestReportSupport() {}

  static String buildRuleTestReport(
      List<NotificationRule> rules, List<ValidationError> errors, String sample) {
    String msg = sample != null ? sample : "";
    msg = msg.trim();
    if (msg.isEmpty()) {
      return "Type a sample message above, then click Test.";
    }

    StringBuilder out = new StringBuilder();

    if (errors != null && !errors.isEmpty()) {
      out.append("Invalid REGEX rules (ignored):\n");
      int shown = 0;
      for (ValidationError e : errors) {
        if (e == null) continue;
        out.append("  - row ")
            .append(e.rowIndex() + 1)
            .append(": ")
            .append(e.effectiveLabel())
            .append("\n");
        shown++;
        if (shown >= 5) {
          int remain = errors.size() - shown;
          if (remain > 0) out.append("  (").append(remain).append(" more)\n");
          break;
        }
      }
      out.append("\n");
    }

    List<String> matches = new ArrayList<>();
    List<String> invalidRegex = new ArrayList<>();

    for (NotificationRule r : (rules != null ? rules : List.<NotificationRule>of())) {
      if (r == null) continue;
      if (!r.enabled()) continue;
      String pat = r.pattern() != null ? r.pattern().trim() : "";
      if (pat.isEmpty()) continue;

      if (r.type() == NotificationRule.Type.REGEX) {
        Pattern p;
        try {
          int flags = Pattern.UNICODE_CASE;
          if (!r.caseSensitive()) flags |= Pattern.CASE_INSENSITIVE;
          p = Pattern.compile(pat, flags);
        } catch (Exception ex) {
          invalidRegex.add(r.label());
          continue;
        }

        Matcher m = p.matcher(msg);
        if (m.find()) {
          String snip = snippetAround(msg, m.start(), m.end());
          matches.add(lineFor(r, snip));
        }
      } else {
        RuleMatch m = findWordMatch(msg, pat, r.caseSensitive(), r.wholeWord());
        if (m != null) {
          String snip = snippetAround(msg, m.start, m.end);
          matches.add(lineFor(r, snip));
        }
      }
    }

    if (!invalidRegex.isEmpty() && (errors == null || errors.isEmpty())) {
      out.append("Some REGEX rules are invalid and were ignored.\n\n");
    }

    if (matches.isEmpty()) {
      out.append("No matches.");
    } else {
      out.append("Matches (").append(matches.size()).append("):\n");
      for (String l : matches) {
        out.append("  ").append(l).append("\n");
      }
    }

    return out.toString().trim();
  }

  private static String lineFor(NotificationRule rule, String snippet) {
    String label =
        (rule.label() != null && !rule.label().trim().isEmpty())
            ? rule.label().trim()
            : (rule.pattern() != null ? rule.pattern().trim() : "(unnamed)");
    return "- " + label + " [" + rule.type() + "]: " + snippet;
  }

  private static String snippetAround(String msg, int start, int end) {
    if (msg == null) return "";
    int len = msg.length();
    if (start < 0) start = 0;
    if (end < start) end = start;
    if (end > len) end = len;

    int ctx = 30;
    int s = Math.max(0, start - ctx);
    int e = Math.min(len, end + ctx);

    String prefix = s > 0 ? "…" : "";
    String suffix = e < len ? "…" : "";

    String before = msg.substring(s, start);
    String mid = msg.substring(start, end);
    String after = msg.substring(end, e);

    return prefix + collapseWs(before) + "[" + collapseWs(mid) + "]" + collapseWs(after) + suffix;
  }

  private static String collapseWs(String s) {
    if (s == null || s.isEmpty()) return "";
    return s.replaceAll("\\s+", " ");
  }

  private static RuleMatch findWordMatch(
      String msg, String pat, boolean caseSensitive, boolean wholeWord) {
    if (msg == null || pat == null) return null;
    if (pat.isEmpty()) return null;

    if (wholeWord) {
      int plen = pat.length();
      for (Token tok : tokenize(msg)) {
        int tlen = tok.end - tok.start;
        if (tlen != plen) continue;

        boolean ok =
            caseSensitive
                ? msg.regionMatches(false, tok.start, pat, 0, plen)
                : msg.regionMatches(true, tok.start, pat, 0, plen);

        if (ok) return new RuleMatch(tok.start, tok.end);
      }
      return null;
    }

    int idx;
    if (caseSensitive) {
      idx = msg.indexOf(pat);
    } else {
      idx = msg.toLowerCase(Locale.ROOT).indexOf(pat.toLowerCase(Locale.ROOT));
    }
    if (idx < 0) return null;
    return new RuleMatch(idx, idx + pat.length());
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

  private static boolean isWordChar(char ch) {
    if (ch >= '0' && ch <= '9') return true;
    if (ch >= 'A' && ch <= 'Z') return true;
    if (ch >= 'a' && ch <= 'z') return true;
    return ch == '_' || ch == '-';
  }

  private record Token(int start, int end) {}

  private record RuleMatch(int start, int end) {}
}

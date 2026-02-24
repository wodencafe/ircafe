package cafe.woden.ircclient.ui.filter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * WeeChat-style tag expression matcher.
 *
 * <p>This is intentionally pragmatic and UI-focused. It supports:
 *
 * <ul>
 *   <li>OR: comma or whitespace
 *   <li>AND: '+' inside a term
 *   <li>Negation: '!tag'
 *   <li>Glob tags: '*' and '?' wildcards
 *   <li>Regex tags: 're:&lt;body&gt;' or '/&lt;body&gt;/i'
 * </ul>
 *
 * <p>Matching is case-insensitive.
 */
public final class TagSpec {

  private static final TagSpec EMPTY = new TagSpec("", List.of());

  private final String expr;
  private final List<OrTerm> terms;

  private TagSpec(String expr, List<OrTerm> terms) {
    this.expr = Objects.toString(expr, "");
    this.terms = (terms == null) ? List.of() : List.copyOf(terms);
  }

  public static TagSpec empty() {
    return EMPTY;
  }

  public static TagSpec parse(String expr) {
    String raw = Objects.toString(expr, "").trim();
    if (raw.isEmpty() || raw.equals("*")) return empty();

    List<OrTerm> terms = new ArrayList<>();
    for (String part : splitOrParts(raw)) {
      String p = Objects.toString(part, "").trim();
      if (p.isEmpty()) continue;
      OrTerm t = parseOrTerm(p);
      if (!t.isEmpty()) {
        terms.add(t);
      }
    }

    if (terms.isEmpty()) return empty();
    return new TagSpec(raw, terms);
  }

  public String expr() {
    return expr;
  }

  public boolean isEmpty() {
    return terms.isEmpty();
  }

  /** Returns true if this tag spec matches the provided set of tags. */
  public boolean matches(Set<String> tags) {
    if (isEmpty()) return true;

    Set<String> safe = (tags == null) ? Set.of() : tags;
    if (safe.isEmpty()) {
      // If there are requirements, empty tags can't satisfy a positive clause.
      for (OrTerm t : terms) {
        if (t.hasAnyPositive()) return false;
      }
      // Spec only had negatives? treat as match.
      return true;
    }

    for (OrTerm term : terms) {
      if (term.matches(safe)) return true;
    }
    return false;
  }

  private static OrTerm parseOrTerm(String token) {
    List<Clause> clauses = new ArrayList<>();
    for (String a : splitAndParts(token)) {
      String s = Objects.toString(a, "").trim();
      if (s.isEmpty()) continue;
      boolean neg = s.startsWith("!");
      if (neg) s = s.substring(1);
      s = s.trim();
      if (s.isEmpty()) continue;

      clauses.add(new Clause(neg, compileTokenMatcher(s)));
    }
    return new OrTerm(clauses);
  }

  private static List<String> splitOrParts(String raw) {
    // OR separators are comma or whitespace. Keep '+' as part of the same term,
    // including the common spaced form: "tag_a + tag_b".
    String normalized = Objects.toString(raw, "").replace(',', ' ');
    String[] parts = normalized.split("\\s+");
    List<String> out = new ArrayList<>();
    boolean pendingPlus = false;
    for (String part : parts) {
      String p = Objects.toString(part, "").trim();
      if (p.isEmpty()) continue;
      if (p.equals("+")) {
        if (!out.isEmpty()) {
          int last = out.size() - 1;
          out.set(last, out.get(last) + "+");
          pendingPlus = true;
        }
        continue;
      }
      if (pendingPlus && !out.isEmpty()) {
        int last = out.size() - 1;
        out.set(last, out.get(last) + p);
        pendingPlus = false;
      } else {
        out.add(p);
      }
    }
    return out;
  }

  private static List<String> splitAndParts(String token) {
    String t = Objects.toString(token, "").trim();
    if (t.isEmpty()) return List.of();

    // For re:<body>, treat the full token as one matcher. This avoids splitting
    // common regex quantifiers like '+' inside the body.
    String tl = t.toLowerCase(Locale.ROOT);
    if (tl.startsWith("re:")) {
      return List.of(t);
    }

    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inRegexLiteral = false;
    boolean escaping = false;
    boolean regexClosed = false;

    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (escaping) {
        cur.append(c);
        escaping = false;
        continue;
      }
      if (c == '\\') {
        cur.append(c);
        escaping = true;
        continue;
      }

      if (inRegexLiteral) {
        cur.append(c);
        if (c == '/') {
          inRegexLiteral = false;
          regexClosed = true;
        }
        continue;
      }

      if (regexClosed) {
        // Accept /.../ims flag tail as part of the same clause.
        char lc = Character.toLowerCase(c);
        if (lc == 'i' || lc == 'm' || lc == 's') {
          cur.append(c);
          continue;
        }
        regexClosed = false;
      }

      if (c == '/' && cur.isEmpty()) {
        inRegexLiteral = true;
        cur.append(c);
        continue;
      }

      if (c == '+') {
        String part = cur.toString().trim();
        if (!part.isEmpty()) out.add(part);
        cur.setLength(0);
        regexClosed = false;
        continue;
      }

      cur.append(c);
    }

    String part = cur.toString().trim();
    if (!part.isEmpty()) out.add(part);
    return out;
  }

  private static Matcher compileTokenMatcher(String token) {
    String t = Objects.toString(token, "").trim();
    if (t.isEmpty() || t.equals("*")) {
      return new Matcher(Pattern.compile("^.*$", Pattern.CASE_INSENSITIVE), token);
    }

    String tl = t.toLowerCase(Locale.ROOT);
    if (tl.startsWith("re:")) {
      String body = t.substring(3);
      return new Matcher(compileRegexBody(body, EnumSet.of(RegexFlag.I)), token);
    }

    // Regex literal /body/flags
    if (t.length() >= 2 && t.startsWith("/")) {
      int last = findLastUnescapedSlash(t);
      if (last > 0) {
        String body = t.substring(1, last);
        String flags = t.substring(last + 1);
        EnumSet<RegexFlag> fs = parseFlags(flags);
        // Always case-insensitive by default for tags.
        fs.add(RegexFlag.I);
        return new Matcher(compileRegexBody(body, fs), token);
      }
    }

    // Glob if it contains wildcards, else exact.
    boolean hasGlob = t.indexOf('*') >= 0 || t.indexOf('?') >= 0;
    if (hasGlob) {
      return new Matcher(compileGlob(t), token);
    }

    String rx = "^" + Pattern.quote(t) + "$";
    return new Matcher(Pattern.compile(rx, Pattern.CASE_INSENSITIVE), token);
  }

  private static EnumSet<RegexFlag> parseFlags(String flags) {
    EnumSet<RegexFlag> out = EnumSet.noneOf(RegexFlag.class);
    String f = Objects.toString(flags, "").trim();
    for (int i = 0; i < f.length(); i++) {
      char c = Character.toLowerCase(f.charAt(i));
      switch (c) {
        case 'i' -> out.add(RegexFlag.I);
        case 'm' -> out.add(RegexFlag.M);
        case 's' -> out.add(RegexFlag.S);
        default -> {
          // ignore
        }
      }
    }
    return out;
  }

  private static int findLastUnescapedSlash(String v) {
    for (int i = v.length() - 1; i > 0; i--) {
      if (v.charAt(i) != '/') continue;
      int bs = 0;
      for (int j = i - 1; j >= 0 && v.charAt(j) == '\\'; j--) bs++;
      if ((bs % 2) == 0) return i;
    }
    return -1;
  }

  private static Pattern compileGlob(String glob) {
    String g = Objects.toString(glob, "*");
    StringBuilder rx = new StringBuilder(g.length() * 2);
    rx.append('^');
    for (int i = 0; i < g.length(); i++) {
      char c = g.charAt(i);
      switch (c) {
        case '*':
          rx.append(".*");
          break;
        case '?':
          rx.append('.');
          break;
        case '.', '(', ')', '[', ']', '{', '}', '+', '$', '^', '|', '\\':
          rx.append('\\').append(c);
          break;
        default:
          rx.append(c);
      }
    }
    rx.append('$');
    return Pattern.compile(rx.toString(), Pattern.CASE_INSENSITIVE);
  }

  private static Pattern compileRegexBody(String body, EnumSet<RegexFlag> flags) {
    int f = 0;
    if (flags != null) {
      if (flags.contains(RegexFlag.I)) f |= Pattern.CASE_INSENSITIVE;
      if (flags.contains(RegexFlag.M)) f |= Pattern.MULTILINE;
      if (flags.contains(RegexFlag.S)) f |= Pattern.DOTALL;
    }
    try {
      return Pattern.compile(Objects.toString(body, ""), f);
    } catch (PatternSyntaxException e) {
      // Defensive fallback.
      return Pattern.compile("^$", Pattern.CASE_INSENSITIVE);
    }
  }

  private record Matcher(Pattern pattern, String raw) {
    boolean matchesAny(Set<String> tags) {
      if (tags == null || tags.isEmpty()) return false;
      for (String tag : tags) {
        String t = Objects.toString(tag, "");
        if (pattern.matcher(t).matches()) return true;
      }
      return false;
    }
  }

  private record Clause(boolean negated, Matcher matcher) {}

  private static final class OrTerm {
    private final List<Clause> clauses;

    private OrTerm(List<Clause> clauses) {
      this.clauses = (clauses == null) ? List.of() : List.copyOf(clauses);
    }

    boolean isEmpty() {
      return clauses.isEmpty();
    }

    boolean hasAnyPositive() {
      for (Clause c : clauses) {
        if (!c.negated()) return true;
      }
      return false;
    }

    boolean matches(Set<String> tags) {
      for (Clause c : clauses) {
        boolean m = c.matcher().matchesAny(tags);
        if (c.negated()) {
          if (m) return false; // forbidden tag present
        } else {
          if (!m) return false; // required tag missing
        }
      }
      return true;
    }
  }
}

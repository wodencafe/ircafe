package cafe.woden.ircclient.state.api;

import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared IRC mode semantic helpers layered on top of {@link ModeVocabulary}. */
@ApplicationLayer
public final class NegotiatedModeSemantics {
  private NegotiatedModeSemantics() {}

  public static boolean takesArgument(ModeVocabulary vocabulary, char mode, boolean adding) {
    return effective(vocabulary).takesArgument(mode, adding);
  }

  public static boolean isStatusMode(ModeVocabulary vocabulary, char mode, String arg) {
    ModeVocabulary effective = effective(vocabulary);
    if (effective.hasExplicitStatusMode(mode)) return true;
    if (effective.hasExplicitListMode(mode)) return false;
    if (mode == 'q') return !looksLikeMaskTarget(arg);
    return effective.isStatusMode(mode);
  }

  public static boolean isListMode(ModeVocabulary vocabulary, char mode, String arg) {
    ModeVocabulary effective = effective(vocabulary);
    if (effective.hasExplicitListMode(mode)) return true;
    if (effective.hasExplicitStatusMode(mode)) return false;
    if (mode == 'q') return looksLikeMaskTarget(arg);
    return effective.isListMode(mode);
  }

  public static boolean looksLikeMaskTarget(String arg) {
    String value = Objects.toString(arg, "").trim();
    if (value.isEmpty()) return false;
    return value.indexOf('!') >= 0
        || value.indexOf('@') >= 0
        || value.indexOf('*') >= 0
        || value.indexOf('$') >= 0
        || value.indexOf(':') >= 0;
  }

  public static boolean looksLikeSnapshotModeDetails(ModeVocabulary vocabulary, String details) {
    if (details == null) return false;
    String d = details.trim();
    if (d.isEmpty()) return false;

    int sp = d.indexOf(' ');
    String modeToken = (sp < 0) ? d : d.substring(0, sp);
    if (modeToken.isEmpty()) return false;

    boolean sawSign = false;
    boolean sawMode = false;
    boolean adding = true;
    ModeVocabulary effective = effective(vocabulary);
    for (int i = 0; i < modeToken.length(); i++) {
      char c = modeToken.charAt(i);
      if (c == '+') {
        sawSign = true;
        adding = true;
        continue;
      }
      if (c == '-') return false;
      if (!Character.isLetterOrDigit(c)) return false;
      if (!adding) return false;
      if (isStatusMode(effective, c, null) || effective.isListMode(c)) return false;
      sawMode = true;
    }
    return sawSign && sawMode;
  }

  private static ModeVocabulary effective(ModeVocabulary vocabulary) {
    return vocabulary == null ? ModeVocabulary.fallback() : vocabulary;
  }
}

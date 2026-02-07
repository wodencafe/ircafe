package cafe.woden.ircclient.ui.terminal;

/**
 * Very small in-memory buffer for terminal output.
 *
 * <p>We keep only the last {@code maxChars} characters.
 */
public final class TerminalBuffer {

  private final int maxChars;
  private final StringBuilder sb = new StringBuilder();

  public TerminalBuffer(int maxChars) {
    this.maxChars = Math.max(10_000, maxChars);
  }

  public synchronized void append(String s) {
    if (s == null || s.isEmpty()) return;
    sb.append(s);
    trimIfNeeded();
  }

  public synchronized String snapshot() {
    return sb.toString();
  }

  private void trimIfNeeded() {
    int over = sb.length() - maxChars;
    if (over <= 0) return;

    // Drop from the front. Keep a small cushion to reduce churn.
    int drop = over + Math.min(16_384, maxChars / 10);
    drop = Math.min(drop, sb.length());
    sb.delete(0, drop);
  }
}

package cafe.woden.ircclient.ui.chat;

/**
 * Runtime nick-color settings (small bus-backed config).
 *
 * <p>These are separated from {@link cafe.woden.ircclient.ui.settings.UiSettings} because they
 * primarily affect render styling (nicks) and can be updated independently.
 */
public record NickColorSettings(boolean enabled, double minContrast) {
  public NickColorSettings {
    // Defaults / normalization
    if (minContrast <= 0) minContrast = 3.0;
    if (minContrast > 21.0) minContrast = 21.0;
  }
}

package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.ChatAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ThemeAppearanceRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for appearance runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigAppearanceAdapter
    implements ThemeAppearanceRuntimeConfigPort,
        ChatAppearanceRuntimeConfigPort,
        ServerTreeAppearanceRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigAppearanceAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberAccentColor(String accentColor) {
    runtimeConfig.rememberAccentColor(accentColor);
  }

  @Override
  public void rememberAccentStrength(int strength) {
    runtimeConfig.rememberAccentStrength(strength);
  }

  @Override
  public void rememberUiDensity(String density) {
    runtimeConfig.rememberUiDensity(density);
  }

  @Override
  public void rememberUiFontOverrideEnabled(boolean enabled) {
    runtimeConfig.rememberUiFontOverrideEnabled(enabled);
  }

  @Override
  public void rememberUiFontFamily(String family) {
    runtimeConfig.rememberUiFontFamily(family);
  }

  @Override
  public void rememberUiFontSize(int size) {
    runtimeConfig.rememberUiFontSize(size);
  }

  @Override
  public void rememberCornerRadius(int cornerRadius) {
    runtimeConfig.rememberCornerRadius(cornerRadius);
  }

  @Override
  public void rememberChatThemePreset(String preset) {
    runtimeConfig.rememberChatThemePreset(preset);
  }

  @Override
  public void rememberChatTimestampColor(String hex) {
    runtimeConfig.rememberChatTimestampColor(hex);
  }

  @Override
  public void rememberChatSystemColor(String hex) {
    runtimeConfig.rememberChatSystemColor(hex);
  }

  @Override
  public void rememberChatMessageColor(String hex) {
    runtimeConfig.rememberChatMessageColor(hex);
  }

  @Override
  public void rememberChatNoticeColor(String hex) {
    runtimeConfig.rememberChatNoticeColor(hex);
  }

  @Override
  public void rememberChatActionColor(String hex) {
    runtimeConfig.rememberChatActionColor(hex);
  }

  @Override
  public void rememberChatErrorColor(String hex) {
    runtimeConfig.rememberChatErrorColor(hex);
  }

  @Override
  public void rememberChatPresenceColor(String hex) {
    runtimeConfig.rememberChatPresenceColor(hex);
  }

  @Override
  public void rememberChatMentionBgColor(String hex) {
    runtimeConfig.rememberChatMentionBgColor(hex);
  }

  @Override
  public void rememberChatMentionStrength(int strength) {
    runtimeConfig.rememberChatMentionStrength(strength);
  }

  @Override
  public void rememberServerTreeUnreadChannelColor(String hex) {
    runtimeConfig.rememberServerTreeUnreadChannelColor(hex);
  }

  @Override
  public void rememberServerTreeHighlightChannelColor(String hex) {
    runtimeConfig.rememberServerTreeHighlightChannelColor(hex);
  }
}

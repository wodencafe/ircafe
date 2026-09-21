package cafe.woden.ircclient.config.runtime.ui;

import cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSupport;

/** Pure metadata and parsing helpers for section-scoped UI feature toggles. */
final class RuntimeConfigUiFeatureToggleCodec {

  enum Setting {
    INVITE_AUTO_JOIN("invites", "autoJoinOnInvite", "invites.autoJoinOnInvite"),
    UPDATE_NOTIFIER("updateNotifier", "enabled", "ui.updateNotifier.enabled"),
    LAG_INDICATOR("lagIndicator", "enabled", "ui.lagIndicator.enabled"),
    FIRST_RUN_SETUP_DISMISSED("setup", "dismissed", "ui.setup.dismissed");

    private final String section;
    private final String key;
    private final String description;

    Setting(String section, String key, String description) {
      this.section = section;
      this.key = key;
      this.description = description;
    }

    String section() {
      return section;
    }

    String key() {
      return key;
    }

    String description() {
      return description;
    }
  }

  private RuntimeConfigUiFeatureToggleCodec() {}

  static boolean readBoolean(Object raw, boolean defaultValue) {
    return RuntimeConfigYamlSupport.asBoolean(raw).orElse(defaultValue);
  }
}

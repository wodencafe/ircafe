package cafe.woden.ircclient.config.runtime.ui;

import cafe.woden.ircclient.config.runtime.ui.RuntimeConfigUiFeatureToggleCodec.Setting;
import cafe.woden.ircclient.config.yaml.RuntimeConfigDocumentStore;
import cafe.woden.ircclient.config.yaml.RuntimeConfigYamlSection;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns simple section-scoped UI feature toggles under {@code ircafe.ui}. */
public class RuntimeConfigUiFeatureToggleStore {

  private static final Logger log =
      LoggerFactory.getLogger(RuntimeConfigUiFeatureToggleStore.class);

  private final RuntimeConfigYamlSection uiSection;

  public RuntimeConfigUiFeatureToggleStore(Path file, RuntimeConfigDocumentStore documentStore) {
    this.uiSection = RuntimeConfigYamlSection.ircafeUi(file, documentStore, log);
  }

  public synchronized boolean readInviteAutoJoinEnabled(boolean defaultValue) {
    return readBoolean(Setting.INVITE_AUTO_JOIN, defaultValue);
  }

  public synchronized void rememberInviteAutoJoinEnabled(boolean enabled) {
    rememberBoolean(Setting.INVITE_AUTO_JOIN, enabled);
  }

  public synchronized boolean readUpdateNotifierEnabled(boolean defaultValue) {
    return readBoolean(Setting.UPDATE_NOTIFIER, defaultValue);
  }

  public synchronized void rememberUpdateNotifierEnabled(boolean enabled) {
    rememberBoolean(Setting.UPDATE_NOTIFIER, enabled);
  }

  public synchronized boolean readLagIndicatorEnabled(boolean defaultValue) {
    return readBoolean(Setting.LAG_INDICATOR, defaultValue);
  }

  public synchronized void rememberLagIndicatorEnabled(boolean enabled) {
    rememberBoolean(Setting.LAG_INDICATOR, enabled);
  }

  private boolean readBoolean(Setting setting, boolean defaultValue) {
    return uiSection
        .readValue(setting.description(), setting.section(), setting.key())
        .map(raw -> RuntimeConfigUiFeatureToggleCodec.readBoolean(raw, defaultValue))
        .orElse(defaultValue);
  }

  public synchronized boolean readFirstRunSetupDismissed() {
    return readBoolean(Setting.FIRST_RUN_SETUP_DISMISSED, false);
  }

  public synchronized void rememberFirstRunSetupDismissed() {
    rememberBoolean(Setting.FIRST_RUN_SETUP_DISMISSED, true);
  }

  private void rememberBoolean(Setting setting, boolean enabled) {
    uiSection.putValue(setting.description(), enabled, setting.section(), setting.key());
  }
}

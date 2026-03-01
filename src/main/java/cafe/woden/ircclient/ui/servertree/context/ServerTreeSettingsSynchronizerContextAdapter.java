package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeSettingsSynchronizer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTypingIndicatorStyle;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Adapter for {@link ServerTreeSettingsSynchronizer.Context}. */
public final class ServerTreeSettingsSynchronizerContextAdapter
    implements ServerTreeSettingsSynchronizer.Context {

  private final UiSettingsBus settingsBus;
  private final JfrRuntimeEventsService jfrRuntimeEventsService;
  private final RuntimeConfigStore runtimeConfig;
  private final Supplier<Boolean> typingIndicatorsTreeEnabled;
  private final Consumer<Boolean> setTypingIndicatorsTreeEnabled;
  private final Runnable clearTypingIndicatorsFromTree;
  private final Consumer<ServerTreeTypingIndicatorStyle> setTypingIndicatorStyle;
  private final Consumer<Boolean> setServerTreeNotificationBadgesEnabled;
  private final IntConsumer setUnreadBadgeScalePercent;
  private final Runnable refreshTreeLayoutAfterUiChange;
  private final Runnable refreshApplicationJfrNode;

  public ServerTreeSettingsSynchronizerContextAdapter(
      UiSettingsBus settingsBus,
      JfrRuntimeEventsService jfrRuntimeEventsService,
      RuntimeConfigStore runtimeConfig,
      Supplier<Boolean> typingIndicatorsTreeEnabled,
      Consumer<Boolean> setTypingIndicatorsTreeEnabled,
      Runnable clearTypingIndicatorsFromTree,
      Consumer<ServerTreeTypingIndicatorStyle> setTypingIndicatorStyle,
      Consumer<Boolean> setServerTreeNotificationBadgesEnabled,
      IntConsumer setUnreadBadgeScalePercent,
      Runnable refreshTreeLayoutAfterUiChange,
      Runnable refreshApplicationJfrNode) {
    this.settingsBus = settingsBus;
    this.jfrRuntimeEventsService = jfrRuntimeEventsService;
    this.runtimeConfig = runtimeConfig;
    this.typingIndicatorsTreeEnabled =
        Objects.requireNonNull(typingIndicatorsTreeEnabled, "typingIndicatorsTreeEnabled");
    this.setTypingIndicatorsTreeEnabled =
        Objects.requireNonNull(setTypingIndicatorsTreeEnabled, "setTypingIndicatorsTreeEnabled");
    this.clearTypingIndicatorsFromTree =
        Objects.requireNonNull(clearTypingIndicatorsFromTree, "clearTypingIndicatorsFromTree");
    this.setTypingIndicatorStyle =
        Objects.requireNonNull(setTypingIndicatorStyle, "setTypingIndicatorStyle");
    this.setServerTreeNotificationBadgesEnabled =
        Objects.requireNonNull(
            setServerTreeNotificationBadgesEnabled, "setServerTreeNotificationBadgesEnabled");
    this.setUnreadBadgeScalePercent =
        Objects.requireNonNull(setUnreadBadgeScalePercent, "setUnreadBadgeScalePercent");
    this.refreshTreeLayoutAfterUiChange =
        Objects.requireNonNull(refreshTreeLayoutAfterUiChange, "refreshTreeLayoutAfterUiChange");
    this.refreshApplicationJfrNode =
        Objects.requireNonNull(refreshApplicationJfrNode, "refreshApplicationJfrNode");
  }

  @Override
  public UiSettingsBus settingsBus() {
    return settingsBus;
  }

  @Override
  public JfrRuntimeEventsService jfrRuntimeEventsService() {
    return jfrRuntimeEventsService;
  }

  @Override
  public RuntimeConfigStore runtimeConfig() {
    return runtimeConfig;
  }

  @Override
  public boolean typingIndicatorsTreeEnabled() {
    return typingIndicatorsTreeEnabled.get();
  }

  @Override
  public void setTypingIndicatorsTreeEnabled(boolean enabled) {
    setTypingIndicatorsTreeEnabled.accept(enabled);
  }

  @Override
  public void clearTypingIndicatorsFromTree() {
    clearTypingIndicatorsFromTree.run();
  }

  @Override
  public void setTypingIndicatorStyle(ServerTreeTypingIndicatorStyle style) {
    setTypingIndicatorStyle.accept(style);
  }

  @Override
  public void setServerTreeNotificationBadgesEnabled(boolean enabled) {
    setServerTreeNotificationBadgesEnabled.accept(enabled);
  }

  @Override
  public void setUnreadBadgeScalePercent(int percent) {
    setUnreadBadgeScalePercent.accept(percent);
  }

  @Override
  public void refreshTreeLayoutAfterUiChange() {
    refreshTreeLayoutAfterUiChange.run();
  }

  @Override
  public void refreshApplicationJfrNode() {
    refreshApplicationJfrNode.run();
  }
}

package cafe.woden.ircclient.ui.settings;

import javax.swing.JPanel;
import javax.swing.JSpinner;

final class UserLookupsPanelControls {
  final UserhostControls userhost;
  final UserInfoEnrichmentControls enrichment;
  final JSpinner monitorIsonPollIntervalSeconds;
  final JPanel panel;

  UserLookupsPanelControls(
      UserhostControls userhost,
      UserInfoEnrichmentControls enrichment,
      JSpinner monitorIsonPollIntervalSeconds,
      JPanel panel) {
    this.userhost = userhost;
    this.enrichment = enrichment;
    this.monitorIsonPollIntervalSeconds = monitorIsonPollIntervalSeconds;
    this.panel = panel;
  }
}

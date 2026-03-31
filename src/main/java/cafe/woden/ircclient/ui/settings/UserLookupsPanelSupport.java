package cafe.woden.ircclient.ui.settings;

import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import net.miginfocom.swing.MigLayout;

final class UserLookupsPanelSupport {
  private UserLookupsPanelSupport() {}

  static UserLookupsPanelControls buildControls(
      UiSettings current, List<AutoCloseable> closeables) {
    JPanel userLookupsPanel =
        new JPanel(new MigLayout("insets 12, fillx, wrap 1, hidemode 3", "[grow,fill]", ""));
    userLookupsPanel.add(PreferencesDialog.tabTitle("User lookups"), "growx, wrap");

    JPanel userLookupsIntro = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    userLookupsIntro.setOpaque(false);
    JTextArea userLookupsBlurb =
        PreferencesDialog.helpText(
            "Optional fallbacks for account/away/host info (USERHOST / WHOIS), with conservative rate limits.");
    JButton userLookupsHelp =
        PreferencesDialog.whyHelpButton(
            "Why do I need user lookups?",
            "Most modern IRC networks provide account and presence information via IRCv3 (e.g., account-tag, account-notify, away-notify, extended-join).\n\n"
                + "However, some networks (or some pieces of data) still require fallback lookups. IRCafe can optionally use USERHOST and (as a last resort) WHOIS to fill missing metadata.\n\n"
                + "If you're on an IRCv3-capable network and don't use hostmask-based ignore rules, you can usually leave these disabled.");
    userLookupsIntro.add(userLookupsBlurb, "growx, wmin 0");
    userLookupsIntro.add(userLookupsHelp, "align right");

    JPanel lookupPresetPanel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]"));
    lookupPresetPanel.setOpaque(false);

    JComboBox<LookupRatePreset> lookupPreset = new JComboBox<>(LookupRatePreset.values());
    lookupPreset.setSelectedItem(detectLookupRatePreset(current));

    JTextArea lookupPresetHint = PreferencesDialog.subtleInfoText();

    Runnable updateLookupPresetHint =
        () -> {
          LookupRatePreset preset = (LookupRatePreset) lookupPreset.getSelectedItem();
          if (preset == null) preset = LookupRatePreset.CUSTOM;

          String message =
              switch (preset) {
                case CONSERVATIVE -> "Lowest traffic. Best for huge channels or strict networks.";
                case BALANCED -> "Recommended default. Good fill-in speed with low risk.";
                case RAPID -> "Faster fill-in. More commands on the wire (use with caution).";
                case CUSTOM -> "Custom shows the tuning controls below.";
              };
          lookupPresetHint.setText(message);
        };
    updateLookupPresetHint.run();

    lookupPresetPanel.add(new JLabel("Rate limit preset:"));
    lookupPresetPanel.add(lookupPreset, "w 220!");
    lookupPresetPanel.add(lookupPresetHint, "span 2, growx, wmin 0, wrap");

    JSpinner monitorIsonPollIntervalSeconds =
        PreferencesDialog.numberSpinner(
            current.monitorIsonFallbackPollIntervalSeconds(), 5, 600, 5, closeables);
    monitorIsonPollIntervalSeconds.setToolTipText(
        "Polling interval for ISON monitor fallback when IRC MONITOR is unavailable.");
    lookupPresetPanel.add(new JLabel("MONITOR fallback poll (sec):"));
    lookupPresetPanel.add(monitorIsonPollIntervalSeconds, "w 110!, wrap");

    JPanel hostmaskPanel =
        new JPanel(
            new MigLayout("insets 8, fillx, wrap 2, hidemode 3", "[right]12[grow,fill]", ""));
    hostmaskPanel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Hostmask discovery"),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
    hostmaskPanel.setOpaque(false);

    JCheckBox userhostEnabled =
        new JCheckBox("Fill missing hostmasks using USERHOST (rate-limited)");
    userhostEnabled.setSelected(current.userhostDiscoveryEnabled());
    userhostEnabled.setToolTipText(
        "When enabled, IRCafe may send USERHOST only when hostmask-based ignore rules exist and some nicks are missing hostmasks.");

    JButton hostmaskHelp =
        PreferencesDialog.whyHelpButton(
            "Why do I need hostmask discovery?",
            "Some ignore rules rely on hostmasks (nick!user@host).\n\n"
                + "On many networks, the full hostmask isn't included in NAMES and might not be available until additional lookups happen.\n\n"
                + "If you use hostmask-based ignore rules and some users show up without hostmasks, IRCafe can send rate-limited USERHOST commands to fill them in.\n\n"
                + "If you don't use hostmask-based ignores, you can usually leave this off.");

    JTextArea hostmaskSummary = PreferencesDialog.subtleInfoText();
    hostmaskSummary.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

    JSpinner userhostMinIntervalSeconds =
        PreferencesDialog.numberSpinner(current.userhostMinIntervalSeconds(), 1, 60, 1, closeables);
    userhostMinIntervalSeconds.setToolTipText(
        "Minimum seconds between USERHOST commands per server.");

    JSpinner userhostMaxPerMinute =
        PreferencesDialog.numberSpinner(
            current.userhostMaxCommandsPerMinute(), 1, 60, 1, closeables);
    userhostMaxPerMinute.setToolTipText("Maximum USERHOST commands per minute per server.");

    JSpinner userhostNickCooldownMinutes =
        PreferencesDialog.numberSpinner(
            current.userhostNickCooldownMinutes(), 1, 240, 1, closeables);
    userhostNickCooldownMinutes.setToolTipText(
        "Cooldown in minutes before re-querying the same nick.");

    JSpinner userhostMaxNicksPerCommand =
        PreferencesDialog.numberSpinner(current.userhostMaxNicksPerCommand(), 1, 5, 1, closeables);
    userhostMaxNicksPerCommand.setToolTipText(
        "How many nicks to include per USERHOST command (servers typically allow up to 5).");

    JPanel hostmaskAdvanced =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    hostmaskAdvanced.setOpaque(false);
    hostmaskAdvanced.add(new JLabel("Min interval (sec):"));
    hostmaskAdvanced.add(userhostMinIntervalSeconds, "w 110!");
    hostmaskAdvanced.add(new JLabel("Max commands/min:"));
    hostmaskAdvanced.add(userhostMaxPerMinute, "w 110!");
    hostmaskAdvanced.add(new JLabel("Nick cooldown (min):"));
    hostmaskAdvanced.add(userhostNickCooldownMinutes, "w 110!");
    hostmaskAdvanced.add(new JLabel("Max nicks/command:"));
    hostmaskAdvanced.add(userhostMaxNicksPerCommand, "w 110!");

    JPanel enrichmentPanel =
        new JPanel(
            new MigLayout("insets 8, fillx, wrap 2, hidemode 3", "[right]12[grow,fill]", ""));
    enrichmentPanel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Roster enrichment (fallback)"),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
    enrichmentPanel.setOpaque(false);

    JCheckBox enrichmentEnabled =
        new JCheckBox("Best-effort roster enrichment using USERHOST (rate-limited)");
    enrichmentEnabled.setSelected(current.userInfoEnrichmentEnabled());
    enrichmentEnabled.setToolTipText(
        "When enabled, IRCafe may send USERHOST occasionally to enrich user info even when you don't have hostmask-based ignore rules.\n"
            + "This is a best-effort fallback for older networks.");

    JCheckBox enrichmentWhoisFallbackEnabled =
        new JCheckBox("Also use WHOIS fallback for account info (very slow)");
    enrichmentWhoisFallbackEnabled.setSelected(current.userInfoEnrichmentWhoisFallbackEnabled());
    enrichmentWhoisFallbackEnabled.setToolTipText(
        "When enabled, IRCafe may occasionally send WHOIS to learn account login state/name and away message.\n"
            + "This is slower and more likely to hit server rate limits. Recommended OFF by default.");

    JCheckBox enrichmentPeriodicRefreshEnabled =
        new JCheckBox("Periodic background refresh (slow scan)");
    enrichmentPeriodicRefreshEnabled.setSelected(
        current.userInfoEnrichmentPeriodicRefreshEnabled());
    enrichmentPeriodicRefreshEnabled.setToolTipText(
        "When enabled, IRCafe will periodically re-check a small number of nicks to detect changes.\n"
            + "Use conservative intervals to avoid extra network load.");

    JButton enrichmentHelp =
        PreferencesDialog.whyHelpButton(
            "Why do I need roster enrichment?",
            "This is a best-effort fallback for older networks or edge cases where IRCv3 metadata isn't available.\n\n"
                + "IRCafe can use rate-limited USERHOST to fill missing user info. Optionally it can also use WHOIS (much slower) to learn account/away details.\n\n"
                + "On modern IRCv3 networks, you typically don't need this. Leave it OFF unless you have a specific reason.");

    JButton whoisHelp =
        PreferencesDialog.whyHelpButton(
            "WHOIS fallback",
            "WHOIS is the slowest and noisiest fallback. It can provide account and away information when IRCv3 isn't available, but it is easy to hit server throttles.\n\n"
                + "Keep this OFF unless you're on a network that doesn't provide account info via IRCv3.");

    JButton refreshHelp =
        PreferencesDialog.whyHelpButton(
            "Periodic background refresh",
            "This periodically re-probes a small number of users to detect changes (e.g., account/away state) on networks that don't push updates.\n\n"
                + "It's a slow scan by design: use high intervals and small batch sizes to avoid extra network load.");

    JTextArea enrichmentSummary = PreferencesDialog.subtleInfoText();
    enrichmentSummary.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

    JSpinner enrichmentUserhostMinIntervalSeconds =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentUserhostMinIntervalSeconds(), 1, 300, 1, closeables);
    enrichmentUserhostMinIntervalSeconds.setToolTipText(
        "Minimum seconds between USERHOST commands per server for enrichment.");

    JSpinner enrichmentUserhostMaxPerMinute =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentUserhostMaxCommandsPerMinute(), 1, 60, 1, closeables);
    enrichmentUserhostMaxPerMinute.setToolTipText(
        "Maximum USERHOST commands per minute per server for enrichment.");

    JSpinner enrichmentUserhostNickCooldownMinutes =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentUserhostNickCooldownMinutes(), 1, 1440, 1, closeables);
    enrichmentUserhostNickCooldownMinutes.setToolTipText(
        "Cooldown in minutes before re-querying the same nick via USERHOST (enrichment).\n"
            + "Higher values reduce network load.");

    JSpinner enrichmentUserhostMaxNicksPerCommand =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentUserhostMaxNicksPerCommand(), 1, 5, 1, closeables);
    enrichmentUserhostMaxNicksPerCommand.setToolTipText(
        "How many nicks to include per USERHOST command (servers typically allow up to 5).\n"
            + "This applies to enrichment mode, separate from hostmask discovery.");

    JSpinner enrichmentWhoisMinIntervalSeconds =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentWhoisMinIntervalSeconds(), 5, 600, 5, closeables);
    enrichmentWhoisMinIntervalSeconds.setToolTipText(
        "Minimum seconds between WHOIS commands per server (enrichment).\n"
            + "Keep this high to avoid throttling.");

    JSpinner enrichmentWhoisNickCooldownMinutes =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentWhoisNickCooldownMinutes(), 1, 1440, 1, closeables);
    enrichmentWhoisNickCooldownMinutes.setToolTipText(
        "Cooldown in minutes before re-WHOIS'ing the same nick.");

    JSpinner enrichmentPeriodicRefreshIntervalSeconds =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentPeriodicRefreshIntervalSeconds(), 30, 3600, 30, closeables);
    enrichmentPeriodicRefreshIntervalSeconds.setToolTipText(
        "How often to run a slow scan tick (seconds).\n"
            + "Higher values are safer. Example: 300 seconds (5 minutes).");

    JSpinner enrichmentPeriodicRefreshNicksPerTick =
        PreferencesDialog.numberSpinner(
            current.userInfoEnrichmentPeriodicRefreshNicksPerTick(), 1, 20, 1, closeables);
    enrichmentPeriodicRefreshNicksPerTick.setToolTipText(
        "How many nicks to probe per periodic tick.\nKeep this small (e.g., 1-3).");

    JPanel enrichmentAdvanced =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2",
                "[right]12[grow,fill]",
                "[]6[]6[]6[]10[]6[]6[]10[]6[]6[]"));
    enrichmentAdvanced.setOpaque(false);
    JLabel userhostHdr = new JLabel("USERHOST tuning");
    userhostHdr.setFont(userhostHdr.getFont().deriveFont(Font.BOLD));
    enrichmentAdvanced.add(userhostHdr, "span 2, growx, wmin 0, wrap");
    enrichmentAdvanced.add(new JLabel("Min interval (sec):"));
    enrichmentAdvanced.add(enrichmentUserhostMinIntervalSeconds, "w 110!");
    enrichmentAdvanced.add(new JLabel("Max cmd/min:"));
    enrichmentAdvanced.add(enrichmentUserhostMaxPerMinute, "w 110!");
    enrichmentAdvanced.add(new JLabel("Nick cooldown (min):"));
    enrichmentAdvanced.add(enrichmentUserhostNickCooldownMinutes, "w 110!");
    enrichmentAdvanced.add(new JLabel("Max nicks/cmd:"));
    enrichmentAdvanced.add(enrichmentUserhostMaxNicksPerCommand, "w 110!");
    JLabel whoisHdr = new JLabel("WHOIS tuning");
    whoisHdr.setFont(whoisHdr.getFont().deriveFont(Font.BOLD));
    enrichmentAdvanced.add(whoisHdr, "span 2, growx, wmin 0, wrap");
    enrichmentAdvanced.add(new JLabel("Min interval (sec):"));
    enrichmentAdvanced.add(enrichmentWhoisMinIntervalSeconds, "w 110!");
    enrichmentAdvanced.add(new JLabel("Nick cooldown (min):"));
    enrichmentAdvanced.add(enrichmentWhoisNickCooldownMinutes, "w 110!");
    JLabel refreshHdr = new JLabel("Periodic refresh tuning");
    refreshHdr.setFont(refreshHdr.getFont().deriveFont(Font.BOLD));
    enrichmentAdvanced.add(refreshHdr, "span 2, growx, wmin 0, wrap");
    enrichmentAdvanced.add(new JLabel("Interval (sec):"));
    enrichmentAdvanced.add(enrichmentPeriodicRefreshIntervalSeconds, "w 110!");
    enrichmentAdvanced.add(new JLabel("Nicks per tick:"));
    enrichmentAdvanced.add(enrichmentPeriodicRefreshNicksPerTick, "w 110!");

    Consumer<LookupRatePreset> applyLookupPreset =
        preset -> {
          if (preset == null || preset == LookupRatePreset.CUSTOM) return;

          switch (preset) {
            case CONSERVATIVE -> {
              userhostMinIntervalSeconds.setValue(10);
              userhostMaxPerMinute.setValue(2);
              userhostNickCooldownMinutes.setValue(60);
              userhostMaxNicksPerCommand.setValue(5);
              enrichmentUserhostMinIntervalSeconds.setValue(30);
              enrichmentUserhostMaxPerMinute.setValue(2);
              enrichmentUserhostNickCooldownMinutes.setValue(180);
              enrichmentUserhostMaxNicksPerCommand.setValue(5);
              enrichmentWhoisMinIntervalSeconds.setValue(120);
              enrichmentWhoisNickCooldownMinutes.setValue(240);
              enrichmentPeriodicRefreshIntervalSeconds.setValue(600);
              enrichmentPeriodicRefreshNicksPerTick.setValue(1);
            }
            case BALANCED -> {
              userhostMinIntervalSeconds.setValue(5);
              userhostMaxPerMinute.setValue(6);
              userhostNickCooldownMinutes.setValue(30);
              userhostMaxNicksPerCommand.setValue(5);
              enrichmentUserhostMinIntervalSeconds.setValue(15);
              enrichmentUserhostMaxPerMinute.setValue(4);
              enrichmentUserhostNickCooldownMinutes.setValue(60);
              enrichmentUserhostMaxNicksPerCommand.setValue(5);
              enrichmentWhoisMinIntervalSeconds.setValue(60);
              enrichmentWhoisNickCooldownMinutes.setValue(120);
              enrichmentPeriodicRefreshIntervalSeconds.setValue(300);
              enrichmentPeriodicRefreshNicksPerTick.setValue(2);
            }
            case RAPID -> {
              userhostMinIntervalSeconds.setValue(2);
              userhostMaxPerMinute.setValue(15);
              userhostNickCooldownMinutes.setValue(10);
              userhostMaxNicksPerCommand.setValue(5);
              enrichmentUserhostMinIntervalSeconds.setValue(5);
              enrichmentUserhostMaxPerMinute.setValue(10);
              enrichmentUserhostNickCooldownMinutes.setValue(15);
              enrichmentUserhostMaxNicksPerCommand.setValue(5);
              enrichmentWhoisMinIntervalSeconds.setValue(15);
              enrichmentWhoisNickCooldownMinutes.setValue(30);
              enrichmentPeriodicRefreshIntervalSeconds.setValue(60);
              enrichmentPeriodicRefreshNicksPerTick.setValue(3);
            }
            case CUSTOM -> {
              // No-op.
            }
          }
        };

    Runnable updateHostmaskSummary =
        () -> {
          if (!userhostEnabled.isSelected()) {
            hostmaskSummary.setText("Disabled");
            return;
          }
          int minInterval = ((Number) userhostMinIntervalSeconds.getValue()).intValue();
          int maxPerMinute = ((Number) userhostMaxPerMinute.getValue()).intValue();
          int cooldownMinutes = ((Number) userhostNickCooldownMinutes.getValue()).intValue();
          int maxNicks = ((Number) userhostMaxNicksPerCommand.getValue()).intValue();
          hostmaskSummary.setText(
              String.format(
                  "USERHOST ≤%d/min • min %ds • cooldown %dm • up to %d nicks/cmd",
                  maxPerMinute, minInterval, cooldownMinutes, maxNicks));
        };

    Runnable updateEnrichmentSummary =
        () -> {
          if (!enrichmentEnabled.isSelected()) {
            enrichmentSummary.setText("Disabled");
            return;
          }

          int minInterval = ((Number) enrichmentUserhostMinIntervalSeconds.getValue()).intValue();
          int maxPerMinute = ((Number) enrichmentUserhostMaxPerMinute.getValue()).intValue();
          int cooldownMinutes =
              ((Number) enrichmentUserhostNickCooldownMinutes.getValue()).intValue();
          int maxNicks = ((Number) enrichmentUserhostMaxNicksPerCommand.getValue()).intValue();

          String whoisSummary;
          if (enrichmentWhoisFallbackEnabled.isSelected()) {
            int whoisMinInterval =
                ((Number) enrichmentWhoisMinIntervalSeconds.getValue()).intValue();
            int whoisCooldown = ((Number) enrichmentWhoisNickCooldownMinutes.getValue()).intValue();
            whoisSummary =
                String.format("WHOIS min %ds, cooldown %dm", whoisMinInterval, whoisCooldown);
          } else {
            whoisSummary = "WHOIS off";
          }

          String refreshSummary;
          if (enrichmentPeriodicRefreshEnabled.isSelected()) {
            int refreshInterval =
                ((Number) enrichmentPeriodicRefreshIntervalSeconds.getValue()).intValue();
            int refreshNicks =
                ((Number) enrichmentPeriodicRefreshNicksPerTick.getValue()).intValue();
            refreshSummary = String.format("Refresh %ds ×%d", refreshInterval, refreshNicks);
          } else {
            refreshSummary = "Refresh off";
          }

          enrichmentSummary.setText(
              String.format(
                  "USERHOST ≤%d/min • min %ds • cooldown %dm • up to %d nicks/cmd\n%s • %s",
                  maxPerMinute,
                  minInterval,
                  cooldownMinutes,
                  maxNicks,
                  whoisSummary,
                  refreshSummary));
        };

    Runnable updateAllSummaries =
        () -> {
          updateHostmaskSummary.run();
          updateEnrichmentSummary.run();
        };

    Runnable updateHostmaskState =
        () -> {
          boolean enabled = userhostEnabled.isSelected();
          LookupRatePreset preset = (LookupRatePreset) lookupPreset.getSelectedItem();
          boolean custom = preset == LookupRatePreset.CUSTOM;

          boolean showAdvanced = enabled && custom;
          hostmaskAdvanced.setVisible(showAdvanced);

          userhostMinIntervalSeconds.setEnabled(showAdvanced);
          userhostMaxPerMinute.setEnabled(showAdvanced);
          userhostNickCooldownMinutes.setEnabled(showAdvanced);
          userhostMaxNicksPerCommand.setEnabled(showAdvanced);

          updateHostmaskSummary.run();
        };

    Runnable updateEnrichmentState =
        () -> {
          boolean enabled = enrichmentEnabled.isSelected();
          LookupRatePreset preset = (LookupRatePreset) lookupPreset.getSelectedItem();
          boolean custom = preset == LookupRatePreset.CUSTOM;

          enrichmentWhoisFallbackEnabled.setEnabled(enabled);
          enrichmentPeriodicRefreshEnabled.setEnabled(enabled);

          boolean showAdvanced = enabled && custom;
          enrichmentAdvanced.setVisible(showAdvanced);

          enrichmentUserhostMinIntervalSeconds.setEnabled(showAdvanced);
          enrichmentUserhostMaxPerMinute.setEnabled(showAdvanced);
          enrichmentUserhostNickCooldownMinutes.setEnabled(showAdvanced);
          enrichmentUserhostMaxNicksPerCommand.setEnabled(showAdvanced);

          boolean whoisEnabled = showAdvanced && enrichmentWhoisFallbackEnabled.isSelected();
          enrichmentWhoisMinIntervalSeconds.setEnabled(whoisEnabled);
          enrichmentWhoisNickCooldownMinutes.setEnabled(whoisEnabled);

          boolean periodicEnabled = showAdvanced && enrichmentPeriodicRefreshEnabled.isSelected();
          enrichmentPeriodicRefreshIntervalSeconds.setEnabled(periodicEnabled);
          enrichmentPeriodicRefreshNicksPerTick.setEnabled(periodicEnabled);

          updateEnrichmentSummary.run();
        };

    userhostEnabled.addActionListener(
        e -> {
          updateHostmaskState.run();
          updateAllSummaries.run();
          hostmaskPanel.revalidate();
          hostmaskPanel.repaint();
          userLookupsPanel.revalidate();
          userLookupsPanel.repaint();
        });

    enrichmentEnabled.addActionListener(
        e -> {
          updateEnrichmentState.run();
          updateAllSummaries.run();
          enrichmentPanel.revalidate();
          enrichmentPanel.repaint();
          userLookupsPanel.revalidate();
          userLookupsPanel.repaint();
        });

    enrichmentWhoisFallbackEnabled.addActionListener(
        e -> {
          updateEnrichmentState.run();
          updateAllSummaries.run();
        });
    enrichmentPeriodicRefreshEnabled.addActionListener(
        e -> {
          updateEnrichmentState.run();
          updateAllSummaries.run();
        });

    lookupPreset.addActionListener(
        e -> {
          LookupRatePreset preset = (LookupRatePreset) lookupPreset.getSelectedItem();
          if (preset != null && preset != LookupRatePreset.CUSTOM) {
            applyLookupPreset.accept(preset);
          }
          updateLookupPresetHint.run();
          updateHostmaskState.run();
          updateEnrichmentState.run();
          updateAllSummaries.run();
          hostmaskPanel.revalidate();
          hostmaskPanel.repaint();
          enrichmentPanel.revalidate();
          enrichmentPanel.repaint();
          userLookupsPanel.revalidate();
          userLookupsPanel.repaint();
        });

    javax.swing.event.ChangeListener summaryChange = e -> updateAllSummaries.run();
    userhostMinIntervalSeconds.addChangeListener(summaryChange);
    userhostMaxPerMinute.addChangeListener(summaryChange);
    userhostNickCooldownMinutes.addChangeListener(summaryChange);
    userhostMaxNicksPerCommand.addChangeListener(summaryChange);
    enrichmentUserhostMinIntervalSeconds.addChangeListener(summaryChange);
    enrichmentUserhostMaxPerMinute.addChangeListener(summaryChange);
    enrichmentUserhostNickCooldownMinutes.addChangeListener(summaryChange);
    enrichmentUserhostMaxNicksPerCommand.addChangeListener(summaryChange);
    enrichmentWhoisMinIntervalSeconds.addChangeListener(summaryChange);
    enrichmentWhoisNickCooldownMinutes.addChangeListener(summaryChange);
    enrichmentPeriodicRefreshIntervalSeconds.addChangeListener(summaryChange);
    enrichmentPeriodicRefreshNicksPerTick.addChangeListener(summaryChange);

    JPanel enrichmentWhoisRow =
        new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    enrichmentWhoisRow.setOpaque(false);
    enrichmentWhoisRow.add(enrichmentWhoisFallbackEnabled, "growx");
    enrichmentWhoisRow.add(whoisHelp, "align right");

    JPanel enrichmentRefreshRow =
        new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]6[]", "[]"));
    enrichmentRefreshRow.setOpaque(false);
    enrichmentRefreshRow.add(enrichmentPeriodicRefreshEnabled, "growx");
    enrichmentRefreshRow.add(refreshHelp, "align right");

    hostmaskPanel.add(userhostEnabled, "growx");
    hostmaskPanel.add(hostmaskHelp, "align right, wrap");
    hostmaskPanel.add(hostmaskSummary, "span 2, growx, wmin 0, wrap");
    hostmaskPanel.add(hostmaskAdvanced, "span 2, growx, wrap, hidemode 3");

    enrichmentPanel.add(enrichmentEnabled, "growx");
    enrichmentPanel.add(enrichmentHelp, "align right, wrap");
    enrichmentPanel.add(enrichmentSummary, "span 2, growx, wmin 0, wrap");
    enrichmentPanel.add(enrichmentWhoisRow, "span 2, gapleft 18, growx, wrap");
    enrichmentPanel.add(enrichmentRefreshRow, "span 2, gapleft 18, growx, wrap");
    enrichmentPanel.add(enrichmentAdvanced, "span 2, growx, wrap, hidemode 3");
    hostmaskAdvanced.setVisible(false);
    enrichmentAdvanced.setVisible(false);
    updateHostmaskState.run();
    updateEnrichmentState.run();

    JTabbedPane lookupsTabs = new JTabbedPane();
    JPanel lookupsOverview =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]10[]"));
    lookupsOverview.setOpaque(false);
    lookupsOverview.add(userLookupsIntro, "growx, wmin 0, wrap");
    lookupsOverview.add(lookupPresetPanel, "growx, wmin 0, wrap");

    lookupsTabs.addTab("Overview", PreferencesDialog.padSubTab(lookupsOverview));
    lookupsTabs.addTab("Hostmask discovery", PreferencesDialog.padSubTab(hostmaskPanel));
    lookupsTabs.addTab("Roster enrichment", PreferencesDialog.padSubTab(enrichmentPanel));

    userLookupsPanel.add(lookupsTabs, "growx, wmin 0, wrap");

    UserhostControls userhostControls =
        new UserhostControls(
            userhostEnabled,
            userhostMinIntervalSeconds,
            userhostMaxPerMinute,
            userhostNickCooldownMinutes,
            userhostMaxNicksPerCommand);
    UserInfoEnrichmentControls enrichmentControls =
        new UserInfoEnrichmentControls(
            enrichmentEnabled,
            enrichmentUserhostMinIntervalSeconds,
            enrichmentUserhostMaxPerMinute,
            enrichmentUserhostNickCooldownMinutes,
            enrichmentUserhostMaxNicksPerCommand,
            enrichmentWhoisFallbackEnabled,
            enrichmentWhoisMinIntervalSeconds,
            enrichmentWhoisNickCooldownMinutes,
            enrichmentPeriodicRefreshEnabled,
            enrichmentPeriodicRefreshIntervalSeconds,
            enrichmentPeriodicRefreshNicksPerTick);

    return new UserLookupsPanelControls(
        userhostControls, enrichmentControls, monitorIsonPollIntervalSeconds, userLookupsPanel);
  }

  private enum LookupRatePreset {
    CONSERVATIVE("Conservative"),
    BALANCED("Balanced"),
    RAPID("Rapid"),
    CUSTOM("Custom");

    private final String label;

    LookupRatePreset(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static LookupRatePreset detectLookupRatePreset(UiSettings settings) {
    if (matchesLookupRatePreset(settings, LookupRatePreset.BALANCED)) {
      return LookupRatePreset.BALANCED;
    }
    if (matchesLookupRatePreset(settings, LookupRatePreset.CONSERVATIVE)) {
      return LookupRatePreset.CONSERVATIVE;
    }
    if (matchesLookupRatePreset(settings, LookupRatePreset.RAPID)) {
      return LookupRatePreset.RAPID;
    }
    return LookupRatePreset.CUSTOM;
  }

  private static boolean matchesLookupRatePreset(UiSettings settings, LookupRatePreset preset) {
    return switch (preset) {
      case CONSERVATIVE ->
          settings.userhostMinIntervalSeconds() == 10
              && settings.userhostMaxCommandsPerMinute() == 2
              && settings.userhostNickCooldownMinutes() == 60
              && settings.userhostMaxNicksPerCommand() == 5
              && settings.userInfoEnrichmentUserhostMinIntervalSeconds() == 30
              && settings.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 2
              && settings.userInfoEnrichmentUserhostNickCooldownMinutes() == 180
              && settings.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5
              && settings.userInfoEnrichmentWhoisMinIntervalSeconds() == 120
              && settings.userInfoEnrichmentWhoisNickCooldownMinutes() == 240
              && settings.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 600
              && settings.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 1;
      case BALANCED ->
          settings.userhostMinIntervalSeconds() == 5
              && settings.userhostMaxCommandsPerMinute() == 6
              && settings.userhostNickCooldownMinutes() == 30
              && settings.userhostMaxNicksPerCommand() == 5
              && settings.userInfoEnrichmentUserhostMinIntervalSeconds() == 15
              && settings.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 4
              && settings.userInfoEnrichmentUserhostNickCooldownMinutes() == 60
              && settings.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5
              && settings.userInfoEnrichmentWhoisMinIntervalSeconds() == 60
              && settings.userInfoEnrichmentWhoisNickCooldownMinutes() == 120
              && settings.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 300
              && settings.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 2;
      case RAPID ->
          settings.userhostMinIntervalSeconds() == 2
              && settings.userhostMaxCommandsPerMinute() == 15
              && settings.userhostNickCooldownMinutes() == 10
              && settings.userhostMaxNicksPerCommand() == 5
              && settings.userInfoEnrichmentUserhostMinIntervalSeconds() == 5
              && settings.userInfoEnrichmentUserhostMaxCommandsPerMinute() == 10
              && settings.userInfoEnrichmentUserhostNickCooldownMinutes() == 15
              && settings.userInfoEnrichmentUserhostMaxNicksPerCommand() == 5
              && settings.userInfoEnrichmentWhoisMinIntervalSeconds() == 15
              && settings.userInfoEnrichmentWhoisNickCooldownMinutes() == 30
              && settings.userInfoEnrichmentPeriodicRefreshIntervalSeconds() == 60
              && settings.userInfoEnrichmentPeriodicRefreshNicksPerTick() == 3;
      case CUSTOM -> false;
    };
  }
}

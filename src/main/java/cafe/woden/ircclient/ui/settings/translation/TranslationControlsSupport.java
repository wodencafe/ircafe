package cafe.woden.ircclient.ui.settings.translation;

import cafe.woden.ircclient.app.translation.MessageTranslationLanguageCatalogSupport;
import cafe.woden.ircclient.app.translation.MessageTranslationSettingsBus;
import cafe.woden.ircclient.app.translation.spi.MessageTranslationLanguage;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.ClientTranslationRuntimeConfigPort;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.ui.localization.UiMessages;
import cafe.woden.ircclient.ui.settings.PreferencesUiSupport;
import cafe.woden.ircclient.ui.settings.SettingsDocumentListener;
import cafe.woden.ircclient.ui.util.MigConstraints;
import cafe.woden.ircclient.ui.util.MigLayouts;
import cafe.woden.ircclient.ui.util.SwingClientProperties;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

public final class TranslationControlsSupport {
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private static final DataFlavor LANGUAGE_TRANSFER_FLAVOR =
      new DataFlavor(LanguageTransfer.class, "Translation language selection");

  private TranslationControlsSupport() {}

  public static TranslationControls buildControls(
      IrcProperties.Client.Translation settings, List<AutoCloseable> closeables) {
    return buildControls(settings, closeables, null);
  }

  public static TranslationControls buildControls(
      IrcProperties.Client.Translation settings,
      List<AutoCloseable> closeables,
      InstalledPluginsPort installedPlugins) {
    IrcProperties.Client.Translation effective = fallback(settings);
    TranslationServiceChoice initialChoice =
        TranslationServiceChoice.fromBackendId(effective.backendId());

    JCheckBox enabled = new JCheckBox(MESSAGES.text("preferences.translation.enabled"));
    enabled.setSelected(effective.enabled());

    JComboBox<IrcProperties.Client.Translation.Mode> mode =
        new JComboBox<>(IrcProperties.Client.Translation.Mode.values());
    mode.setRenderer(modeRenderer());
    mode.setSelectedItem(effective.mode());
    PreferencesUiSupport.decorateComboBoxSelection(mode, closeables);

    JComboBox<TranslationServiceChoice> backend =
        new JComboBox<>(TranslationServiceChoice.values());
    backend.setRenderer(backendRenderer());
    backend.setSelectedItem(initialChoice);
    PreferencesUiSupport.decorateComboBoxSelection(backend, closeables);

    JTextField endpoint = new JTextField(endpointFor(effective, initialChoice));
    PreferencesUiSupport.placeholder(endpoint, initialChoice.defaultEndpoint());

    JPasswordField apiKey = new JPasswordField(Objects.toString(effective.apiKey(), ""));
    PreferencesUiSupport.placeholder(
        apiKey, MESSAGES.text("preferences.translation.apiKey.placeholder"));
    apiKey.putClientProperty(SwingClientProperties.PASSWORD_FIELD_SHOW_REVEAL_BUTTON, true);
    apiKey.putClientProperty(FlatClientProperties.STYLE, "showRevealButton:true;");
    JButton clearApiKey = new JButton(MESSAGES.text("preferences.translation.button.clearApiKey"));
    clearApiKey.addActionListener(event -> apiKey.setText(""));

    JCheckBox translateUnknownMessages =
        new JCheckBox(MESSAGES.text("preferences.translation.translateUnknown"));
    translateUnknownMessages.setSelected(effective.translateUnknownMessages());

    JCheckBox detectAllLanguages =
        new JCheckBox(MESSAGES.text("preferences.translation.detectAllLanguages"));
    detectAllLanguages.setSelected(effective.detectAllLanguages());
    List<MessageTranslationLanguage> catalog =
        MessageTranslationLanguageCatalogSupport.commonTargets(installedPlugins);
    DefaultListModel<MessageTranslationLanguage> enabledDetectionLanguageModel =
        languageModel(enabledDetectionLanguages(effective, catalog));
    DefaultListModel<MessageTranslationLanguage> disabledDetectionLanguageModel =
        languageModel(disabledDetectionLanguages(catalog, enabledDetectionLanguageModel));
    Runnable[] afterLanguageTransfer = new Runnable[1];
    JList<MessageTranslationLanguage> disabledDetectionLanguages =
        languageList(disabledDetectionLanguageModel, afterLanguageTransfer);
    JList<MessageTranslationLanguage> enabledDetectionLanguages =
        languageList(enabledDetectionLanguageModel, afterLanguageTransfer);
    JComboBox<TranslationLanguageChoice> sourceLanguage =
        new JComboBox<>(
            sourceLanguageModel(
                availableDetectionLanguages(
                    detectAllLanguages.isSelected(), enabledDetectionLanguageModel, catalog)));
    selectLanguage(sourceLanguage, firstNonBlank(effective.sourceLanguage(), "auto"));
    PreferencesUiSupport.decorateComboBoxSelection(sourceLanguage, closeables);
    JComboBox<TranslationLanguageChoice> targetLanguage =
        new JComboBox<>(
            targetLanguageModel(
                availableDetectionLanguages(
                    detectAllLanguages.isSelected(), enabledDetectionLanguageModel, catalog)));
    selectLanguage(targetLanguage, effective.targetLanguage());
    PreferencesUiSupport.decorateComboBoxSelection(targetLanguage, closeables);
    JButton addDetectionLanguage =
        new JButton(MESSAGES.text("preferences.translation.button.addDetectionLanguage"));
    JButton removeDetectionLanguage =
        new JButton(MESSAGES.text("preferences.translation.button.removeDetectionLanguage"));
    JButton addAllDetectionLanguages =
        new JButton(MESSAGES.text("preferences.translation.button.addAllDetectionLanguages"));
    JButton removeAllDetectionLanguages =
        new JButton(MESSAGES.text("preferences.translation.button.removeAllDetectionLanguages"));

    JSpinner requestTimeoutSeconds =
        PreferencesUiSupport.numberSpinner(
            (int) Math.max(1L, effective.requestTimeoutMs() / 1000L), 1, 120, 1, closeables);
    JSpinner maxRequestChars =
        PreferencesUiSupport.numberSpinner(
            effective.maxRequestChars(), 1, 128 * 1024, 100, closeables);
    JSpinner maxConcurrentRequests =
        PreferencesUiSupport.numberSpinner(effective.maxConcurrentRequests(), 1, 16, 1, closeables);

    JPanel panel = new JPanel(MigLayouts.singleColumnFill(12, "[]8[]"));
    panel.add(
        PreferencesUiSupport.tabTitle(MESSAGES.text("preferences.translation.title")),
        MigConstraints.growXMinWidth0Wrap());
    panel.add(enabled, MigConstraints.growXMinWidth0Wrap());

    JPanel service =
        PreferencesUiSupport.captionPanel(
            MESSAGES.text("preferences.translation.section.service"), MigLayouts.twoColumnForm(8));
    service.add(new JLabel(MESSAGES.text("preferences.translation.field.mode")));
    service.add(mode, MigConstraints.growXMinWidth0());
    service.add(new JLabel(MESSAGES.text("preferences.translation.field.backend")));
    service.add(backend, MigConstraints.growXMinWidth0());
    service.add(new JLabel(MESSAGES.text("preferences.translation.field.endpoint")));
    service.add(endpoint, MigConstraints.growXMinWidth0());
    service.add(new JLabel(MESSAGES.text("preferences.translation.field.apiKey")));
    JPanel apiKeyRow = new JPanel(MigLayouts.fillXGrowTrailing(6));
    apiKeyRow.setOpaque(false);
    apiKeyRow.add(apiKey, MigConstraints.growXPushXMinWidth0());
    apiKeyRow.add(clearApiKey);
    service.add(apiKeyRow, MigConstraints.growXMinWidth0());
    panel.add(service, MigConstraints.growXMinWidth0Wrap());

    JPanel languages =
        PreferencesUiSupport.captionPanel(
            MESSAGES.text("preferences.translation.section.languages"),
            MigLayouts.twoColumnForm(8));
    languages.add(new JLabel(MESSAGES.text("preferences.translation.field.source")));
    languages.add(sourceLanguage, MigConstraints.growXMinWidth0Wrap());
    languages.add(new JLabel(MESSAGES.text("preferences.translation.field.target")));
    languages.add(targetLanguage, MigConstraints.growXMinWidth0Wrap());
    languages.add(new JLabel(""));
    languages.add(translateUnknownMessages, MigConstraints.growXMinWidth0());
    panel.add(languages, MigConstraints.growXMinWidth0Wrap());

    JPanel detectionLanguages =
        PreferencesUiSupport.captionPanel(
            MESSAGES.text("preferences.translation.section.languageDetection"),
            MigLayouts.singleColumnFill(0));
    detectionLanguages.add(detectAllLanguages, MigConstraints.growXMinWidth0Wrap());
    detectionLanguages.add(
        PreferencesUiSupport.subtleInfoTextWith(
            MESSAGES.text("preferences.translation.languageDetection.help")),
        MigConstraints.growXMinWidth0Wrap());
    JPanel languageLists =
        new JPanel(MigLayouts.fillX("[grow,fill]8[]8[grow,fill]", "[]4[grow,fill]"));
    languageLists.setOpaque(false);
    languageLists.add(
        new JLabel(MESSAGES.text("preferences.translation.languageDetection.disabled")));
    languageLists.add(new JLabel(""));
    languageLists.add(
        new JLabel(MESSAGES.text("preferences.translation.languageDetection.enabled")),
        MigConstraints.wrap());
    languageLists.add(
        languageScroll(disabledDetectionLanguages), MigConstraints.growPushHeight(150));
    JPanel languageButtons = new JPanel(MigLayouts.singleColumn(0));
    languageButtons.setOpaque(false);
    languageButtons.add(addDetectionLanguage, MigConstraints.growXMinWidth0Wrap());
    languageButtons.add(removeDetectionLanguage, MigConstraints.growXMinWidth0Wrap());
    languageButtons.add(addAllDetectionLanguages, MigConstraints.growXMinWidth0Wrap());
    languageButtons.add(removeAllDetectionLanguages, MigConstraints.growXMinWidth0());
    languageLists.add(languageButtons, MigConstraints.alignYTop());
    languageLists.add(
        languageScroll(enabledDetectionLanguages), MigConstraints.growPushHeightWrap(150));
    detectionLanguages.add(languageLists, MigConstraints.growXMinWidth0Wrap());
    panel.add(detectionLanguages, MigConstraints.growXMinWidth0Wrap());

    JPanel limits =
        PreferencesUiSupport.captionPanel(
            MESSAGES.text("preferences.translation.section.limits"), MigLayouts.twoColumnForm(8));
    limits.add(new JLabel(MESSAGES.text("preferences.translation.field.requestTimeoutSeconds")));
    limits.add(requestTimeoutSeconds, MigConstraints.widthWrap(110));
    limits.add(new JLabel(MESSAGES.text("preferences.translation.field.maxRequestChars")));
    limits.add(maxRequestChars, MigConstraints.widthWrap(130));
    limits.add(new JLabel(MESSAGES.text("preferences.translation.field.maxConcurrentRequests")));
    limits.add(maxConcurrentRequests, MigConstraints.width(110));
    panel.add(limits, MigConstraints.growXMinWidth0());

    Runnable syncLanguageCombos =
        () ->
            syncLanguageCombos(
                sourceLanguage,
                targetLanguage,
                availableDetectionLanguages(
                    detectAllLanguages.isSelected(), enabledDetectionLanguageModel, catalog));

    Runnable refresh =
        () -> {
          TranslationServiceChoice choice =
              PreferencesUiSupport.selectedComboItem(
                  backend, TranslationServiceChoice.class, TranslationServiceChoice.DEEPL);
          boolean active = enabled.isSelected();
          mode.setEnabled(active);
          backend.setEnabled(active);
          endpoint.setEnabled(active);
          apiKey.setEnabled(active);
          clearApiKey.setEnabled(active);
          sourceLanguage.setEnabled(active);
          targetLanguage.setEnabled(active);
          translateUnknownMessages.setEnabled(active);
          boolean detectionLanguageControlsActive = active && !detectAllLanguages.isSelected();
          detectAllLanguages.setEnabled(active);
          disabledDetectionLanguages.setEnabled(detectionLanguageControlsActive);
          enabledDetectionLanguages.setEnabled(detectionLanguageControlsActive);
          addDetectionLanguage.setEnabled(
              detectionLanguageControlsActive && !disabledDetectionLanguages.isSelectionEmpty());
          removeDetectionLanguage.setEnabled(
              detectionLanguageControlsActive && !enabledDetectionLanguages.isSelectionEmpty());
          addAllDetectionLanguages.setEnabled(
              detectionLanguageControlsActive && !disabledDetectionLanguageModel.isEmpty());
          removeAllDetectionLanguages.setEnabled(
              detectionLanguageControlsActive && !enabledDetectionLanguageModel.isEmpty());
          requestTimeoutSeconds.setEnabled(active);
          maxRequestChars.setEnabled(active);
          maxConcurrentRequests.setEnabled(active);
          validate(active, choice, endpoint, apiKey, targetLanguage);
        };
    afterLanguageTransfer[0] =
        () -> {
          syncLanguageCombos.run();
          refresh.run();
        };

    enabled.addActionListener(event -> refresh.run());
    backend.addActionListener(
        event -> {
          TranslationServiceChoice choice =
              PreferencesUiSupport.selectedComboItem(
                  backend, TranslationServiceChoice.class, TranslationServiceChoice.DEEPL);
          String currentEndpoint = PreferencesUiSupport.trimmedText(endpoint);
          if (currentEndpoint.isBlank() || isKnownDefaultEndpoint(currentEndpoint)) {
            endpoint.setText(choice.defaultEndpoint());
          }
          refresh.run();
        });
    endpoint.getDocument().addDocumentListener(new SettingsDocumentListener(refresh));
    apiKey.getDocument().addDocumentListener(new SettingsDocumentListener(refresh));
    disabledDetectionLanguages.addListSelectionListener(event -> refresh.run());
    enabledDetectionLanguages.addListSelectionListener(event -> refresh.run());
    detectAllLanguages.addActionListener(
        event -> {
          if (detectAllLanguages.isSelected()) {
            moveAllLanguages(disabledDetectionLanguageModel, enabledDetectionLanguageModel);
          }
          syncLanguageCombos.run();
          refresh.run();
        });
    addDetectionLanguage.addActionListener(
        event -> {
          moveSelectedLanguages(disabledDetectionLanguages, enabledDetectionLanguageModel);
          sortLanguageModel(enabledDetectionLanguageModel);
          syncLanguageCombos.run();
          refresh.run();
        });
    removeDetectionLanguage.addActionListener(
        event -> {
          moveSelectedLanguages(enabledDetectionLanguages, disabledDetectionLanguageModel);
          sortLanguageModel(disabledDetectionLanguageModel);
          syncLanguageCombos.run();
          refresh.run();
        });
    addAllDetectionLanguages.addActionListener(
        event -> {
          moveAllLanguages(disabledDetectionLanguageModel, enabledDetectionLanguageModel);
          syncLanguageCombos.run();
          refresh.run();
        });
    removeAllDetectionLanguages.addActionListener(
        event -> {
          moveAllLanguages(enabledDetectionLanguageModel, disabledDetectionLanguageModel);
          syncLanguageCombos.run();
          refresh.run();
        });
    refresh.run();

    return new TranslationControls(
        enabled,
        mode,
        backend,
        endpoint,
        apiKey,
        sourceLanguage,
        targetLanguage,
        translateUnknownMessages,
        detectAllLanguages,
        disabledDetectionLanguages,
        enabledDetectionLanguages,
        requestTimeoutSeconds,
        maxRequestChars,
        maxConcurrentRequests,
        panel);
  }

  public static IrcProperties.Client.Translation readSettings(TranslationControls controls) {
    TranslationServiceChoice backend =
        PreferencesUiSupport.selectedComboItem(
            controls.backend(), TranslationServiceChoice.class, TranslationServiceChoice.DEEPL);
    IrcProperties.Client.Translation.Mode mode =
        PreferencesUiSupport.selectedComboItem(
            controls.mode(),
            IrcProperties.Client.Translation.Mode.class,
            IrcProperties.Client.Translation.Mode.AUTO);
    boolean enabled = controls.enabled().isSelected();
    String endpoint = PreferencesUiSupport.trimmedText(controls.endpoint());
    String apiKey = PreferencesUiSupport.trimmedPasswordText(controls.apiKey());
    TranslationLanguageChoice sourceChoice =
        PreferencesUiSupport.selectedComboItem(
            controls.sourceLanguage(),
            TranslationLanguageChoice.class,
            TranslationLanguageChoice.AUTO);
    String sourceLanguage = sourceChoice.code();
    if (sourceLanguage.isBlank()) {
      sourceLanguage = "auto";
    }
    TranslationLanguageChoice targetChoice =
        PreferencesUiSupport.selectedComboItem(
            controls.targetLanguage(),
            TranslationLanguageChoice.class,
            TranslationLanguageChoice.NONE);
    String targetLanguage = targetChoice.code();
    boolean detectAllLanguages = controls.detectAllLanguages().isSelected();
    List<String> detectionLanguages =
        detectAllLanguages ? List.of() : languageCodes(controls.enabledDetectionLanguages());
    if (enabled) {
      validateSettings(
          mode, backend, endpoint, apiKey, targetLanguage, detectAllLanguages, detectionLanguages);
    }
    return new IrcProperties.Client.Translation(
        enabled,
        mode,
        enabled ? backend.backendId() : "",
        endpoint,
        apiKey,
        sourceLanguage,
        targetLanguage,
        controls.translateUnknownMessages().isSelected(),
        detectAllLanguages,
        detectionLanguages,
        IrcProperties.Client.Translation.DisplayMode.BELOW_ORIGINAL,
        Math.max(1, PreferencesUiSupport.spinnerInt(controls.requestTimeoutSeconds())) * 1000L,
        PreferencesUiSupport.spinnerInt(controls.maxRequestChars()),
        PreferencesUiSupport.spinnerInt(controls.maxConcurrentRequests()));
  }

  public static void rememberSettings(
      ClientTranslationRuntimeConfigPort runtimeConfig,
      MessageTranslationSettingsBus settingsBus,
      IrcProperties.Client.Translation settings) {
    if (settingsBus != null) {
      settingsBus.set(settings);
    }
    if (runtimeConfig != null) {
      runtimeConfig.rememberClientTranslation(settings);
    }
  }

  private static void validate(
      boolean enabled,
      TranslationServiceChoice backend,
      JTextField endpoint,
      JPasswordField apiKey,
      JComboBox<TranslationLanguageChoice> targetLanguage) {
    endpoint.putClientProperty(FlatClientProperties.OUTLINE, null);
    apiKey.putClientProperty(FlatClientProperties.OUTLINE, null);
    targetLanguage.putClientProperty(FlatClientProperties.OUTLINE, null);
    if (!enabled) {
      return;
    }
    if (!validEndpoint(PreferencesUiSupport.trimmedText(endpoint))) {
      endpoint.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
    }
    if (backend.apiKeyRequired() && PreferencesUiSupport.trimmedPasswordText(apiKey).isBlank()) {
      apiKey.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
    }
    TranslationLanguageChoice targetChoice =
        PreferencesUiSupport.selectedComboItem(
            targetLanguage, TranslationLanguageChoice.class, TranslationLanguageChoice.NONE);
    if (targetChoice.code().isBlank()) {
      targetLanguage.putClientProperty(
          FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
    }
  }

  private static void validateSettings(
      IrcProperties.Client.Translation.Mode mode,
      TranslationServiceChoice backend,
      String endpoint,
      String apiKey,
      String targetLanguage,
      boolean detectAllLanguages,
      List<String> detectionLanguages) {
    if (!validEndpoint(endpoint)) {
      throw new TranslationSettingsException(
          MESSAGES.text("preferences.translation.validation.title"),
          MESSAGES.text("preferences.translation.validation.endpoint"));
    }
    if (backend.apiKeyRequired() && apiKey.isBlank()) {
      throw new TranslationSettingsException(
          MESSAGES.text("preferences.translation.validation.title"),
          MESSAGES.text(
              "preferences.translation.validation.apiKeyRequired", backendLabel(backend)));
    }
    if (targetLanguage.isBlank()) {
      throw new TranslationSettingsException(
          MESSAGES.text("preferences.translation.validation.title"),
          MESSAGES.text("preferences.translation.validation.targetRequired"));
    }
    if (mode == IrcProperties.Client.Translation.Mode.AUTO
        && !detectAllLanguages
        && detectionLanguages.size() < 2) {
      throw new TranslationSettingsException(
          MESSAGES.text("preferences.translation.validation.title"),
          MESSAGES.text("preferences.translation.validation.detectionLanguageCount"));
    }
  }

  private static List<MessageTranslationLanguage> enabledDetectionLanguages(
      IrcProperties.Client.Translation settings, List<MessageTranslationLanguage> catalog) {
    if (settings.detectAllLanguages()) {
      return List.copyOf(catalog);
    }
    Map<String, MessageTranslationLanguage> byCode = languageByCode(catalog);
    return settings.detectionLanguages().stream()
        .map(byCode::get)
        .filter(Objects::nonNull)
        .toList();
  }

  private static List<MessageTranslationLanguage> disabledDetectionLanguages(
      List<MessageTranslationLanguage> catalog,
      DefaultListModel<MessageTranslationLanguage> enabledLanguages) {
    List<MessageTranslationLanguage> disabled = new ArrayList<>();
    for (MessageTranslationLanguage language : catalog) {
      if (!modelContains(enabledLanguages, language)) {
        disabled.add(language);
      }
    }
    return disabled;
  }

  private static DefaultListModel<MessageTranslationLanguage> languageModel(
      List<MessageTranslationLanguage> languages) {
    DefaultListModel<MessageTranslationLanguage> model = new DefaultListModel<>();
    for (MessageTranslationLanguage language : languages) {
      model.addElement(language);
    }
    sortLanguageModel(model);
    return model;
  }

  private static DefaultListCellRenderer modeRenderer() {
    return new DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label =
            (JLabel)
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof IrcProperties.Client.Translation.Mode mode) {
          label.setText(modeLabel(mode));
        }
        return label;
      }
    };
  }

  private static DefaultListCellRenderer backendRenderer() {
    return new DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label =
            (JLabel)
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof TranslationServiceChoice choice) {
          label.setText(backendLabel(choice));
        }
        return label;
      }
    };
  }

  private static String modeLabel(IrcProperties.Client.Translation.Mode mode) {
    return switch (mode) {
      case AUTO -> MESSAGES.text("preferences.translation.mode.auto");
      case MANUAL -> MESSAGES.text("preferences.translation.mode.manual");
    };
  }

  private static String backendLabel(TranslationServiceChoice choice) {
    return switch (choice) {
      case DEEPL -> MESSAGES.text("preferences.translation.service.deepl");
      case LIBRETRANSLATE -> MESSAGES.text("preferences.translation.service.libreTranslate");
      case GOOGLE_WEB -> MESSAGES.text("preferences.translation.service.googleWeb");
    };
  }

  private static TranslationLanguageChoice autoLanguageChoice() {
    return new TranslationLanguageChoice(
        "auto", MESSAGES.text("preferences.translation.language.autoDetect"));
  }

  private static TranslationLanguageChoice noneLanguageChoice() {
    return new TranslationLanguageChoice(
        "", MESSAGES.text("preferences.translation.language.select"));
  }

  private static DefaultComboBoxModel<TranslationLanguageChoice> sourceLanguageModel(
      List<MessageTranslationLanguage> languages) {
    DefaultComboBoxModel<TranslationLanguageChoice> model = new DefaultComboBoxModel<>();
    model.addElement(autoLanguageChoice());
    for (MessageTranslationLanguage language : sortedLanguages(languages)) {
      model.addElement(TranslationLanguageChoice.from(language));
    }
    return model;
  }

  private static DefaultComboBoxModel<TranslationLanguageChoice> targetLanguageModel(
      List<MessageTranslationLanguage> languages) {
    DefaultComboBoxModel<TranslationLanguageChoice> model = new DefaultComboBoxModel<>();
    model.addElement(noneLanguageChoice());
    for (MessageTranslationLanguage language : sortedLanguages(languages)) {
      model.addElement(TranslationLanguageChoice.from(language));
    }
    return model;
  }

  private static void syncLanguageCombos(
      JComboBox<TranslationLanguageChoice> sourceLanguage,
      JComboBox<TranslationLanguageChoice> targetLanguage,
      List<MessageTranslationLanguage> languages) {
    String selectedSource = selectedLanguageCode(sourceLanguage);
    String selectedTarget = selectedLanguageCode(targetLanguage);
    sourceLanguage.setModel(sourceLanguageModel(languages));
    targetLanguage.setModel(targetLanguageModel(languages));
    selectLanguage(sourceLanguage, selectedSource.isBlank() ? "auto" : selectedSource);
    selectLanguage(targetLanguage, selectedTarget);
  }

  private static List<MessageTranslationLanguage> availableDetectionLanguages(
      boolean detectAllLanguages,
      DefaultListModel<MessageTranslationLanguage> enabledLanguages,
      List<MessageTranslationLanguage> catalog) {
    return detectAllLanguages ? List.copyOf(catalog) : elements(enabledLanguages);
  }

  private static void selectLanguage(
      JComboBox<TranslationLanguageChoice> combo, String languageCode) {
    String normalized = normalizeLanguageCode(languageCode);
    for (int i = 0; i < combo.getItemCount(); i++) {
      TranslationLanguageChoice item = combo.getItemAt(i);
      if (item.code().equals(normalized)) {
        combo.setSelectedIndex(i);
        return;
      }
    }
    combo.setSelectedIndex(0);
  }

  private static String selectedLanguageCode(JComboBox<TranslationLanguageChoice> combo) {
    TranslationLanguageChoice choice =
        PreferencesUiSupport.selectedComboItem(combo, TranslationLanguageChoice.class, null);
    return choice != null ? choice.code() : "";
  }

  private static List<MessageTranslationLanguage> sortedLanguages(
      List<MessageTranslationLanguage> languages) {
    return languages.stream()
        .sorted(java.util.Comparator.comparing(MessageTranslationLanguage::label))
        .toList();
  }

  private static JList<MessageTranslationLanguage> languageList(
      DefaultListModel<MessageTranslationLanguage> model, Runnable[] afterTransfer) {
    JList<MessageTranslationLanguage> list = new JList<>(model);
    list.setVisibleRowCount(7);
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setCellRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MessageTranslationLanguage language) {
              setText(language.label() + " (" + language.code() + ")");
            }
            return this;
          }
        });
    list.setDropMode(DropMode.INSERT);
    list.setTransferHandler(new LanguageListTransferHandler(afterTransfer));
    if (!GraphicsEnvironment.isHeadless()) {
      list.setDragEnabled(true);
    }
    return list;
  }

  private static JScrollPane languageScroll(JList<MessageTranslationLanguage> list) {
    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(220, 150));
    return scroll;
  }

  private static void moveSelectedLanguages(
      JList<MessageTranslationLanguage> source,
      DefaultListModel<MessageTranslationLanguage> targetModel) {
    DefaultListModel<MessageTranslationLanguage> sourceModel = languageModel(source);
    List<MessageTranslationLanguage> selected = new ArrayList<>(source.getSelectedValuesList());
    for (MessageTranslationLanguage language : selected) {
      sourceModel.removeElement(language);
      if (!modelContains(targetModel, language)) {
        targetModel.addElement(language);
      }
    }
  }

  private static void moveAllLanguages(
      DefaultListModel<MessageTranslationLanguage> sourceModel,
      DefaultListModel<MessageTranslationLanguage> targetModel) {
    List<MessageTranslationLanguage> moving = elements(sourceModel);
    sourceModel.clear();
    for (MessageTranslationLanguage language : moving) {
      if (!modelContains(targetModel, language)) {
        targetModel.addElement(language);
      }
    }
    sortLanguageModel(targetModel);
  }

  private static void sortLanguageModel(DefaultListModel<MessageTranslationLanguage> model) {
    List<MessageTranslationLanguage> sorted =
        elements(model).stream()
            .sorted(java.util.Comparator.comparing(MessageTranslationLanguage::label))
            .toList();
    model.clear();
    for (MessageTranslationLanguage language : sorted) {
      model.addElement(language);
    }
  }

  private static List<String> languageCodes(JList<MessageTranslationLanguage> list) {
    return elements(languageModel(list)).stream().map(MessageTranslationLanguage::code).toList();
  }

  private static String normalizeLanguageCode(String languageCode) {
    return Objects.toString(languageCode, "").trim().toLowerCase(Locale.ROOT);
  }

  private static List<MessageTranslationLanguage> elements(
      DefaultListModel<MessageTranslationLanguage> model) {
    List<MessageTranslationLanguage> result = new ArrayList<>(model.getSize());
    for (int i = 0; i < model.getSize(); i++) {
      result.add(model.getElementAt(i));
    }
    return result;
  }

  private static DefaultListModel<MessageTranslationLanguage> languageModel(
      JList<MessageTranslationLanguage> list) {
    return (DefaultListModel<MessageTranslationLanguage>) list.getModel();
  }

  private static boolean modelContains(
      DefaultListModel<MessageTranslationLanguage> model, MessageTranslationLanguage language) {
    return model.indexOf(language) >= 0;
  }

  private static Map<String, MessageTranslationLanguage> languageByCode(
      List<MessageTranslationLanguage> languages) {
    Map<String, MessageTranslationLanguage> byCode = new LinkedHashMap<>();
    for (MessageTranslationLanguage language : languages) {
      byCode.put(language.code(), language);
    }
    return byCode;
  }

  private static boolean validEndpoint(String endpoint) {
    try {
      URI uri = URI.create(Objects.toString(endpoint, "").trim());
      String scheme = Objects.toString(uri.getScheme(), "").trim().toLowerCase(Locale.ROOT);
      return ("http".equals(scheme) || "https".equals(scheme))
          && uri.getHost() != null
          && !uri.getHost().isBlank();
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean isKnownDefaultEndpoint(String endpoint) {
    for (TranslationServiceChoice choice : TranslationServiceChoice.values()) {
      if (choice.defaultEndpoint().equals(endpoint)) {
        return true;
      }
    }
    return false;
  }

  private static String endpointFor(
      IrcProperties.Client.Translation settings, TranslationServiceChoice choice) {
    String endpoint = Objects.toString(settings.endpoint(), "").trim();
    return endpoint.isBlank() ? choice.defaultEndpoint() : endpoint;
  }

  private static String firstNonBlank(String preferred, String fallback) {
    String value = Objects.toString(preferred, "").trim();
    return value.isBlank() ? Objects.toString(fallback, "").trim() : value;
  }

  private static IrcProperties.Client.Translation fallback(
      IrcProperties.Client.Translation settings) {
    if (settings != null) {
      return settings;
    }
    return new IrcProperties.Client.Translation(
        false,
        IrcProperties.Client.Translation.Mode.AUTO,
        "",
        "",
        "",
        "auto",
        "",
        null,
        10_000,
        4_000,
        2);
  }

  public static final class TranslationSettingsException extends IllegalArgumentException {
    private final String title;

    private TranslationSettingsException(String title, String message) {
      super(message);
      this.title = title;
    }

    public String title() {
      return title;
    }
  }

  private record LanguageTransfer(
      DefaultListModel<MessageTranslationLanguage> sourceModel,
      List<MessageTranslationLanguage> languages) {}

  private static final class LanguageListTransferHandler extends TransferHandler {
    private final Runnable[] afterTransfer;

    private LanguageListTransferHandler(Runnable[] afterTransfer) {
      this.afterTransfer = afterTransfer;
    }

    @Override
    public int getSourceActions(javax.swing.JComponent component) {
      return MOVE;
    }

    @Override
    protected Transferable createTransferable(javax.swing.JComponent component) {
      if (!(component instanceof JList<?> list)
          || !(list.getModel() instanceof DefaultListModel<?> rawModel)) {
        return null;
      }
      @SuppressWarnings("unchecked")
      DefaultListModel<MessageTranslationLanguage> model =
          (DefaultListModel<MessageTranslationLanguage>) rawModel;
      List<MessageTranslationLanguage> selected =
          list.getSelectedValuesList().stream()
              .filter(MessageTranslationLanguage.class::isInstance)
              .map(MessageTranslationLanguage.class::cast)
              .toList();
      return new LanguageTransferable(new LanguageTransfer(model, selected));
    }

    @Override
    public boolean canImport(TransferSupport support) {
      return support.isDataFlavorSupported(LANGUAGE_TRANSFER_FLAVOR)
          && support.getComponent() instanceof JList<?>;
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      try {
        LanguageTransfer transfer =
            (LanguageTransfer) support.getTransferable().getTransferData(LANGUAGE_TRANSFER_FLAVOR);
        if (!(support.getComponent() instanceof JList<?> target)
            || !(target.getModel() instanceof DefaultListModel<?> rawTargetModel)) {
          return false;
        }
        @SuppressWarnings("unchecked")
        DefaultListModel<MessageTranslationLanguage> targetModel =
            (DefaultListModel<MessageTranslationLanguage>) rawTargetModel;
        for (MessageTranslationLanguage language : transfer.languages()) {
          transfer.sourceModel().removeElement(language);
          if (!modelContains(targetModel, language)) {
            targetModel.addElement(language);
          }
        }
        sortLanguageModel(transfer.sourceModel());
        sortLanguageModel(targetModel);
        if (afterTransfer != null && afterTransfer.length > 0 && afterTransfer[0] != null) {
          afterTransfer[0].run();
        }
        return true;
      } catch (IOException | UnsupportedFlavorException ex) {
        return false;
      }
    }
  }

  private record LanguageTransferable(LanguageTransfer transfer) implements Transferable {
    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[] {LANGUAGE_TRANSFER_FLAVOR};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return LANGUAGE_TRANSFER_FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (!isDataFlavorSupported(flavor)) {
        throw new UnsupportedFlavorException(flavor);
      }
      return transfer;
    }
  }
}

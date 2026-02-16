package cafe.woden.ircclient.ui.filter;

import cafe.woden.ircclient.logging.model.LogKind;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Simple modal dialog for creating/editing a filter rule. */
public final class FilterRuleEntryDialog {

  private FilterRuleEntryDialog() {
  }

  public static Optional<FilterRule> open(Window owner,
                                         String title,
                                         FilterRule seed,
                                         Set<String> reservedNameKeys,
                                         String suggestedScope) {
    if (!SwingUtilities.isEventDispatchThread()) {
      final Optional<FilterRule>[] box = new Optional[]{Optional.empty()};
      try {
        SwingUtilities.invokeAndWait(() -> box[0] = open(owner, title, seed, reservedNameKeys, suggestedScope));
      } catch (Exception ignored) {
      }
      return box[0];
    }

    JDialog dlg = new JDialog(owner, Objects.toString(title, "Filter Rule"), JDialog.ModalityType.APPLICATION_MODAL);
    dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dlg.setLayout(new BorderLayout(10, 10));
    ((JPanel) dlg.getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JTextField name = new JTextField(24);
    JTextField scope = new JTextField(24);
    JComboBox<FilterDirection> direction = new JComboBox<>(FilterDirection.values());
    JCheckBox enabled = new JCheckBox("Enabled");

    // Kinds
    JCheckBox kindChat = new JCheckBox("CHAT");
    JCheckBox kindAction = new JCheckBox("ACTION");
    JCheckBox kindNotice = new JCheckBox("NOTICE");
    JCheckBox kindStatus = new JCheckBox("STATUS");
    JCheckBox kindError = new JCheckBox("ERROR");
    JCheckBox kindPresence = new JCheckBox("PRESENCE");
    JCheckBox kindSpoiler = new JCheckBox("SPOILER");

    JTextField fromGlobs = new JTextField(24);
    JTextField tags = new JTextField(24);

    JTextField regex = new JTextField(24);
    JCheckBox reI = new JCheckBox("i (case-insensitive)");
    JCheckBox reM = new JCheckBox("m (multiline)");
    JCheckBox reS = new JCheckBox("s (dotall)");
    reI.setSelected(true); // user preference: default case-insensitive

    name.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Unique rule name");
    scope.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Scope pattern (e.g. libera/#llamas, */status, *)");
    fromGlobs.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Optional from: globs (comma/space separated)");
    tags.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Optional tags (e.g. irc_in+irc_privmsg, !irc_notice, re:^irc_)");
    regex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Optional text regex");

    if (seed != null) {
      name.setText(Objects.toString(seed.name(), ""));
      enabled.setSelected(seed.enabled());
      scope.setText(Objects.toString(seed.scopePattern(), "*"));
      direction.setSelectedItem(seed.direction() != null ? seed.direction() : FilterDirection.ANY);

      EnumSet<LogKind> k = seed.kinds();
      if (k != null) {
        kindChat.setSelected(k.contains(LogKind.CHAT));
        kindAction.setSelected(k.contains(LogKind.ACTION));
        kindNotice.setSelected(k.contains(LogKind.NOTICE));
        kindStatus.setSelected(k.contains(LogKind.STATUS));
        kindError.setSelected(k.contains(LogKind.ERROR));
        kindPresence.setSelected(k.contains(LogKind.PRESENCE));
        kindSpoiler.setSelected(k.contains(LogKind.SPOILER));
      }

      if (seed.fromNickGlobs() != null && !seed.fromNickGlobs().isEmpty()) {
        fromGlobs.setText(String.join(", ", seed.fromNickGlobs()));
      }

      if (seed.tags() != null && !seed.tags().isEmpty()) {
        tags.setText(Objects.toString(seed.tags().expr(), ""));
      }

      RegexSpec rs = seed.textRegex();
      if (rs != null && !Objects.toString(rs.pattern(), "").isBlank()) {
        regex.setText(rs.pattern());
      }
      if (rs != null && rs.flags() != null) {
        reI.setSelected(rs.flags().contains(RegexFlag.I));
        reM.setSelected(rs.flags().contains(RegexFlag.M));
        reS.setSelected(rs.flags().contains(RegexFlag.S));
      }
    } else {
      enabled.setSelected(true);
      scope.setText(Objects.toString(suggestedScope, "*").trim().isEmpty() ? "*" : Objects.toString(suggestedScope, "*").trim());
      direction.setSelectedItem(FilterDirection.ANY);
    }

    JLabel hint = new JLabel("Action: HIDE (MVP)");
    hint.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
    JLabel error = new JLabel(" ");
    error.putClientProperty(FlatClientProperties.STYLE, "foreground: #C62828");

    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");
    ok.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");

    JPanel form = new JPanel(new GridBagLayout());
    GridBagConstraints g = new GridBagConstraints();
    g.insets = new Insets(6, 8, 6, 8);
    g.anchor = GridBagConstraints.WEST;
    g.fill = GridBagConstraints.HORIZONTAL;
    g.weightx = 1.0;

    int row = 0;
    g.gridx = 0;
    g.gridy = row;
    form.add(new JLabel("Name"), g);
    g.gridx = 1;
    form.add(name, g);

    row++;
    g.gridx = 0;
    g.gridy = row;
    form.add(new JLabel("Scope"), g);
    g.gridx = 1;
    form.add(scope, g);

    row++;
    g.gridx = 0;
    g.gridy = row;
    form.add(new JLabel("Direction"), g);
    g.gridx = 1;
    JPanel dirRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    dirRow.add(direction);
    dirRow.add(enabled);
    form.add(dirRow, g);

    row++;
    g.gridx = 0;
    g.gridy = row;
    form.add(new JLabel("Kinds"), g);
    g.gridx = 1;
    JPanel kinds = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    kinds.add(kindChat);
    kinds.add(kindAction);
    kinds.add(kindNotice);
    kinds.add(kindStatus);
    kinds.add(kindError);
    kinds.add(kindPresence);
    kinds.add(kindSpoiler);
    form.add(kinds, g);

    row++;
    g.gridx = 0;
    g.gridy = row;
    form.add(new JLabel("From"), g);
    g.gridx = 1;
    form.add(fromGlobs, g);

    row++;
    g.gridx = 0;
    g.gridy = row;
    form.add(new JLabel("Tags"), g);
    g.gridx = 1;
    form.add(tags, g);

    row++;
    g.gridx = 0;
    g.gridy = row;
    form.add(new JLabel("Text"), g);
    g.gridx = 1;
    JPanel reRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    reRow.add(regex);
    form.add(reRow, g);

    row++;
    g.gridx = 1;
    g.gridy = row;
    JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    flags.add(reI);
    flags.add(reM);
    flags.add(reS);
    flags.add(hint);
    form.add(flags, g);

    row++;
    g.gridx = 1;
    g.gridy = row;
    form.add(error, g);

    dlg.add(form, BorderLayout.CENTER);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(cancel);
    buttons.add(ok);
    dlg.add(buttons, BorderLayout.SOUTH);

    final Optional<FilterRule>[] result = new Optional[]{Optional.empty()};

    Runnable validate = () -> {
      String n = normalizeName(name.getText());
      String nKey = n.toLowerCase(Locale.ROOT);
      String sc = normalizeScope(scope.getText());
      String re = Objects.toString(regex.getText(), "").trim();

      if (n.isEmpty()) {
        error.setText("Name is required.");
        ok.setEnabled(false);
        return;
      }
      if (reservedNameKeys != null && reservedNameKeys.contains(nKey)) {
        error.setText("Name already exists.");
        ok.setEnabled(false);
        return;
      }
      if (sc.isEmpty()) {
        error.setText("Scope is required.");
        ok.setEnabled(false);
        return;
      }
      if (!re.isEmpty()) {
        try {
          Pattern.compile(re, toPatternFlags(reI.isSelected(), reM.isSelected(), reS.isSelected()));
        } catch (PatternSyntaxException ex) {
          error.setText("Invalid regex: " + sanitize(ex.getDescription()));
          ok.setEnabled(false);
          return;
        } catch (Exception ex) {
          error.setText("Invalid regex.");
          ok.setEnabled(false);
          return;
        }
      }

      String tagExpr = Objects.toString(tags.getText(), "").trim();
      String tagErr = validateTagsExpr(tagExpr);
      if (tagErr != null) {
        error.setText(tagErr);
        ok.setEnabled(false);
        return;
      }

      error.setText(" ");
      ok.setEnabled(true);
    };

    DocumentListener dl = new SimpleDocListener(validate);
    name.getDocument().addDocumentListener(dl);
    scope.getDocument().addDocumentListener(dl);
    fromGlobs.getDocument().addDocumentListener(dl);
    tags.getDocument().addDocumentListener(dl);
    regex.getDocument().addDocumentListener(dl);

    enabled.addActionListener(e -> validate.run());
    direction.addActionListener(e -> validate.run());
    kindChat.addActionListener(e -> validate.run());
    kindAction.addActionListener(e -> validate.run());
    kindNotice.addActionListener(e -> validate.run());
    kindStatus.addActionListener(e -> validate.run());
    kindError.addActionListener(e -> validate.run());
    kindPresence.addActionListener(e -> validate.run());
    kindSpoiler.addActionListener(e -> validate.run());
    reI.addActionListener(e -> validate.run());
    reM.addActionListener(e -> validate.run());
    reS.addActionListener(e -> validate.run());

    cancel.addActionListener(e -> {
      result[0] = Optional.empty();
      dlg.dispose();
    });

    ok.addActionListener(e -> {
      validate.run();
      if (!ok.isEnabled()) return;

      String n = normalizeName(name.getText());
      String sc = normalizeScope(scope.getText());

      EnumSet<LogKind> kindsSet = EnumSet.noneOf(LogKind.class);
      if (kindChat.isSelected()) kindsSet.add(LogKind.CHAT);
      if (kindAction.isSelected()) kindsSet.add(LogKind.ACTION);
      if (kindNotice.isSelected()) kindsSet.add(LogKind.NOTICE);
      if (kindStatus.isSelected()) kindsSet.add(LogKind.STATUS);
      if (kindError.isSelected()) kindsSet.add(LogKind.ERROR);
      if (kindPresence.isSelected()) kindsSet.add(LogKind.PRESENCE);
      if (kindSpoiler.isSelected()) kindsSet.add(LogKind.SPOILER);

      List<String> from = splitTokens(fromGlobs.getText());

      String re = Objects.toString(regex.getText(), "").trim();
      EnumSet<RegexFlag> regexFlags = EnumSet.noneOf(RegexFlag.class);
      if (reI.isSelected()) regexFlags.add(RegexFlag.I);
      if (reM.isSelected()) regexFlags.add(RegexFlag.M);
      if (reS.isSelected()) regexFlags.add(RegexFlag.S);
      RegexSpec rs = new RegexSpec(re, regexFlags);

      TagSpec tagSpec = TagSpec.parse(Objects.toString(tags.getText(), "").trim());

      FilterRule base = new FilterRule(
          seed != null ? seed.id() : null,
          n,
          enabled.isSelected(),
          sc,
          FilterAction.HIDE,
          (FilterDirection) direction.getSelectedItem(),
          kindsSet,
          from,
          rs,
          tagSpec
      );

      result[0] = Optional.of(base);
      dlg.dispose();
    });

    validate.run();

    dlg.setMinimumSize(new Dimension(820, 360));
    dlg.pack();
    dlg.setLocationRelativeTo(owner);
    dlg.setVisible(true);
    return result[0];
  }

  private static String validateTagsExpr(String expr) {
    String s = Objects.toString(expr, "").trim();
    if (s.isEmpty()) return null;

    // Very lightweight validation: only verify explicit regex tokens.
    // Glob tokens are always accepted.
    try {
      // Split on comma / whitespace into OR terms.
      for (String term : s.split("[\\s,]+")) {
        term = term.trim();
        if (term.isEmpty()) continue;
        // AND tokens (+)
        for (String tok : term.split("\\+")) {
          tok = tok.trim();
          if (tok.isEmpty()) continue;
          if (tok.startsWith("!")) tok = tok.substring(1).trim();
          if (tok.startsWith("re:")) {
            String body = tok.substring(3);
            java.util.regex.Pattern.compile(body, java.util.regex.Pattern.CASE_INSENSITIVE);
          } else if (tok.startsWith("/") && tok.lastIndexOf('/') > 0) {
            int last = tok.lastIndexOf('/');
            String body = tok.substring(1, last);
            String flags = tok.substring(last + 1);
            int pf = java.util.regex.Pattern.CASE_INSENSITIVE;
            if (flags.contains("m")) pf |= java.util.regex.Pattern.MULTILINE;
            if (flags.contains("s")) pf |= java.util.regex.Pattern.DOTALL;
            java.util.regex.Pattern.compile(body, pf);
          }
        }
      }
    } catch (java.util.regex.PatternSyntaxException ex) {
      return "Invalid tag regex: " + sanitize(ex.getDescription());
    } catch (Exception ex) {
      return "Invalid tags.";
    }
    return null;
  }

  private static String normalizeName(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String normalizeScope(String raw) {
    String s = Objects.toString(raw, "").trim();
    return s.isEmpty() ? "" : s;
  }

  private static int toPatternFlags(boolean i, boolean m, boolean s) {
    int flags = 0;
    if (i) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    if (m) flags |= Pattern.MULTILINE;
    if (s) flags |= Pattern.DOTALL;
    return flags;
  }

  private static List<String> splitTokens(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return List.of();

    String[] parts = s.split("[\\s,]+" );
    List<String> out = new ArrayList<>();
    for (String p : parts) {
      String t = Objects.toString(p, "").trim();
      if (!t.isEmpty()) out.add(t);
    }
    return out;
  }

  private static String sanitize(String s) {
    String t = Objects.toString(s, "").trim();
    if (t.isEmpty()) return "";
    // Keep this short; no stack traces.
    if (t.length() > 120) t = t.substring(0, 120) + "…";
    return t;
  }

  private static final class SimpleDocListener implements DocumentListener {
    private final Runnable onChange;

    private SimpleDocListener(Runnable onChange) {
      this.onChange = onChange;
    }

    @Override public void insertUpdate(DocumentEvent e) { onChange.run(); }
    @Override public void removeUpdate(DocumentEvent e) { onChange.run(); }
    @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
  }
}

package cafe.woden.ircclient.ui.channellist;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;

final class MatrixChannelListUxMode implements ChannelListUxMode {
  private static final String DEFAULT_HINT =
      "Use refresh for /list defaults, filters for Matrix search/since/limit, and next page when available.";
  private static final int MATRIX_LIST_DEFAULT_LIMIT = 100;
  private static final int MATRIX_LIST_MAX_LIMIT = 200;
  private static final ActionPresentation PRESENTATION =
      new ActionPresentation(
          "Run Matrix /list with default options.",
          "Run Matrix /list",
          "Run Matrix /list with search/since/limit options.",
          "Run Matrix list filters",
          true,
          "Run next Matrix /list page (uses next_batch from last response).",
          "Run next Matrix list page");

  private final Map<String, MatrixListState> stateByServer = new HashMap<>();

  @Override
  public String defaultHint() {
    return DEFAULT_HINT;
  }

  @Override
  public ActionPresentation actionPresentation() {
    return PRESENTATION;
  }

  @Override
  public void runPrimaryAction(Context context, String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    context.rememberRequestType(sid, ChannelListRequestType.MATRIX_LIST);
    stateByServer.put(sid, new MatrixListState("", MATRIX_LIST_DEFAULT_LIMIT, ""));
    context.clearFilterText();
    context.updateListButtons();
    context.emitRunListRequest();
  }

  @Override
  public void runSecondaryAction(Context context, String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    MatrixListState current =
        stateByServer.getOrDefault(sid, new MatrixListState("", MATRIX_LIST_DEFAULT_LIMIT, ""));
    JTextField queryField = new JTextField(current.searchTerm(), 28);
    JSpinner limitSpinner =
        new JSpinner(
            new SpinnerNumberModel(
                normalizeMatrixListLimit(current.limit()), 1, MATRIX_LIST_MAX_LIMIT, 1));
    JCheckBox sinceEnabled = new JCheckBox("Use since token");
    JTextField sinceField = new JTextField(28);
    String defaultSince = current.nextSinceToken();
    if (!defaultSince.isEmpty()) {
      sinceEnabled.setSelected(true);
      sinceField.setText(defaultSince);
    }
    sinceField.setEnabled(sinceEnabled.isSelected());
    sinceEnabled.addActionListener(e -> sinceField.setEnabled(sinceEnabled.isSelected()));

    JPanel form =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[right][grow,fill]", "[]6[]6[]6[]"));
    form.add(new JLabel("Search:"));
    form.add(queryField, "growx");
    form.add(new JLabel("Limit:"));
    form.add(limitSpinner, "w 120!");
    form.add(sinceEnabled);
    form.add(sinceField, "growx");
    form.add(new JLabel("Tip:"));
    form.add(new JLabel("Use Next Page after results include next_batch."), "growx");

    int choice =
        JOptionPane.showConfirmDialog(
            context.ownerWindow(),
            form,
            "Run Matrix /LIST",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) return;

    String searchTerm = Objects.toString(queryField.getText(), "").trim();
    String sinceToken =
        sinceEnabled.isSelected() ? Objects.toString(sinceField.getText(), "").trim() : "";
    int limit = ((Number) limitSpinner.getValue()).intValue();

    ChannelListPanel.MatrixListOptions options =
        new ChannelListPanel.MatrixListOptions(searchTerm, sinceToken, limit);
    stateByServer.put(sid, new MatrixListState(options.searchTerm(), options.limit(), ""));

    context.rememberRequestType(sid, ChannelListRequestType.MATRIX_LIST);
    context.updateListButtons();
    context.emitRunCommand(buildMatrixListCommand(options));
  }

  @Override
  public void runPagingAction(Context context, String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    MatrixListState current =
        stateByServer.getOrDefault(sid, new MatrixListState("", MATRIX_LIST_DEFAULT_LIMIT, ""));
    String nextSince = normalizeMatrixToken(current.nextSinceToken());
    if (nextSince.isEmpty()) return;

    ChannelListPanel.MatrixListOptions options =
        new ChannelListPanel.MatrixListOptions(current.searchTerm(), nextSince, current.limit());
    stateByServer.put(sid, new MatrixListState(current.searchTerm(), current.limit(), ""));

    context.rememberRequestType(sid, ChannelListRequestType.MATRIX_LIST);
    context.updateListButtons();
    context.emitRunCommand(buildMatrixListCommand(options));
  }

  @Override
  public void onBeginList(String serverId, String banner) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    MatrixListOptions options = parseMatrixListOptionsFromLoadingBanner(banner);
    MatrixListState current =
        stateByServer.getOrDefault(sid, new MatrixListState("", MATRIX_LIST_DEFAULT_LIMIT, ""));
    if (options == null) {
      stateByServer.put(sid, current.withNextSinceToken(""));
      return;
    }
    stateByServer.put(
        sid,
        new MatrixListState(options.searchTerm(), normalizeMatrixListLimit(options.limit()), ""));
  }

  @Override
  public void onEndList(String serverId, String summary) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    MatrixListState current =
        stateByServer.getOrDefault(sid, new MatrixListState("", MATRIX_LIST_DEFAULT_LIMIT, ""));
    stateByServer.put(sid, current.withNextSinceToken(parseMatrixNextBatchToken(summary)));
  }

  @Override
  public boolean isPagingActionEnabled(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    MatrixListState current =
        stateByServer.getOrDefault(sid, new MatrixListState("", MATRIX_LIST_DEFAULT_LIMIT, ""));
    return !normalizeMatrixToken(current.nextSinceToken()).isEmpty();
  }

  @Override
  public ChannelListRequestType inferRequestTypeFromBanner(String banner) {
    String text = Objects.toString(banner, "").trim().toLowerCase(Locale.ROOT);
    if (text.contains("matrix")) return ChannelListRequestType.MATRIX_LIST;
    return ChannelListRequestType.UNKNOWN;
  }

  static String buildMatrixListCommand(ChannelListPanel.MatrixListOptions options) {
    ChannelListPanel.MatrixListOptions opts =
        options == null ? ChannelListPanel.MatrixListOptions.defaults() : options;
    String search = normalizeMatrixToken(opts.searchTerm());
    String since = normalizeMatrixToken(opts.sinceToken());
    int limit = normalizeMatrixListLimit(opts.limit());

    StringBuilder args = new StringBuilder();
    if (!search.isEmpty()) {
      args.append(search);
    }
    if (!since.isEmpty()) {
      if (!args.isEmpty()) args.append(' ');
      args.append("since ").append(since);
    }
    if (limit > 0) {
      if (!args.isEmpty()) args.append(' ');
      args.append("limit ").append(limit);
    }

    String suffix = args.toString().trim();
    return suffix.isEmpty() ? "/list" : ("/list " + suffix);
  }

  private static MatrixListOptions parseMatrixListOptionsFromLoadingBanner(String banner) {
    String text = Objects.toString(banner, "").trim();
    if (text.isEmpty()) return null;
    String lower = text.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("loading channel list")) {
      return null;
    }
    int open = text.indexOf('(');
    int close = text.lastIndexOf(')');
    if (open < 0 || close <= open) {
      return MatrixListOptions.defaults();
    }
    String args = text.substring(open + 1, close).trim();
    return parseMatrixListOptions(args);
  }

  private static MatrixListOptions parseMatrixListOptions(String rawArgs) {
    String args = Objects.toString(rawArgs, "").trim();
    if (args.isEmpty()) return MatrixListOptions.defaults();

    String[] tokens = args.split("\\s+");
    java.util.ArrayList<String> freeTokens = new java.util.ArrayList<>();
    String searchTerm = "";
    String sinceToken = "";
    int limit = MATRIX_LIST_DEFAULT_LIMIT;

    for (int i = 0; i < tokens.length; i++) {
      String token = normalizeMatrixToken(tokens[i]);
      if (token.isEmpty()) continue;
      String lower = token.toLowerCase(Locale.ROOT);

      String sinceInline = matrixListOptionValue(lower, token, "since");
      if (!sinceInline.isEmpty()) {
        sinceToken = sinceInline;
        continue;
      }
      if ("since".equals(lower) || "--since".equals(lower) || "-since".equals(lower)) {
        if (i + 1 < tokens.length) {
          sinceToken = normalizeMatrixToken(tokens[++i]);
        }
        continue;
      }

      String searchInline = matrixListOptionValue(lower, token, "search");
      if (searchInline.isEmpty()) {
        searchInline = matrixListOptionValue(lower, token, "q");
      }
      if (!searchInline.isEmpty()) {
        searchTerm = searchInline;
        continue;
      }
      if ("search".equals(lower)
          || "--search".equals(lower)
          || "-search".equals(lower)
          || "q".equals(lower)
          || "--q".equals(lower)
          || "-q".equals(lower)) {
        if (i + 1 < tokens.length) {
          searchTerm = normalizeMatrixToken(tokens[++i]);
        }
        continue;
      }

      String limitInline = matrixListOptionValue(lower, token, "limit");
      if (limitInline.isEmpty()) {
        limitInline = matrixListOptionValue(lower, token, "max");
      }
      if (!limitInline.isEmpty()) {
        Integer parsed = parsePositiveIntToken(limitInline);
        if (parsed != null) {
          limit = parsed.intValue();
        }
        continue;
      }
      if ("limit".equals(lower)
          || "--limit".equals(lower)
          || "-limit".equals(lower)
          || "max".equals(lower)
          || "--max".equals(lower)
          || "-max".equals(lower)) {
        if (i + 1 < tokens.length) {
          Integer parsed = parsePositiveIntToken(tokens[++i]);
          if (parsed != null) {
            limit = parsed.intValue();
          }
        }
        continue;
      }

      freeTokens.add(token);
    }

    if (searchTerm.isEmpty() && !freeTokens.isEmpty()) {
      searchTerm = normalizeMatrixToken(String.join(" ", freeTokens));
    }
    return new MatrixListOptions(searchTerm, sinceToken, limit);
  }

  private static String matrixListOptionValue(
      String tokenLower, String tokenRaw, String optionName) {
    String option = normalizeMatrixToken(optionName).toLowerCase(Locale.ROOT);
    if (option.isEmpty()) return "";
    if (tokenLower.startsWith(option + "=")) {
      return normalizeMatrixToken(tokenRaw.substring(option.length() + 1));
    }
    if (tokenLower.startsWith("--" + option + "=")) {
      return normalizeMatrixToken(tokenRaw.substring(option.length() + 3));
    }
    if (tokenLower.startsWith("-" + option + "=")) {
      return normalizeMatrixToken(tokenRaw.substring(option.length() + 2));
    }
    return "";
  }

  private static String parseMatrixNextBatchToken(String summary) {
    String text = Objects.toString(summary, "").trim();
    if (text.isEmpty()) return "";

    String marker = "next_batch=";
    String lower = text.toLowerCase(Locale.ROOT);
    int markerIndex = lower.indexOf(marker);
    if (markerIndex < 0) return "";

    int start = markerIndex + marker.length();
    while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
      start++;
    }
    int end = start;
    while (end < text.length()) {
      char c = text.charAt(end);
      if (Character.isWhitespace(c) || c == ',' || c == ';' || c == ')') {
        break;
      }
      end++;
    }
    return normalizeMatrixToken(text.substring(start, end));
  }

  private static Integer parsePositiveIntToken(String token) {
    String value = normalizeMatrixToken(token);
    if (value.isEmpty()) return null;
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0) {
        return null;
      }
      return Integer.valueOf(parsed);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static int normalizeMatrixListLimit(int limit) {
    int requested = limit <= 0 ? MATRIX_LIST_DEFAULT_LIMIT : limit;
    return Math.max(1, Math.min(requested, MATRIX_LIST_MAX_LIMIT));
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeMatrixToken(String value) {
    return Objects.toString(value, "").trim();
  }

  private record MatrixListState(String searchTerm, int limit, String nextSinceToken) {
    MatrixListState {
      searchTerm = normalizeMatrixToken(searchTerm);
      limit = normalizeMatrixListLimit(limit);
      nextSinceToken = normalizeMatrixToken(nextSinceToken);
    }

    MatrixListState withNextSinceToken(String nextSinceToken) {
      return new MatrixListState(searchTerm, limit, nextSinceToken);
    }
  }

  private record MatrixListOptions(String searchTerm, String sinceToken, int limit) {
    static MatrixListOptions defaults() {
      return new MatrixListOptions("", "", MATRIX_LIST_DEFAULT_LIMIT);
    }

    MatrixListOptions {
      searchTerm = normalizeMatrixToken(searchTerm);
      sinceToken = normalizeMatrixToken(sinceToken);
      limit = normalizeMatrixListLimit(limit);
    }
  }
}

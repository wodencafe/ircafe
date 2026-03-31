package cafe.woden.ircclient.ui.settings;

import java.util.Objects;

record UserCommandAliasValidationError(int rowIndex, String command, String message) {

  String formatForDialog() {
    String cmd = Objects.toString(command, "").trim();
    if (cmd.isEmpty()) cmd = "(blank)";
    String msg = Objects.toString(message, "Invalid alias").trim();
    return "Row " + (rowIndex + 1) + " (/" + cmd + "):\n" + msg;
  }
}

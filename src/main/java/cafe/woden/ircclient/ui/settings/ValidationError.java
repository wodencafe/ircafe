package cafe.woden.ircclient.ui.settings;

record ValidationError(int rowIndex, String label, String pattern, String message) {

  String effectiveLabel() {
    String l = label != null ? label.trim() : "";
    if (!l.isEmpty()) return l;
    String p = pattern != null ? pattern.trim() : "";
    return p.isEmpty() ? "(unnamed)" : p;
  }

  String formatForInline() {
    String msg = message != null ? message.trim() : "Invalid regex";
    if (msg.length() > 180) msg = msg.substring(0, 180) + "…";
    return "Invalid REGEX (row " + (rowIndex + 1) + ", " + effectiveLabel() + "): " + msg;
  }

  String formatForDialog() {
    String msg = message != null ? message.trim() : "Invalid regex";
    return "Row "
        + (rowIndex + 1)
        + " ("
        + effectiveLabel()
        + "):\n"
        + msg
        + "\n\nPattern:\n"
        + (pattern != null ? pattern : "");
  }
}

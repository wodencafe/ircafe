package cafe.woden.ircclient.ui.servers;

enum ServerEditorAuthMode {
  DISABLED("Disabled"),
  SASL("SASL"),
  NICKSERV("NickServ");

  private final String label;

  ServerEditorAuthMode(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}

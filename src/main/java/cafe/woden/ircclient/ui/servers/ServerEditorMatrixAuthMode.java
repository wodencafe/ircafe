package cafe.woden.ircclient.ui.servers;

enum ServerEditorMatrixAuthMode {
  ACCESS_TOKEN("Access token"),
  USERNAME_PASSWORD("Username + password");

  private final String label;

  ServerEditorMatrixAuthMode(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}

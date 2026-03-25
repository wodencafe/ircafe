package cafe.woden.ircclient.ui.settings;

record LaunchGcOption(String id, String label) {
  @Override
  public String toString() {
    return label;
  }
}
